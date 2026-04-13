package com.tracksure.android.bridgeupload.model

import com.google.gson.annotations.SerializedName

/**
 * Backend bulk upload response.
 */
data class LocationBatchUploadResponse(
    @SerializedName("inserted") val inserted: Int,
    @SerializedName("duplicates") val duplicates: Int,
    @SerializedName("totalReceived") val totalReceived: Int
)

