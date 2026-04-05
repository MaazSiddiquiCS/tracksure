package com.tracksure.android.bridgeupload.model

/**
 * Backend bulk upload request.
 */
data class LocationBatchUploadRequest(
    val subjectDeviceId: Long,
    val uploaderDeviceId: Long,
    val points: List<LocationPointDto>
)

