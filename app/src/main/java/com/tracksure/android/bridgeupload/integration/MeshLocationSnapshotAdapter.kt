package com.tracksure.android.bridgeupload.integration

import android.content.Context
import android.util.Log
import com.tracksure.android.geohash.LocationChannelManager
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.mesh.PeerInfo

/**
 * Read-only adapter over mesh runtime state.
 */
class MeshLocationSnapshotAdapter(private val context: Context) {
    companion object {
        private const val TAG = "BridgeUpload"
        private const val MAX_REMOTE_STALENESS_MS = 2 * 60 * 1000L
    }

    private val locationChannelManager by lazy { LocationChannelManager.getInstance(context.applicationContext) }

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
        val remotePeerIds = mesh.getPeerNicknames().keys
        val remoteSnapshots = toSnapshots(remotePeerIds, nowEpochMs) { peerId -> mesh.getPeerInfo(peerId) }

        val hasSelfInRemote = remoteSnapshots.any { it.peerId.equals(mesh.myPeerID, ignoreCase = true) }
        val selfSnapshot = if (hasSelfInRemote) null else captureSelfSnapshot(mesh, nowEpochMs)
        val snapshots = if (selfSnapshot != null) remoteSnapshots + selfSnapshot else remoteSnapshots

        Log.d(TAG, "Snapshot capture peerIds=${remotePeerIds.size + 1} validSnapshots=${snapshots.size}")
        return snapshots
    }

    private fun captureSelfSnapshot(mesh: BluetoothMeshService, nowEpochMs: Long): MeshLocationSnapshot? {
        val selfPeerId = mesh.myPeerID
        val selfInfo = mesh.getPeerInfo(selfPeerId)
        val infoLat = selfInfo?.latitude
        val infoLon = selfInfo?.longitude

        if (infoLat != null && infoLon != null) {
            return MeshLocationSnapshot(
                peerId = selfPeerId,
                lat = infoLat,
                lon = infoLon,
                recordedAtEpochMs = selfInfo.lastSeen.takeIf { it > 0L } ?: nowEpochMs
            )
        }

        val local = locationChannelManager.getCurrentLocation() ?: return null
        return MeshLocationSnapshot(
            peerId = selfPeerId,
            lat = local.latitude,
            lon = local.longitude,
            recordedAtEpochMs = local.time.takeIf { it > 0L } ?: nowEpochMs
        )
    }

    internal fun toSnapshots(
        peerIds: Collection<String>,
        nowEpochMs: Long,
        peerInfoProvider: (String) -> PeerInfo?
    ): List<MeshLocationSnapshot> {
        var droppedMissingInfo = 0
        var droppedMissingCoord = 0
        var droppedStale = 0
        val snapshots = peerIds.mapNotNull { peerId ->
            val info = peerInfoProvider(peerId) ?: return@mapNotNull null

            val normalizedLastSeenMs = normalizeEpochMillis(info.lastSeen)
            if (normalizedLastSeenMs > 0L && nowEpochMs - normalizedLastSeenMs > MAX_REMOTE_STALENESS_MS) {
                droppedStale++
                return@mapNotNull null
            }

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
        droppedMissingInfo = peerIds.size - snapshots.size - droppedMissingCoord - droppedStale
        if (droppedMissingInfo > 0 || droppedMissingCoord > 0 || droppedStale > 0) {
            Log.d(
                TAG,
                "Snapshot dropped missingInfo=$droppedMissingInfo missingCoords=$droppedMissingCoord stale=$droppedStale"
            )
        }
        return snapshots
    }

    private fun normalizeEpochMillis(value: Long): Long {
        if (value <= 0L) return value
        return if (value < 10_000_000_000L) value * 1000L else value
    }
}


