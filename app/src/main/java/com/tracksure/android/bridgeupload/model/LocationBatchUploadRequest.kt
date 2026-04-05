package com.tracksure.android.bridgeupload.model

/**
 * Backend bulk upload request.
 */
data class LocationBatchUploadRequest(
    val clientBatchUuid: String,
    val subjectPeerId: String,
    val uploaderDeviceId: Long,
    val points: List<LocationPointDto>
)

