package com.tracksure.android.bridgeupload.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * File-backed queue with best-effort corruption recovery.
 */
class LocationUploadQueueStore(
    private val queueFile: File,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BridgeUpload"
    }

    private val mutex = Mutex()
    private val listType = object : TypeToken<MutableList<QueuedPoint>>() {}.type

    suspend fun enqueue(point: QueuedPoint) {
        enqueueAll(listOf(point))
    }

    suspend fun enqueueAll(points: List<QueuedPoint>) {
        if (points.isEmpty()) return
        mutex.withLock {
            val existing = readAllUnsafe()
            val existingIds = existing.asSequence().map { it.clientPointId }.toHashSet()
            var added = 0
            points.forEach { point ->
                if (existingIds.add(point.clientPointId)) {
                    existing.add(point)
                    added++
                }
            }
            writeAllUnsafe(existing)
            Log.d(TAG, "enqueueAll requested=${points.size} added=$added queueSize=${existing.size}")
        }
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
}


