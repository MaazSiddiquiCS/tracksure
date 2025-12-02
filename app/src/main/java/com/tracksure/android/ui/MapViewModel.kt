package com.tracksure.android.ui

import android.app.Application
import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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

    // This is the ONLY runnable we want.
    private val locationRunnable = object : Runnable {
        override fun run() {
            // 1. Update My Location
            val loc = locationChannelManager.getCurrentLocation()
            if (loc != null) {
                state.setMyLocation(loc)

                // 2. Broadcast if needed
                val now = System.currentTimeMillis()
                if (now - lastBroadcastTime > BROADCAST_INTERVAL) {
                    broadcastMyLocation(loc)
                }
            }

            // 3. Explicitly refresh peer list
            refreshPeerList()

            // Loop
            mainHandler.postDelayed(this, 2000)
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
    val nickname: LiveData<String> = state.nickname
    val connectedPeersCount: LiveData<Int> = state.connectedPeersCount

    private var lastBroadcastTime = 0L
    private val BROADCAST_INTERVAL = 5000L

    init {
        meshService.delegate = this

        // Start Services
        if (!meshService.connectionManager.startServices()) {
            Log.e("MapViewModel", "Failed to start Mesh Services")
        }

        // Force location on
        if (!locationChannelManager.isLocationServicesEnabled()) {
            locationChannelManager.enableLocationServices()
        }
        locationChannelManager.enableLocationChannels()
        locationChannelManager.beginLiveRefresh(2000)

        // START THE LOOP
        mainHandler.post(locationRunnable)

        // REMOVED: startLocationLoop() -> This was creating a zombie loop
    }

    // REMOVED: private fun startLocationLoop() {...} -> Delete this function entirely

    private fun broadcastMyLocation(loc: Location) {
        Log.d("MapViewModel", "📡 Broadcasting my location: ${loc.latitude}, ${loc.longitude}")
        meshService.broadcastLocation(loc.latitude, loc.longitude)
        lastBroadcastTime = System.currentTimeMillis()
    }

    private fun refreshPeerList() {
        // Get IDs that PeerManager considers active
        val activePeers = meshService.peerManager.getActivePeerIDs()
        val map = mutableMapOf<String, PeerInfo>()

        activePeers.forEach { id ->
            meshService.peerManager.getPeerInfo(id)?.let { info ->
                map[id] = info
            }
        }

        // Update State
        state.setPeerLocations(map)

        // Update Counter
        val count = map.size
        state.setConnectedPeersCount(count)

        // Update connectivity status based on peers or raw connection
        val hasRawConnection = meshService.connectionManager.getConnectedDeviceEntries().isNotEmpty()
        state.setIsConnected(count > 0 || hasRawConnection)
    }

    fun setAppBackgroundState(inBackground: Boolean) {
        // Forward to notification manager for notification logic
        notificationManager.setAppBackgroundState(inBackground)
        meshService.connectionManager.setAppBackgroundState(inBackground = false)
    }

    // --- BluetoothMeshDelegate ---

    override fun handleLocationUpdate(routed: RoutedPacket) {
        // CRITICAL FIX: DO NOT REFRESH HERE.
        // This method is called instantly when a packet is processed, but before
        // PeerManager has finished saving the data. Refreshing here causes the UI
        // to read old data, leading to the delay you're seeing.
        Log.d("MapViewModel", "Packet received for ${routed.peerID}. Waiting for PeerManager to confirm update.")
        // REMOVED: refreshPeerList()
    }

    override fun didUpdatePeerList(peerIDs: List<String>) {
        // THIS IS THE CORRECT PLACE to refresh the UI.
        // This method is only called AFTER PeerManager has successfully updated its internal list.
        Log.d("MapViewModel", "PeerManager confirmed update for ${peerIDs.size} peers. Refreshing UI now.")
        refreshPeerList()

        // If a new peer appears, send them our location immediately
        locationChannelManager.getCurrentLocation()?.let { broadcastMyLocation(it) }
    }

    override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
        Log.d("MapViewModel", "Device Connected: ${device.address}")
        state.setIsConnected(true)
        // Trigger refresh to see if PeerManager has processed them yet
        refreshPeerList()
    }

    override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice) {
        Log.d("MapViewModel", "Device Disconnected: ${device.address}")
        refreshPeerList()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("MapViewModel", "🛑 App closing, performing hard shutdown...")

        // 1. Stop UI & Location
        mainHandler.removeCallbacks(locationRunnable)
        locationChannelManager.endLiveRefresh()

        // 2. Stop Android Service
        try {
            val intent = Intent(getApplication(), MeshForegroundService::class.java)
            getApplication<Application>().stopService(intent)
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error stopping foreground service", e)
        }

        // 3. HARD SHUTDOWN: Destroy the Singleton instance
        BluetoothMeshService.shutdown()
    }



    // ... Stubs remain the same ...
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
