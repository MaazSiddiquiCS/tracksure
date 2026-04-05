package com.tracksure.android.bridgeupload.storage

/**
 * Persisted queue record for a location point pending backend upload.
 */
data class QueuedPoint(
    val subjectPeerId: String? = null,
    val uploaderDeviceId: Long,
    val pendingBatchUuid: String? = null,
    val clientPointId: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Double,
    val recordedAt: String,
    val source: String,
    val queuedAtEpochMs: Long,
    val attemptCount: Int,
    val nextAttemptAtEpochMs: Long
)

/**
 * Public input model for programmatic enqueue operations.
 */
data class QueuedPointInput(
    val subjectPeerId: String,
    val uploaderDeviceId: Long,
    val pendingBatchUuid: String? = null,
    val clientPointId: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Double,
    val recordedAt: String,
    val source: String = "MESH"
)

