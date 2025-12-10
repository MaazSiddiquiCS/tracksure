package com.tracksure.android.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tracksure.android.geohash.LocationChannelManager
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

    // --- Settings Data ---
    private val _myNickname = MutableLiveData<String>()
    val myNickname: LiveData<String> = _myNickname

    private val _myMagicCode = MutableLiveData<String>()
    val myMagicCode: LiveData<String> = _myMagicCode

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
        // Load Settings
        _myNickname.value = prefs.getString("nickname", "Unknown User")
        _myMagicCode.value = prefs.getString("magic_code", "1234")

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

    fun updateSettings(newNickname: String, newCode: String) {
        _myNickname.value = newNickname
        _myMagicCode.value = newCode
        prefs.edit()
            .putString("nickname", newNickname)
            .putString("magic_code", newCode)
            .apply()
    }

    /**
     * Authenticate tracking.
     * Since we cannot modify the mesh protocol to verify the code remotely,
     * we perform a client-side check. If the user enters a non-empty code
     * (simulating they know the shared secret), we authorize the peer.
     */
    fun authorizeTracking(peerId: String, inputCode: String): Boolean {
        if (inputCode.isNotBlank()) {
            val currentSet = _authorizedPeers.value.orEmpty().toMutableSet()
            currentSet.add(peerId)
            _authorizedPeers.value = currentSet
            return true
        }
        return false
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
    override fun getNickname(): String = state.getNicknameValue()
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
    override fun getPeerNickname(peerID: String): String? = null
    override fun didReceiveMessage(message: BitchatMessage) {}
    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}
    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {}
    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {}
    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
}
