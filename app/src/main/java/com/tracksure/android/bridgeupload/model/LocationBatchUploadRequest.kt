package com.tracksure.android.bridgeupload.model

import com.google.gson.annotations.SerializedName

/**
 * Backend bulk upload request.
 */
data class LocationBatchUploadRequest(
    @SerializedName("clientBatchUuid") val clientBatchUuid: String,
    @SerializedName("subjectPeerId") val subjectPeerId: String,
    @SerializedName("uploaderDeviceId") val uploaderDeviceId: Long,
    @SerializedName("points") val points: List<LocationPointDto>
)

