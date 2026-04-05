package com.tracksure.android.bridgeupload.test

import android.content.Context
import com.tracksure.android.bridgeupload.integration.MeshLocationSnapshotAdapter
import com.tracksure.android.mesh.PeerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class MeshSnapshotAdapterTest {
    @Test
    fun `toSnapshots ignores peers without coordinates`() {
        val context = Mockito.mock(Context::class.java)
        val adapter = MeshLocationSnapshotAdapter(context)

        val peers = listOf("peer-1", "peer-2", "peer-3")
        val snapshots = adapter.toSnapshots(peers, nowEpochMs = 1000L) { peerId ->
            when (peerId) {
                "peer-1" -> peerInfo(peerId, lat = 1.0, lon = 2.0, lastSeen = 10L)
                "peer-2" -> peerInfo(peerId, lat = null, lon = 2.0, lastSeen = 20L)
                else -> null
            }
        }

        assertEquals(1, snapshots.size)
        assertEquals("peer-1", snapshots.first().peerId)
        assertEquals(1.0, snapshots.first().lat, 0.0)
        assertEquals(2.0, snapshots.first().lon, 0.0)
    }

    @Test
    fun `toSnapshots falls back to now when lastSeen is non-positive`() {
        val context = Mockito.mock(Context::class.java)
        val adapter = MeshLocationSnapshotAdapter(context)

        val snapshots = adapter.toSnapshots(listOf("peer-1"), nowEpochMs = 1234L) {
            peerInfo("peer-1", lat = 9.0, lon = 8.0, lastSeen = 0L)
        }

        assertTrue(snapshots.isNotEmpty())
        assertEquals(1234L, snapshots.first().recordedAtEpochMs)
    }

    private fun peerInfo(peerId: String, lat: Double?, lon: Double?, lastSeen: Long): PeerInfo {
        return PeerInfo(
            id = peerId,
            nickname = "peer",
            isConnected = true,
            isDirectConnection = false,
            noisePublicKey = null,
            signingPublicKey = null,
            isVerifiedNickname = false,
            lastSeen = lastSeen,
            latitude = lat,
            longitude = lon
        )
    }
}

