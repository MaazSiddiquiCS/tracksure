package com.tracksure.android.bridgeupload.integration

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
import java.io.File

/**
 * Resolves mesh peer IDs (hex strings) to backend device IDs (Long).
 */
class DeviceIdResolver(
    private val mapFile: File,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BridgeUpload"
    }

    private val mapType = object : TypeToken<Map<String, Long>>() {}.type

    @Synchronized
    fun resolve(peerId: String): Long? {
        val map = readMapUnsafe()
        val normalized = normalizePeerId(peerId)
        val resolved = map[peerId] ?: map[normalized]
        if (resolved == null) {
            Log.d(TAG, "No mapping found for peerId=$peerId normalized=$normalized")
        }
        return resolved
    }

    @Synchronized
    fun putMapping(peerId: String, subjectDeviceId: Long) {
        val map = readMapUnsafe().toMutableMap()
        val normalized = normalizePeerId(peerId)
        map[normalized] = subjectDeviceId
        writeMapUnsafe(map)
        Log.i(TAG, "Saved mapping peerId=$peerId normalized=$normalized -> $subjectDeviceId")
    }

    @Synchronized
    fun putMappings(mappings: Map<String, Long>) {
        if (mappings.isEmpty()) return
        val map = readMapUnsafe().toMutableMap()
        mappings.forEach { (peerId, subjectDeviceId) ->
            map[normalizePeerId(peerId)] = subjectDeviceId
        }
        writeMapUnsafe(map)
        Log.i(TAG, "Saved ${mappings.size} mappings to ${mapFile.absolutePath}")
    }

    @Synchronized
    fun seedDefaultsIfMissing(defaults: Map<String, Long>): Int {
        if (defaults.isEmpty()) return 0
        val map = readMapUnsafe().toMutableMap()
        var inserted = 0
        defaults.forEach { (peerId, deviceId) ->
            val key = normalizePeerId(peerId)
            if (!map.containsKey(key)) {
                map[key] = deviceId
                inserted++
            }
        }
        if (inserted > 0) {
            writeMapUnsafe(map)
            Log.i(TAG, "Seeded $inserted default peer mappings")
        }
        return inserted
    }

    @Synchronized
    fun resolveOrAssign(peerId: String, fallbackSubjectDeviceId: Long?): Long? {
        val resolved = resolve(peerId)
        if (resolved != null) return resolved
        if (fallbackSubjectDeviceId == null) return null

        val normalized = normalizePeerId(peerId)
        val map = readMapUnsafe().toMutableMap()
        map[normalized] = fallbackSubjectDeviceId
        writeMapUnsafe(map)
        Log.w(
            TAG,
            "Auto-assigned missing mapping peerId=$peerId normalized=$normalized -> $fallbackSubjectDeviceId"
        )
        return fallbackSubjectDeviceId
    }

    @Synchronized
    fun snapshot(): Map<String, Long> = readMapUnsafe()

    private fun readMapUnsafe(): Map<String, Long> {
        return try {
            if (!mapFile.exists()) return emptyMap()
            val raw = mapFile.readText()
            if (raw.isBlank()) return emptyMap()
            gson.fromJson<Map<String, Long>>(raw, mapType) ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading map file ${mapFile.absolutePath}: ${e.message}")
            emptyMap()
        }
    }

    private fun writeMapUnsafe(data: Map<String, Long>) {
        mapFile.parentFile?.mkdirs()
        mapFile.writeText(gson.toJson(data, mapType))
        Log.d(TAG, "Mapping file written ${mapFile.absolutePath} totalEntries=${data.size}")
    }

    private fun normalizePeerId(peerId: String): String {
        return peerId.trim().lowercase()
    }
}




