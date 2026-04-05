package com.tracksure.android.bridgeupload.model

/**
 * Flush execution stats.
 */
data class FlushReport(
    val queuedBefore: Int,
    val attempted: Int,
    val uploaded: Int,
    val failedRetryable: Int,
    val failedFatal: Int,
    val skippedInvalid: Int,
    val skippedMissingMapping: Int,
    val remaining: Int,
    val networkEligible: Boolean
)

