package com.tracksure.android.net

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DeviceLinkApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson()
) {
    private data class ApiErrorResponse(
        val message: String? = null,
        val detail: String? = null
    )

    data class LinkResult(
        val deviceId: Long,
        val peerId: String
    )

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Error(val message: String, val code: Int? = null, val retryable: Boolean = false) : Result<Nothing>()
    }

    suspend fun linkDevice(accessToken: String, peerId: String, deviceName: String?): Result<LinkResult> = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, Any>("peerId" to peerId)
        if (!deviceName.isNullOrBlank()) payload["deviceName"] = deviceName

        return@withContext try {
            val req = Request.Builder()
                .url(url("/api/device/link"))
                .header("Authorization", "Bearer $accessToken")
                .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(url("/api/device/link")).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use Result.Error(
                        extractErrorMessage(body, "Device link failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }

                val parsed = gson.fromJson(body, JsonObject::class.java)
                val deviceId = parsed?.get("deviceId")?.asLong
                    ?: parsed?.get("targetDeviceId")?.asLong
                val resolvedPeer = parsed?.get("peerId")?.asString?.ifBlank { null } ?: peerId

                if (deviceId == null || deviceId <= 0L) {
                    Result.Error("Invalid device link response", response.code)
                } else {
                    Result.Success(LinkResult(deviceId = deviceId, peerId = resolvedPeer))
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Device link failed", retryable = true)
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"

    private fun extractErrorMessage(rawBody: String, fallback: String): String {
        if (rawBody.isBlank()) return fallback
        return try {
            val parsed = gson.fromJson(rawBody, ApiErrorResponse::class.java)
            parsed?.message?.takeIf { it.isNotBlank() }
                ?: parsed?.detail?.takeIf { it.isNotBlank() }
                ?: rawBody
        } catch (_: Exception) {
            rawBody
        }
    }
}
