package com.tracksure.android.bridgeupload.model

/**
 * Backend bulk upload response.
 */
data class LocationBatchUploadResponse(
    val inserted: Int,
    val duplicates: Int,
    val totalReceived: Int
)

