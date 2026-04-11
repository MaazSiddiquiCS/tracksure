package com.tracksure.android.bridgeupload.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.abs

/**
 * File-backed queue with best-effort corruption recovery.
 */
class LocationUploadQueueStore(
    private val queueFile: File,
    private val lastPointFile: File = File(queueFile.parentFile, "${queueFile.name}.last.json"),
    private val minMovementMeters: Double = 100.0,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BridgeUpload"
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    private val mutex = Mutex()
    private val listType = object : TypeToken<MutableList<QueuedPoint>>() {}.type
    private val lastPointType = object : TypeToken<MutableMap<String, LastPointSignature>>() {}.type

    suspend fun enqueue(point: QueuedPoint) {
        enqueueAll(listOf(point))
    }

    suspend fun enqueueAll(points: List<QueuedPoint>) {
        if (points.isEmpty()) return
        mutex.withLock {
            val existing = compactQueueUnsafe(readAllUnsafe())
            val lastAcceptedByStream = readLastAcceptedUnsafe()
            val existingIds = existing.asSequence().map { it.clientPointId }.toHashSet()
            var added = 0
            var skippedConsecutiveDuplicate = 0
            var skippedStaticDuplicate = 0
            points.forEach { point ->
                if (!existingIds.add(point.clientPointId)) return@forEach
                if (isConsecutiveDuplicate(point, existing)) {
                    skippedConsecutiveDuplicate++
                    return@forEach
                }

                val streamKey = streamKeyOf(point)
                val lastAccepted = lastAcceptedByStream[streamKey]
                if (lastAccepted != null && isSamePoint(lastAccepted, point)) {
                    skippedStaticDuplicate++
                    return@forEach
                }

                existing.add(point)
                lastAcceptedByStream[streamKey] = LastPointSignature(
                    lat = point.lat,
                    lon = point.lon,
                    accuracy = point.accuracy
                )
                added++
            }
            writeAllUnsafe(existing)
            writeLastAcceptedUnsafe(lastAcceptedByStream)
            Log.d(
                TAG,
                "enqueueAll requested=${points.size} added=$added skippedConsecutiveDuplicate=$skippedConsecutiveDuplicate skippedStaticDuplicate=$skippedStaticDuplicate queueSize=${existing.size}"
            )
        }
    }

    private fun compactQueueUnsafe(existing: MutableList<QueuedPoint>): MutableList<QueuedPoint> {
        if (existing.isEmpty()) return existing

        val compacted = mutableListOf<QueuedPoint>()
        val lastByStream = mutableMapOf<String, LastPointSignature>()

        existing.sortedBy { it.queuedAtEpochMs }.forEach { point ->
            val key = streamKeyOf(point)
            val last = lastByStream[key]
            if (last != null && isSamePoint(last, point)) {
                return@forEach
            }

            compacted += point
            lastByStream[key] = LastPointSignature(
                lat = point.lat,
                lon = point.lon,
                accuracy = point.accuracy
            )
        }

        if (compacted.size != existing.size) {
            Log.d(TAG, "compactQueue removed=${existing.size - compacted.size} kept=${compacted.size}")
        }
        return compacted
    }

    private fun isConsecutiveDuplicate(candidate: QueuedPoint, queue: List<QueuedPoint>): Boolean {
        val previous = queue.asReversed().firstOrNull {
            it.subjectPeerId == candidate.subjectPeerId &&
                it.uploaderDeviceId == candidate.uploaderDeviceId &&
                it.source == candidate.source
        } ?: return false

        return isSamePoint(
            LastPointSignature(previous.lat, previous.lon, previous.accuracy),
            candidate
        )
    }

    suspend fun peekBatch(maxItems: Int, nowEpochMs: Long = System.currentTimeMillis()): List<QueuedPoint> {
        if (maxItems <= 0) return emptyList()
        return mutex.withLock {
            val eligible = readAllUnsafe()
                .asSequence()
                .filter { it.nextAttemptAtEpochMs <= nowEpochMs }
                .sortedWith(compareBy<QueuedPoint> { it.nextAttemptAtEpochMs }.thenBy { it.queuedAtEpochMs })
                .take(maxItems)
                .toList()
            Log.d(TAG, "peekBatch max=$maxItems eligible=${eligible.size}")
            eligible
        }
    }

    suspend fun removeByClientPointIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val existing = readAllUnsafe()
            val filtered = existing.filterNot { ids.contains(it.clientPointId) }.toMutableList()
            writeAllUnsafe(filtered)
            Log.d(TAG, "removeByClientPointIds removed=${existing.size - filtered.size} queueSize=${filtered.size}")
        }
    }

    suspend fun upsertAll(points: List<QueuedPoint>) {
        if (points.isEmpty()) return
        mutex.withLock {
            val existing = readAllUnsafe()
            val byId = existing.associateBy { it.clientPointId }.toMutableMap()
            points.forEach { point -> byId[point.clientPointId] = point }
            val merged = byId.values.sortedBy { it.queuedAtEpochMs }.toMutableList()
            writeAllUnsafe(merged)
            Log.d(TAG, "upsertAll updated=${points.size} queueSize=${merged.size}")
        }
    }

    suspend fun size(): Int {
        return mutex.withLock { readAllUnsafe().size }
    }

    private fun readAllUnsafe(): MutableList<QueuedPoint> {
        return try {
            if (!queueFile.exists()) return mutableListOf()
            val raw = queueFile.readText()
            if (raw.isBlank()) return mutableListOf()
            gson.fromJson<MutableList<QueuedPoint>>(raw, listType) ?: mutableListOf()
        } catch (e: Exception) {
            Log.w(TAG, "Queue file parse failed, resetting ${queueFile.absolutePath}: ${e.message}")
            mutableListOf()
        }
    }

    private fun writeAllUnsafe(items: MutableList<QueuedPoint>) {
        queueFile.parentFile?.mkdirs()
        queueFile.writeText(gson.toJson(items, listType))
        Log.v(TAG, "Queue file written ${queueFile.absolutePath} items=${items.size}")
    }

    private fun readLastAcceptedUnsafe(): MutableMap<String, LastPointSignature> {
        return try {
            if (!lastPointFile.exists()) return mutableMapOf()
            val raw = lastPointFile.readText()
            if (raw.isBlank()) return mutableMapOf()
            gson.fromJson<MutableMap<String, LastPointSignature>>(raw, lastPointType) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.w(TAG, "Last-point file parse failed, resetting ${lastPointFile.absolutePath}: ${e.message}")
            mutableMapOf()
        }
    }

    private fun writeLastAcceptedUnsafe(lastAcceptedByStream: MutableMap<String, LastPointSignature>) {
        lastPointFile.parentFile?.mkdirs()
        lastPointFile.writeText(gson.toJson(lastAcceptedByStream, lastPointType))
    }

    private fun isSamePoint(previous: LastPointSignature, candidate: QueuedPoint): Boolean {
        return distanceMeters(previous.lat, previous.lon, candidate.lat, candidate.lon) < minMovementMeters
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val sinLat = kotlin.math.sin(dLat / 2)
        val sinLon = kotlin.math.sin(dLon / 2)
        val a = sinLat * sinLat + kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) * sinLon * sinLon
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun streamKeyOf(point: QueuedPoint): String {
        val subjectPeerId = point.subjectPeerId?.trim()?.lowercase().orEmpty()
        val source = point.source.trim().uppercase()
        return "$subjectPeerId|${point.uploaderDeviceId}|$source"
    }

    private data class LastPointSignature(
        val lat: Double,
        val lon: Double,
        val accuracy: Double
    )
}


