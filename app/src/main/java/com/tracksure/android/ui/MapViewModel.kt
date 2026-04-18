package com.tracksure.android.ui

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tracksure.android.geohash.LocationChannelManager
import com.tracksure.android.identity.AuthTokenStore
import com.tracksure.android.mesh.BluetoothMeshDelegate
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.mesh.PeerInfo
import com.tracksure.android.model.BitchatMessage
import com.tracksure.android.model.RoutedPacket
import com.tracksure.android.protocol.BitchatPacket
import com.tracksure.android.services.MeshForegroundService
import com.tracksure.android.util.NotificationIntervalManager

class MapViewModel(
    application: Application,
    val meshService: BluetoothMeshService
) : AndroidViewModel(application), BluetoothMeshDelegate {

    private val state = MapState()
    private val locationChannelManager = LocationChannelManager.getInstance(application)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs = application.getSharedPreferences("tracksure_prefs", Context.MODE_PRIVATE)
    private val trackingPrefs = application.getSharedPreferences("tracksure_tracking_prefs", Context.MODE_PRIVATE)
    private val authTokenStore = AuthTokenStore(application.applicationContext)

    companion object {
        private const val KEY_OWNER_INVITE = "owner_tracking_invite"
        private const val KEY_IMPORTED_INVITE = "imported_tracking_invite"
    }

    // --- Settings Data ---
    private val _myNickname = MutableLiveData<String>()
    val myNickname: LiveData<String> = _myNickname

    private val _accountUsername = MutableLiveData<String>()
    val accountUsername: LiveData<String> = _accountUsername

    private val _accountEmail = MutableLiveData<String>()
    val accountEmail: LiveData<String> = _accountEmail

    private val _ownerTrackingInvite = MutableLiveData<TrackingShareInvite?>()
    val ownerTrackingInvite: LiveData<TrackingShareInvite?> = _ownerTrackingInvite

    private val _importedTrackingInvite = MutableLiveData<TrackingShareInvite?>()
    val importedTrackingInvite: LiveData<TrackingShareInvite?> = _importedTrackingInvite

    // --- Authorized Peers (Unlocked via Code) ---
    private val _authorizedPeers = MutableLiveData<Set<String>>(emptySet())
    val authorizedPeers: LiveData<Set<String>> = _authorizedPeers

    // --- Live Refresh Loop ---
    private val locationRunnable = object : Runnable {
        override fun run() {
            // 1. Update My Location
            val loc = locationChannelManager.getCurrentLocation()
            if (loc != null) {
                state.setMyLocation(loc)
                // Broadcast occasionally (every 5s) to avoid flooding, but update UI every 1s
                val now = System.currentTimeMillis()
                if (now - lastBroadcastTime > BROADCAST_INTERVAL) {
                    broadcastMyLocation(loc)
                }
            }

            // 2. Refresh Peer List (Fetch latest data from mesh service)
            refreshPeerList()

            // 3. Loop every 1 second for "Live" tracking feel
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val notificationManager = NotificationManager(
        application.applicationContext,
        NotificationManagerCompat.from(application.applicationContext),
        NotificationIntervalManager()
    )

    // Expose LiveData
    val peerLocations: LiveData<Map<String, PeerInfo>> = state.peerLocations
    val myLocation: LiveData<Location?> = state.myLocation
    val isConnected: LiveData<Boolean> = state.isConnected
    val connectedPeersCount: LiveData<Int> = state.connectedPeersCount

    private var lastBroadcastTime = 0L
    private val BROADCAST_INTERVAL = 5000L

    init {
        refreshAccountProfile()

        // Load Settings
        val savedNickname = prefs.getString("nickname", null)?.trim().orEmpty()
        val resolvedNickname = if (savedNickname.shouldUseDeviceNameAsDefault()) {
            resolveLocalDeviceName()
        } else {
            savedNickname
        }

        _myNickname.value = resolvedNickname
        state.setNickname(resolvedNickname)
        if (savedNickname != resolvedNickname) {
            prefs.edit().putString("nickname", resolvedNickname).apply()
        }
        _ownerTrackingInvite.value = loadInvite(KEY_OWNER_INVITE)
        _importedTrackingInvite.value = loadInvite(KEY_IMPORTED_INVITE)

        meshService.delegate = this

        if (!meshService.connectionManager.startServices()) {
            Log.e("MapViewModel", "Failed to start Mesh Services")
        }

        if (!locationChannelManager.isLocationServicesEnabled()) {
            locationChannelManager.enableLocationServices()
        }
        locationChannelManager.enableLocationChannels()

        // Tell LocationChannelManager to update frequently
        locationChannelManager.beginLiveRefresh(1000)

        // Start the ViewModel loop
        mainHandler.post(locationRunnable)
    }

    // --- Actions ---

    fun updateSettings(newNickname: String) {
        val normalizedNickname = newNickname.trim().ifBlank { resolveLocalDeviceName() }
        _myNickname.value = normalizedNickname
        state.setNickname(normalizedNickname)
        prefs.edit()
            .putString("nickname", normalizedNickname)
            .apply()
    }

    fun refreshAccountProfile() {
        val session = authTokenStore.load()
        _accountUsername.value = session?.username?.trim().takeIf { !it.isNullOrBlank() } ?: "Unknown user"
        _accountEmail.value = session?.email?.trim().takeIf { !it.isNullOrBlank() } ?: ""
    }

    fun createOrRotateOwnerTrackingInvite(): TrackingShareInvite {
        val invite = TrackingShareInvite.generate(
            ownerPeerId = meshService.myPeerID,
            ownerNickname = _myNickname.value.orEmpty()
        )
        _ownerTrackingInvite.value = invite
        saveInvite(KEY_OWNER_INVITE, invite)
        return invite
    }

    fun importTrackingInviteFromPayload(payload: String): TrackingShareInvite? {
        val invite = TrackingShareInvite.fromPayload(payload.trim()) ?: return null
        if (invite.isExpired()) return null
        _importedTrackingInvite.value = invite
        saveInvite(KEY_IMPORTED_INVITE, invite)
        return invite
    }

    fun authorizeTracking(peerId: String, inputCode: String): Boolean {
        val invite = _importedTrackingInvite.value ?: return false
        if (invite.isExpired()) return false
        if (!invite.ownerPeerId.trim().equals(peerId.trim(), ignoreCase = true)) return false

        val expectedPassword = normalizeTrackingSecret(invite.password)
        val providedPassword = normalizeTrackingSecret(inputCode)
        if (expectedPassword != providedPassword) return false

        val currentSet = _authorizedPeers.value.orEmpty().toMutableSet()
        currentSet.add(peerId)
        _authorizedPeers.value = currentSet

        if (invite.oneTimeUse) {
            clearImportedInvite()
        }

        return true
    }

    private fun clearImportedInvite() {
        _importedTrackingInvite.value = null
        trackingPrefs.edit().remove(KEY_IMPORTED_INVITE).apply()
    }

    private fun saveInvite(key: String, invite: TrackingShareInvite) {
        trackingPrefs.edit().putString(key, invite.toPayload()).apply()
    }

    private fun loadInvite(key: String): TrackingShareInvite? {
        val payload = trackingPrefs.getString(key, null) ?: return null
        val invite = TrackingShareInvite.fromPayload(payload) ?: return null
        return if (invite.isExpired()) null else invite
    }

    private fun normalizeTrackingSecret(value: String): String {
        return value
            .trim()
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace(":", "")
    }

    private fun broadcastMyLocation(loc: Location) {
        meshService.broadcastLocation(loc.latitude, loc.longitude)
        lastBroadcastTime = System.currentTimeMillis()
    }

    private fun refreshPeerList() {
        val activePeers = meshService.peerManager.getActivePeerIDs()
        val map = mutableMapOf<String, PeerInfo>()

        activePeers.forEach { id ->
            meshService.peerManager.getPeerInfo(id)?.let { info ->
                map[id] = info
            }
        }

        state.setPeerLocations(map)
        state.setConnectedPeersCount(map.size)
        val hasRawConnection = meshService.connectionManager.getConnectedDeviceEntries().isNotEmpty()
        state.setIsConnected(map.isNotEmpty() || hasRawConnection)
    }

    fun setAppBackgroundState(inBackground: Boolean) {
        notificationManager.setAppBackgroundState(inBackground)
        meshService.connectionManager.setAppBackgroundState(inBackground = false)
    }

    // --- BluetoothMeshDelegate ---

    override fun handleLocationUpdate(routed: RoutedPacket) {
        refreshPeerList()
    }

    override fun didUpdatePeerList(peerIDs: List<String>) {
        refreshPeerList()
    }

    override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
        state.setIsConnected(true)
        refreshPeerList()
    }

    override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice) {
        refreshPeerList()
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacks(locationRunnable)
        locationChannelManager.endLiveRefresh()
        try {
            val intent = Intent(getApplication(), MeshForegroundService::class.java)
            getApplication<Application>().stopService(intent)
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error stopping foreground service", e)
        }
        BluetoothMeshService.shutdown()
    }

    // Stubs
    override fun onPacketReceived(packet: BitchatPacket, peerID: String, device: android.bluetooth.BluetoothDevice?) {}
    override fun onRSSIUpdated(deviceAddress: String, rssi: Int) {}
    override fun handleMessage(routed: RoutedPacket) {}
    override fun handleAnnounce(routed: RoutedPacket) { refreshPeerList() }
    override fun handleLeave(routed: RoutedPacket) { refreshPeerList() }
    override fun getNickname(): String = _myNickname.value?.takeIf { it.isNotBlank() } ?: state.getNicknameValue()
    override fun isFavorite(peerID: String): Boolean = false
    override fun handleFragment(packet: BitchatPacket): BitchatPacket? = null
    override fun handleRequestSync(routed: RoutedPacket) {}
    override fun handleNoiseHandshake(routed: RoutedPacket) {}
    override fun handleNoiseEncrypted(routed: RoutedPacket) {}
    override fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean = true
    override fun updatePeerLastSeen(peerID: String) {}
    override fun getNetworkSize(): Int = 0
    override fun getBroadcastRecipient(): ByteArray = ByteArray(0)
    override fun relayPacket(routed: RoutedPacket) {}
    override fun getPeerNickname(peerID: String): String? = meshService.getPeerNicknames()[peerID]
    override fun didReceiveMessage(message: BitchatMessage) {}
    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}
    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {}
    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {}
    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null

    fun getDisplayNameForPeer(peerID: String, fallbackNickname: String? = null): String {
        val meshNickname = meshService.getPeerNicknames()[peerID]?.trim()
        if (meshNickname.isUsableDisplayNameForPeer(peerID)) {
            return meshNickname.orEmpty()
        }

        val bluetoothDeviceName = meshService.connectionManager.getDeviceNameForPeer(peerID)?.trim()
        if (bluetoothDeviceName.isUsableDisplayNameForPeer(peerID)) {
            return bluetoothDeviceName.orEmpty()
        }

        val fallback = fallbackNickname?.trim()
        if (fallback.isUsableDisplayNameForPeer(peerID)) {
            return fallback.orEmpty()
        }

        return "Unknown device"
    }

    private fun resolveLocalDeviceName(): String {
        val bluetoothName = try {
            val manager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.name?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        val systemDeviceName = try {
            Settings.Global.getString(getApplication<Application>().contentResolver, "device_name")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val modelFallback = listOfNotNull(
            Build.MANUFACTURER?.trim()?.takeIf { it.isNotBlank() },
            Build.MODEL?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim().takeIf { it.isNotBlank() }

        return (bluetoothName ?: systemDeviceName ?: modelFallback ?: "android-device").take(64)
    }

    private fun String?.isUsableDisplayNameForPeer(peerID: String): Boolean {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return false
        if (value.equals(peerID, ignoreCase = true)) return false
        if (value.equals("Unknown", ignoreCase = true) || value.equals("Unknown User", ignoreCase = true)) return false
        if (value.isAutogeneratedPeerLabel()) return false
        return true
    }

    private fun String.shouldUseDeviceNameAsDefault(): Boolean {
        if (this.isBlank()) return true
        if (this.equals("Unknown", ignoreCase = true) || this.equals("Unknown User", ignoreCase = true)) return true
        if (this.startsWith("anon", ignoreCase = true)) return true
        if (this.isAutogeneratedPeerLabel()) return true
        return false
    }

    private fun String.isAutogeneratedPeerLabel(): Boolean {
        val normalized = this.trim()
        return Regex("^(?i)(peer|device)\\s+[0-9a-f]{4,16}$").matches(normalized)
    }
}
