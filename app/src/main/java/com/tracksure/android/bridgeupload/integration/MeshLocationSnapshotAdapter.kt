package com.tracksure.android.bridgeupload.integration

import android.content.Context
import android.util.Log
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.mesh.PeerInfo

/**
 * Read-only adapter over mesh runtime state.
 */
class MeshLocationSnapshotAdapter(private val context: Context) {
    companion object {
        private const val TAG = "BridgeUpload"
    }

    data class MeshLocationSnapshot(
        val peerId: String,
        val lat: Double,
        val lon: Double,
        val recordedAtEpochMs: Long,
        val accuracy: Double = 0.1,
        val source: String = "MESH"
    )

    fun captureSnapshots(nowEpochMs: Long = System.currentTimeMillis()): List<MeshLocationSnapshot> {
        val mesh = BluetoothMeshService.getInstance(context)
        val peerIds = (mesh.getPeerNicknames().keys + mesh.myPeerID).toSet()
        val snapshots = toSnapshots(peerIds, nowEpochMs) { peerId -> mesh.getPeerInfo(peerId) }
        Log.d(TAG, "Snapshot capture peerIds=${peerIds.size} validSnapshots=${snapshots.size}")
        return snapshots
    }

    internal fun toSnapshots(
        peerIds: Collection<String>,
        nowEpochMs: Long,
        peerInfoProvider: (String) -> PeerInfo?
    ): List<MeshLocationSnapshot> {
        var droppedMissingInfo = 0
        var droppedMissingCoord = 0
        val snapshots = peerIds.mapNotNull { peerId ->
            val info = peerInfoProvider(peerId) ?: return@mapNotNull null
            val lat = info.latitude
            val lon = info.longitude
            if (lat == null || lon == null) {
                droppedMissingCoord++
                return@mapNotNull null
            }
            MeshLocationSnapshot(
                peerId = peerId,
                lat = lat,
                lon = lon,
                recordedAtEpochMs = info.lastSeen.takeIf { it > 0L } ?: nowEpochMs
            )
        }
        droppedMissingInfo = peerIds.size - snapshots.size - droppedMissingCoord
        if (droppedMissingInfo > 0 || droppedMissingCoord > 0) {
            Log.d(
                TAG,
                "Snapshot dropped missingInfo=$droppedMissingInfo missingCoords=$droppedMissingCoord"
            )
        }
        return snapshots
    }
}


