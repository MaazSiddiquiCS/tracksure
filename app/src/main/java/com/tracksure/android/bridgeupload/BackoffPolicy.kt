package com.tracksure.android.bridgeupload

/**
 * Exponential backoff helper: 15s, 30s, 60s, 120s ... up to max cap.
 */
object BackoffPolicy {
    fun nextDelayMs(attemptCount: Int, maxBackoffMs: Long): Long {
        val base = 15_000L
        val safeAttempt = attemptCount.coerceAtLeast(1)
        val shift = (safeAttempt - 1).coerceAtMost(20)
        val raw = base * (1L shl shift)
        return raw.coerceAtMost(maxBackoffMs)
    }
}

