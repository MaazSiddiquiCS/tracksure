package com.tracksure.android.config

import android.content.Context
import com.tracksure.android.R

object ApiConfig {
    private const val BRIDGE_UPLOAD_PATH = "/v1/locations:batch"

    fun baseUrl(context: Context): String {
        return normalize(context.getString(R.string.api_base_url))
    }

    fun resolveBridgeUploadEndpoint(raw: String): String {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return ""
        return if (normalized.contains(BRIDGE_UPLOAD_PATH)) {
            normalized
        } else {
            normalized.trimEnd('/') + BRIDGE_UPLOAD_PATH
        }
    }

    private fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }
}
