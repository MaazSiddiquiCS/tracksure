package com.tracksure.android.bridgeupload

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * App-level runtime bootstrap for bridge upload sidecar.
 */
object BridgeUploadRuntime {
    private const val TAG = "BridgeUpload"
    private const val META_ENABLED = "com.tracksure.android.bridgeupload.ENABLED"
    private const val META_ENDPOINT = "com.tracksure.android.bridgeupload.ENDPOINT_URL"
    private const val META_UPLOADER_ID = "com.tracksure.android.bridgeupload.UPLOADER_DEVICE_ID"
    private const val META_REQUIRE_WIFI = "com.tracksure.android.bridgeupload.REQUIRE_WIFI"
    private const val META_REQUIRE_VALIDATED = "com.tracksure.android.bridgeupload.REQUIRE_VALIDATED"
    private const val META_PEER_MAP_JSON = "com.tracksure.android.bridgeupload.PEER_ID_MAP_JSON"
    private const val META_AUTO_ASSIGN = "com.tracksure.android.bridgeupload.AUTO_ASSIGN_MISSING_PEERS"

    @Volatile
    private var scheduler: BridgeUploadScheduler? = null

    @Synchronized
    fun start(context: Context) {
        if (scheduler != null) {
            Log.d(TAG, "Bridge runtime already running")
            return
        }

        val appContext = context.applicationContext
        val meta = readAppMeta(appContext) ?: run {
            Log.w(TAG, "Bridge runtime disabled: failed to read app metadata")
            return
        }

        val enabled = meta.getBoolean(META_ENABLED, false)
        if (!enabled) {
            Log.i(TAG, "Bridge runtime disabled by manifest meta-data ($META_ENABLED=false)")
            return
        }

        val endpointUrl = readStringMeta(appContext, meta, META_ENDPOINT).orEmpty().trim()
        if (endpointUrl.isBlank()) {
            Log.w(TAG, "Bridge runtime disabled: missing $META_ENDPOINT")
            return
        }

        val uploaderRaw = readStringMeta(appContext, meta, META_UPLOADER_ID).orEmpty().trim()
        val uploaderDeviceId = readLongMeta(appContext, meta, META_UPLOADER_ID)
        if (uploaderDeviceId == null) {
            Log.w(TAG, "Bridge runtime disabled: invalid/missing $META_UPLOADER_ID='$uploaderRaw'")
            return
        }

        val config = BridgeUploadConfig(
            endpointUrl = endpointUrl,
            uploaderDeviceId = uploaderDeviceId,
            requireWifiTransport = meta.getBoolean(META_REQUIRE_WIFI, true),
            requireValidatedNetwork = meta.getBoolean(META_REQUIRE_VALIDATED, true),
            autoAssignMissingPeerToUploader = meta.getBoolean(META_AUTO_ASSIGN, true)
        )

        bootstrapPeerMappings(appContext, config, meta)

        val orchestrator = BridgeUploadFactory.create(appContext, config)
        scheduler = BridgeUploadScheduler(orchestrator, config).also { it.start() }
        Log.i(
            TAG,
            "Bridge runtime started endpoint=$endpointUrl uploaderDeviceId=$uploaderDeviceId " +
                "requireWifi=${config.requireWifiTransport} requireValidated=${config.requireValidatedNetwork} " +
                "autoAssignMissingPeers=${config.autoAssignMissingPeerToUploader}"
        )
    }

    @Synchronized
    fun stop() {
        scheduler?.stop()
        scheduler = null
        Log.i(TAG, "Bridge runtime stopped")
    }

    private fun readAppMeta(context: Context) = try {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
    } catch (e: Exception) {
        Log.e(TAG, "Unable to load manifest metadata: ${e.message}")
        null
    }

    private fun readStringMeta(context: Context, meta: android.os.Bundle, key: String): String? {
        val raw = meta.get(key) ?: return null
        return when (raw) {
            is String -> raw
            is Number -> {
                // Treat integer values as potential string resource IDs first.
                val id = raw.toInt()
                try {
                    context.getString(id)
                } catch (_: Exception) {
                    raw.toString()
                }
            }
            else -> raw.toString()
        }
    }

    private fun readLongMeta(context: Context, meta: android.os.Bundle, key: String): Long? {
        val raw = meta.get(key) ?: return null
        return when (raw) {
            is Number -> {
                val asInt = raw.toInt()
                val fromRes = try {
                    context.getString(asInt).trim().toLongOrNull()
                } catch (_: Exception) {
                    null
                }
                fromRes ?: raw.toLong()
            }
            is String -> raw.trim().toLongOrNull()
            else -> raw.toString().trim().toLongOrNull()
        }
    }

    private fun bootstrapPeerMappings(context: Context, config: BridgeUploadConfig, meta: android.os.Bundle) {
        val rawMapJson = readStringMeta(context, meta, META_PEER_MAP_JSON).orEmpty().trim()
        if (rawMapJson.isBlank() || rawMapJson == "{}") {
            Log.d(TAG, "No default peer mapping metadata provided")
            return
        }

        val defaults = try {
            val mapType = object : TypeToken<Map<String, Long>>() {}.type
            Gson().fromJson<Map<String, Long>>(rawMapJson, mapType).orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Invalid $META_PEER_MAP_JSON metadata: ${e.message}")
            emptyMap()
        }

        if (defaults.isEmpty()) {
            Log.d(TAG, "Default peer mapping metadata parsed to empty map")
            return
        }

        val resolver = BridgeUploadFactory.createDeviceIdResolver(context, config)
        val inserted = resolver.seedDefaultsIfMissing(defaults)
        Log.i(TAG, "Default peer mapping bootstrap done defaults=${defaults.size} inserted=$inserted")
    }
}




