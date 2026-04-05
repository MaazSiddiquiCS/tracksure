package com.tracksure.android.bridgeupload

import android.util.Log
import com.tracksure.android.bridgeupload.integration.DeviceIdResolver
import com.tracksure.android.bridgeupload.integration.MeshLocationSnapshotAdapter
import com.tracksure.android.bridgeupload.model.FlushReport
import com.tracksure.android.bridgeupload.model.LocationBatchUploadRequest
import com.tracksure.android.bridgeupload.model.LocationPointDto
import com.tracksure.android.bridgeupload.net.ConnectivityGate
import com.tracksure.android.bridgeupload.net.LocationBatchApiClient
import com.tracksure.android.bridgeupload.storage.LocationUploadQueueStore
import com.tracksure.android.bridgeupload.storage.QueuedPoint
import com.tracksure.android.bridgeupload.storage.QueuedPointInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Sidecar coordinator that captures mesh snapshots, queues points, and flushes to backend.
 */
class BridgeUploadOrchestrator(
    private val config: BridgeUploadConfig,
    private val snapshotAdapter: MeshLocationSnapshotAdapter,
    private val deviceIdResolver: DeviceIdResolver,
    private val queueStore: LocationUploadQueueStore,
    private val connectivityGate: ConnectivityGate,
    private val apiClient: LocationBatchApiClient
) {
    data class CaptureReport(
        val captured: Int,
        val enqueued: Int,
        val skippedMissingMapping: Int,
        val skippedInvalid: Int
    )

    suspend fun enqueuePoint(
        subjectDeviceId: Long,
        uploaderDeviceId: Long,
        clientPointId: String,
        lat: Double,
        lon: Double,
        accuracy: Double,
        recordedAtIso: String,
        source: String
    ) {
        enqueuePoints(
            listOf(
                QueuedPointInput(
                    subjectDeviceId = subjectDeviceId,
                    uploaderDeviceId = uploaderDeviceId,
                    clientPointId = clientPointId,
                    lat = lat,
                    lon = lon,
                    accuracy = accuracy,
                    recordedAt = recordedAtIso,
                    source = source
                )
            )
        )
    }

    suspend fun enqueuePoints(points: List<QueuedPointInput>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val valid = points.filter { isValidLatLon(it.lat, it.lon) }
        if (valid.size != points.size) {
            Log.w(TAG, "enqueuePoints dropped invalid=${points.size - valid.size}")
        }
        val queued = valid.map {
            QueuedPoint(
                subjectDeviceId = it.subjectDeviceId,
                uploaderDeviceId = it.uploaderDeviceId,
                clientPointId = it.clientPointId,
                lat = it.lat,
                lon = it.lon,
                accuracy = it.accuracy,
                recordedAt = it.recordedAt,
                source = it.source,
                queuedAtEpochMs = now,
                attemptCount = 0,
                nextAttemptAtEpochMs = now
            )
        }
        queueStore.enqueueAll(queued)
        Log.d(TAG, "enqueuePoints accepted=${queued.size}")
    }

    suspend fun captureSnapshotAndEnqueue(): CaptureReport = withContext(Dispatchers.IO) {
        val snapshots = snapshotAdapter.captureSnapshots()
        var skippedMissingMapping = 0
        var skippedInvalid = 0

        val now = System.currentTimeMillis()
        val toQueue = mutableListOf<QueuedPoint>()

        snapshots.forEach { snap ->
            if (!isValidLatLon(snap.lat, snap.lon)) {
                skippedInvalid++
                Log.w(TAG, "Skipping invalid coordinates peerId=${snap.peerId} lat=${snap.lat} lon=${snap.lon}")
                return@forEach
            }
            val subject = deviceIdResolver.resolveOrAssign(
                peerId = snap.peerId,
                fallbackSubjectDeviceId = if (config.autoAssignMissingPeerToUploader) config.uploaderDeviceId else null
            )
            if (subject == null) {
                skippedMissingMapping++
                Log.i(TAG, "Skipping peer with no mapping peerId=${snap.peerId}")
                return@forEach
            }
            toQueue += QueuedPoint(
                subjectDeviceId = subject,
                uploaderDeviceId = config.uploaderDeviceId,
                clientPointId = UUID.randomUUID().toString(),
                lat = snap.lat,
                lon = snap.lon,
                accuracy = snap.accuracy,
                recordedAt = toIsoUtc(snap.recordedAtEpochMs),
                source = snap.source,
                queuedAtEpochMs = now,
                attemptCount = 0,
                nextAttemptAtEpochMs = now
            )
        }

        queueStore.enqueueAll(toQueue)
        Log.d(
            TAG,
            "captureSnapshotAndEnqueue captured=${snapshots.size} enqueued=${toQueue.size} " +
                "skippedMapping=$skippedMissingMapping skippedInvalid=$skippedInvalid"
        )

        CaptureReport(
            captured = snapshots.size,
            enqueued = toQueue.size,
            skippedMissingMapping = skippedMissingMapping,
            skippedInvalid = skippedInvalid
        )
    }

    suspend fun flushNow(maxBatchSize: Int = config.maxBatchSizeDefault): FlushReport = withContext(Dispatchers.IO) {
        val queuedBefore = queueStore.size()
        val captureReport = captureSnapshotAndEnqueue()
        Log.d(TAG, "flushNow queuedBefore=$queuedBefore captureEnqueued=${captureReport.enqueued}")

        val networkEligible = connectivityGate.canUploadNow()
        if (!networkEligible) {
            return@withContext FlushReport(
                queuedBefore = queuedBefore,
                attempted = 0,
                uploaded = 0,
                failedRetryable = 0,
                failedFatal = 0,
                skippedInvalid = captureReport.skippedInvalid,
                skippedMissingMapping = captureReport.skippedMissingMapping,
                remaining = queueStore.size(),
                networkEligible = false
            )
        }

        val ready = queueStore.peekBatch(maxBatchSize)
        if (ready.isEmpty()) {
            Log.d(TAG, "flushNow no ready points")
            return@withContext FlushReport(
                queuedBefore = queuedBefore,
                attempted = 0,
                uploaded = 0,
                failedRetryable = 0,
                failedFatal = 0,
                skippedInvalid = captureReport.skippedInvalid,
                skippedMissingMapping = captureReport.skippedMissingMapping,
                remaining = queueStore.size(),
                networkEligible = true
            )
        }

        val valid = ready.filter { isValidLatLon(it.lat, it.lon) }
        val skippedInvalid = captureReport.skippedInvalid + (ready.size - valid.size)
        val grouped = valid.groupBy { it.subjectDeviceId to it.uploaderDeviceId }
        Log.d(TAG, "flushNow ready=${ready.size} valid=${valid.size} groups=${grouped.size}")

        val uploadedIds = mutableSetOf<String>()
        val retryUpdates = mutableListOf<QueuedPoint>()
        var failedRetryable = 0
        var failedFatal = 0

        grouped.forEach { (key, points) ->
            Log.d(
                TAG,
                "Uploading group subject=${key.first} uploader=${key.second} points=${points.size}"
            )
            val request = LocationBatchUploadRequest(
                subjectDeviceId = key.first,
                uploaderDeviceId = key.second,
                points = points.map {
                    LocationPointDto(
                        clientPointId = it.clientPointId,
                        lat = it.lat,
                        lon = it.lon,
                        accuracy = it.accuracy,
                        recordedAt = it.recordedAt,
                        source = it.source
                    )
                }
            )

            when (val result = apiClient.uploadBatch(request)) {
                is LocationBatchApiClient.UploadResult.Success -> {
                    uploadedIds += points.map { it.clientPointId }
                    Log.d(TAG, "flush success inserted=${result.response.inserted} duplicates=${result.response.duplicates}")
                }
                is LocationBatchApiClient.UploadResult.RetryableError -> {
                    failedRetryable += points.size
                    retryUpdates += points.map { nextRetry(it) }
                    Log.w(TAG, "flush retryable error code=${result.code} msg=${result.message}")
                }
                is LocationBatchApiClient.UploadResult.FatalError -> {
                    failedFatal += points.size
                    retryUpdates += points.map { nextRetry(it, fatal = true) }
                    Log.w(TAG, "flush fatal error code=${result.code} msg=${result.message}")
                }
            }
        }

        val invalidIds = ready.filterNot { isValidLatLon(it.lat, it.lon) }.map { it.clientPointId }.toSet()
        queueStore.removeByClientPointIds(uploadedIds + invalidIds)
        queueStore.upsertAll(retryUpdates)

        val remaining = queueStore.size()
        Log.i(
            TAG,
            "flushNow done attempted=${ready.size} uploaded=${uploadedIds.size} retryable=$failedRetryable " +
                "fatal=$failedFatal invalidSkipped=${invalidIds.size} remaining=$remaining"
        )

        FlushReport(
            queuedBefore = queuedBefore,
            attempted = ready.size,
            uploaded = uploadedIds.size,
            failedRetryable = failedRetryable,
            failedFatal = failedFatal,
            skippedInvalid = skippedInvalid,
            skippedMissingMapping = captureReport.skippedMissingMapping,
            remaining = remaining,
            networkEligible = true
        )
    }

    internal fun nextBackoffMs(attemptCount: Int): Long {
        return BackoffPolicy.nextDelayMs(attemptCount, config.maxBackoffMs)
    }

    private fun nextRetry(point: QueuedPoint, fatal: Boolean = false): QueuedPoint {
        val nextAttempt = point.attemptCount + 1
        val baseDelay = nextBackoffMs(nextAttempt)
        val delayMs = if (fatal) (baseDelay * 2).coerceAtMost(config.maxBackoffMs) else baseDelay
        return point.copy(
            attemptCount = nextAttempt,
            nextAttemptAtEpochMs = System.currentTimeMillis() + delayMs
        )
    }

    private fun toIsoUtc(epochMs: Long): String {
        return ISO_FORMATTER.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))
    }

    private fun isValidLatLon(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    companion object {
        private const val TAG = "BridgeUpload"
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}


