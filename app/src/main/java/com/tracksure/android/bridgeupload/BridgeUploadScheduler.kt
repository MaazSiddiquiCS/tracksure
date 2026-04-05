package com.tracksure.android.bridgeupload

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional sidecar loop for periodic capture and flush.
 * Not auto-started.
 */
class BridgeUploadScheduler(
    private val orchestrator: BridgeUploadOrchestrator,
    private val config: BridgeUploadConfig
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var flushJob: Job? = null

    @Synchronized
    fun start() {
        if (captureJob?.isActive == true || flushJob?.isActive == true) {
            Log.d(TAG, "Scheduler already running")
            return
        }
        Log.i(TAG, "Scheduler starting captureInterval=${config.captureIntervalMs} flushInterval=${config.flushIntervalMs}")

        captureJob = scope.launch {
            while (isActive) {
                try {
                    val report = orchestrator.captureSnapshotAndEnqueue()
                    Log.d(TAG, "Capture tick captured=${report.captured} enqueued=${report.enqueued}")
                } catch (e: Exception) {
                    Log.w(TAG, "capture loop failed: ${e.message}")
                }
                delay(config.captureIntervalMs)
            }
        }

        flushJob = scope.launch {
            while (isActive) {
                try {
                    val report = orchestrator.flushNow(config.maxBatchSizeDefault)
                    Log.d(
                        TAG,
                        "Flush tick attempted=${report.attempted} uploaded=${report.uploaded} remaining=${report.remaining} networkEligible=${report.networkEligible}"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "flush loop failed: ${e.message}")
                }
                delay(config.flushIntervalMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        Log.i(TAG, "Scheduler stopping")
        captureJob?.cancel()
        flushJob?.cancel()
        captureJob = null
        flushJob = null
    }

    companion object {
        private const val TAG = "BridgeUpload"
    }
}


