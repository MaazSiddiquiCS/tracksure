package com.tracksure.android.bridgeupload.test

import com.tracksure.android.bridgeupload.storage.LocationUploadQueueStore
import com.tracksure.android.bridgeupload.storage.QueuedPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class QueueDedupTest {
    @Test
    fun `enqueue deduplicates by clientPointId`() = runBlocking {
        val tempFile = File.createTempFile("bridge-upload-queue", ".json")
        tempFile.deleteOnExit()
        val store = LocationUploadQueueStore(tempFile)

        val point = QueuedPoint(
            subjectDeviceId = 1L,
            uploaderDeviceId = 2L,
            clientPointId = "same-id",
            lat = 10.0,
            lon = 20.0,
            accuracy = 0.1,
            recordedAt = "2026-04-04T14:19:37.816Z",
            source = "MESH",
            queuedAtEpochMs = 1L,
            attemptCount = 0,
            nextAttemptAtEpochMs = 1L
        )

        store.enqueue(point)
        store.enqueue(point.copy(lat = 11.0))

        assertEquals(1, store.size())
        assertEquals(1, store.peekBatch(10, nowEpochMs = Long.MAX_VALUE).size)
    }
}

