package com.tracksure.android.bridgeupload.test

import com.tracksure.android.bridgeupload.BackoffPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffPolicyTest {
    @Test
    fun `backoff doubles and respects max cap`() {
        val cap = 600_000L
        assertEquals(15_000L, BackoffPolicy.nextDelayMs(1, cap))
        assertEquals(30_000L, BackoffPolicy.nextDelayMs(2, cap))
        assertEquals(60_000L, BackoffPolicy.nextDelayMs(3, cap))
        assertEquals(120_000L, BackoffPolicy.nextDelayMs(4, cap))
        assertEquals(600_000L, BackoffPolicy.nextDelayMs(20, cap))
    }
}

