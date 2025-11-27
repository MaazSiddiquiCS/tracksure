package com.tracksure.android.ui

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.tracksure.android.geohash.LocationChannelManager
import com.tracksure.android.mesh.BluetoothMeshDelegate
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.mesh.PeerInfo
import com.tracksure.android.model.BitchatMessage
import com.tracksure.android.model.RoutedPacket
import com.tracksure.android.protocol.BitchatPacket
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    val meshService: BluetoothMeshService
) : AndroidViewModel(application), BluetoothMeshDelegate {

    private val state = ChatState()
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
            Log.e("ChatViewModel", "Failed to start Mesh Services")
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
        Log.d("ChatViewModel", "📡 Broadcasting my location: ${loc.latitude}, ${loc.longitude}")
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

    // --- BluetoothMeshDelegate ---

    override fun handleLocationUpdate(routed: RoutedPacket) {
        // Packet arrived -> PeerManager updated -> We refresh UI
        Log.d("ChatViewModel", "Received Location Update from ${routed.peerID}")
        refreshPeerList()
    }

    override fun didUpdatePeerList(peerIDs: List<String>) {
        Log.d("ChatViewModel", "Peer List Updated: ${peerIDs.size} peers")
        refreshPeerList()

        // If a new peer appears, send them our location immediately
        locationChannelManager.getCurrentLocation()?.let { broadcastMyLocation(it) }
    }

    override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
        Log.d("ChatViewModel", "Device Connected: ${device.address}")
        state.setIsConnected(true)
        // Trigger refresh to see if PeerManager has processed them yet
        refreshPeerList()
    }

    override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice) {
        Log.d("ChatViewModel", "Device Disconnected: ${device.address}")
        refreshPeerList()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "🛑 App closing, stopping services...")

        // Stop UI Loop - This now works because there is only one runnable
        mainHandler.removeCallbacks(locationRunnable)

        // Stop Location Updates
        locationChannelManager.endLiveRefresh()

        // Stop Bluetooth/Mesh
        meshService.connectionManager.stopServices()
        meshService.peerManager.shutdown()
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
