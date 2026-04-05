package com.tracksure.android.bridgeupload

/**
 * Exponential backoff helper: 15s, 30s, 60s, 120s ... up to max cap.
 */
object BackoffPolicy {
    fun nextDelayMs(attemptCount: Int, maxBackoffMs: Long, jitterRatio: Double = 0.0): Long {
        val base = 15_000L
        val safeAttempt = attemptCount.coerceAtLeast(1)
        val shift = (safeAttempt - 1).coerceAtMost(20)
        val raw = base * (1L shl shift)
        val capped = raw.coerceAtMost(maxBackoffMs)
        if (jitterRatio <= 0.0) return capped

        val ratio = jitterRatio.coerceIn(0.0, 0.5)
        val jitterWindow = (capped * ratio).toLong().coerceAtLeast(1L)
        val random = kotlin.random.Random.nextLong(-jitterWindow, jitterWindow + 1)
        return (capped + random).coerceIn(1L, maxBackoffMs)
    }
}

