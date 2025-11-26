package com.tracksure.android.ui

import android.app.Application
import android.util.Log
//import androidx.compose.ui.test.cancel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tracksure.android.geohash.ChannelID
import com.tracksure.android.geohash.LocationServiceManager
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.protocol.BitchatPacket
import com.tracksure.android.protocol.MessageType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

// --- STATE CLASSES ---
data class PeerLocation(
    val peerID: String,
    val nickname: String,
    val geoPoint: GeoPoint,
    val timestamp: Long,
    val isSelf: Boolean = false
)

data class MapState(
    val peerLocations: Map<String, PeerLocation> = emptyMap(),
    val myLocation: PeerLocation? = null,
    val selectedPeerID: String? = null
)

// --- VIEW MODEL ---
class MapViewModel(
    application: Application,
    // IMPORTANT: Must be public so MainActivity can access it
    val meshService: BluetoothMeshService
) : AndroidViewModel(application) {

    private val _state = MutableLiveData(MapState())
    val state: LiveData<MapState> = _state

    private val locationServiceManager = LocationServiceManager(application)

    private var locationTrackingJob: Job? = null

    init {
       // startTrackingLocation()
    }

    companion object {
        private const val TAG = "MapViewModel"
    }

    /**
     * Handle incoming location packet from mesh (Format: "lat,long")
     */
    fun handleLocationPacket(peerID: String, payloadString: String) {
        try {
            val parts = payloadString.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()

                if (lat != null && lon != null) {
                    val nickname = "User ${peerID.take(4)}"

                    val newLocation = PeerLocation(
                        peerID = peerID,
                        nickname = nickname,
                        geoPoint = GeoPoint(lat, lon),
                        timestamp = System.currentTimeMillis()
                    )

                    val currentMap = _state.value?.peerLocations?.toMutableMap() ?: mutableMapOf()
                    currentMap[peerID] = newLocation

                    _state.postValue(_state.value?.copy(peerLocations = currentMap))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing location: $payloadString")
        }
    }


    fun tryStartLocationTracking() {
        // Cancel old job if exists
        locationTrackingJob?.cancel()

        locationTrackingJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Starting Location Flow collection...")

                // This collects the Flow we fixed in Step 1
                locationServiceManager.getLocationUpdates().collect { location ->

                    // 1. Update Local UI
                    updateMyLocation(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location tracking error", e)
            }
        }
    }

    fun handleBackPressed(): Boolean {
        if (_state.value?.selectedPeerID != null) {
            selectPeer(null)
            return true
        }
        return false
    }

    // Add inside MapViewModel class
    fun updatePeerList(activePeerIDs: List<String>) {
        val currentMap = _state.value?.peerLocations?.toMutableMap() ?: return

        // Identify peers to remove (those in map but not in active list)
        // Don't remove ourselves
        val peersToRemove = currentMap.keys.filter { !activePeerIDs.contains(it) && it != meshService.myPeerID }

        if (peersToRemove.isNotEmpty()) {
            peersToRemove.forEach { currentMap.remove(it) }
            _state.postValue(_state.value?.copy(peerLocations = currentMap))
            Log.d(TAG, "Removed stale peers: $peersToRemove")
        }
    }


    fun setAppBackgroundState(isBackground: Boolean) {
        meshService.connectionManager.setAppBackgroundState(isBackground)
    }

    fun updateMyLocation(lat: Double, lon: Double) {
        val myLoc = PeerLocation(
            peerID = meshService.myPeerID,
            nickname = "Me",
            geoPoint = GeoPoint(lat, lon),
            timestamp = System.currentTimeMillis(),
            isSelf = true
        )

        _state.value = _state.value?.copy(myLocation = myLoc)
        broadcastLocation(lat, lon)
    }

    private fun broadcastLocation(lat: Double, lon: Double) {
        val payload = "$lat,$lon".toByteArray(Charsets.UTF_8)

        val packet = BitchatPacket(
            type = MessageType.LOCATION_UPDATE.value,
            payload = payload,
            senderID = hexStringToByteArray(meshService.myPeerID),
            timestamp = System.currentTimeMillis().toULong(),
            ttl = 3u
        )

        meshService.connectionManager.broadcastPacket(com.tracksure.android.model.RoutedPacket(packet))
    }

    fun selectPeer(peerID: String?) {
        _state.value = _state.value?.copy(selectedPeerID = peerID)
    }

    // Notification Stubs (Required by MainActivity)
    fun clearNotificationsForSender(peerID: String) { Log.d(TAG, "Clear notifications: $peerID") }
    fun selectLocationChannel(channelId: ChannelID) { Log.d(TAG, "Select channel: $channelId") }
    fun setCurrentGeohash(geohash: String) { Log.d(TAG, "Set geohash: $geohash") }
    fun clearNotificationsForGeohash(geohash: String) { Log.d(TAG, "Clear geohash notifications: $geohash") }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
