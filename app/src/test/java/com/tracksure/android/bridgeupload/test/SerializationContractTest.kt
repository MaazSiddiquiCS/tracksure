package com.tracksure.android.bridgeupload.test

import com.google.gson.Gson
import com.tracksure.android.bridgeupload.model.LocationBatchUploadRequest
import com.tracksure.android.bridgeupload.model.LocationPointDto
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationContractTest {
    private val gson = Gson()

    @Test
    fun `request json uses expected backend field names`() {
        val request = LocationBatchUploadRequest(
            subjectDeviceId = 9007199254740991L,
            uploaderDeviceId = 9007199254740991L,
            points = listOf(
                LocationPointDto(
                    clientPointId = "abc-1",
                    lat = -90.0,
                    lon = -180.0,
                    accuracy = 0.1,
                    recordedAt = "2026-04-04T14:19:37.816Z",
                    source = "GPS"
                )
            )
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"subjectDeviceId\""))
        assertTrue(json.contains("\"uploaderDeviceId\""))
        assertTrue(json.contains("\"points\""))
        assertTrue(json.contains("\"clientPointId\""))
        assertTrue(json.contains("\"lat\""))
        assertTrue(json.contains("\"lon\""))
        assertTrue(json.contains("\"accuracy\""))
        assertTrue(json.contains("\"recordedAt\""))
        assertTrue(json.contains("\"source\""))
    }
}

