package com.tracksure.android.net

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
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
        @SerializedName("message")
        val message: String? = null,
        @SerializedName("detail")
        val detail: String? = null
    )

    data class DeviceLinkCreateRequest(
        @SerializedName("peerId") val peerId: String? = null,
        @SerializedName("targetDeviceId") val targetDeviceId: Long? = null,
        @SerializedName("deviceName") val deviceName: String? = null,
        @SerializedName("permissionType") val permissionType: String? = "TRACK"
    )

    data class LinkResult(
        val deviceId: Long,
        val peerId: String
    )

    data class TrackedDeviceLinkResponse(
        @SerializedName("linkId") val linkId: Long? = null,
        @SerializedName("deviceId") val deviceId: Long? = null,
        @SerializedName("peerId") val peerId: String? = null,
        @SerializedName("deviceName") val deviceName: String? = null,
        @SerializedName("ownerUserId") val ownerUserId: Long? = null,
        @SerializedName("ownerUsername") val ownerUsername: String? = null,
        @SerializedName("permissionType") val permissionType: String? = null,
        @SerializedName("active") val active: Boolean? = null,
        @SerializedName("lastSeenAt") val lastSeenAt: String? = null
    )

    data class TrackerLinkResponse(
        @SerializedName("linkId") val linkId: Long? = null,
        @SerializedName("deviceId") val deviceId: Long? = null,
        @SerializedName("peerId") val peerId: String? = null,
        @SerializedName("deviceName") val deviceName: String? = null,
        @SerializedName("trackerUserId") val trackerUserId: Long? = null,
        @SerializedName("trackerUsername") val trackerUsername: String? = null,
        @SerializedName("permissionType") val permissionType: String? = null,
        @SerializedName("active") val active: Boolean? = null
    )

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Error(val message: String, val code: Int? = null, val retryable: Boolean = false) : Result<Nothing>()
    }

    suspend fun linkDevice(accessToken: String, peerId: String, deviceName: String?): Result<LinkResult> = withContext(Dispatchers.IO) {
        val request = DeviceLinkCreateRequest(
            peerId = peerId,
            deviceName = deviceName,
            permissionType = "TRACK"
        )

        return@withContext when (val result = createLink(accessToken, request)) {
            is Result.Success -> {
                val value = result.value
                val resolvedDeviceId = value.deviceId ?: value.linkId
                if (resolvedDeviceId == null || resolvedDeviceId <= 0L) {
                    Result.Error("Invalid device link response")
                } else {
                    Result.Success(
                        LinkResult(
                            deviceId = resolvedDeviceId,
                            peerId = value.peerId ?: peerId
                        )
                    )
                }
            }

            is Result.Error -> result
        }
    }

    suspend fun createLink(accessToken: String, request: DeviceLinkCreateRequest): Result<TrackedDeviceLinkResponse> = withContext(Dispatchers.IO) {

        return@withContext try {
            val req = Request.Builder()
                .url(url("/api/device-links"))
                .header("Authorization", "Bearer $accessToken")
                .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(url("/api/device-links")).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use Result.Error(
                        extractErrorMessage(body, "Device link failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }

                val parsed = parseTrackedDeviceLinkResponse(body)
                if (parsed.deviceId == null && parsed.linkId == null && parsed.peerId.isNullOrBlank()) {
                    Result.Error("Invalid device link response", response.code)
                } else {
                    Result.Success(parsed)
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Device link failed", retryable = true)
        }
    }

    suspend fun getTrackedDevices(accessToken: String): Result<List<TrackedDeviceLinkResponse>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/device-links/tracked")
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.Error(
                        extractErrorMessage(body, "Failed to load tracked devices"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                } else {
                    val array = gson.fromJson(body, Array<JsonObject>::class.java)
                    val items = array?.mapNotNull { parseTrackedDeviceLinkResponse(it) }.orEmpty()
                    Result.Success(items)
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to load tracked devices", retryable = true)
        }
    }

    suspend fun deleteLink(accessToken: String, linkId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/device-links/$linkId")
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.Success(Unit)
                } else {
                    Result.Error(
                        extractErrorMessage(body, "Delete failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Delete failed", retryable = true)
        }
    }

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"

    private fun parseTrackedDeviceLinkResponse(rawBody: String): TrackedDeviceLinkResponse {
        return try {
            val parsed = gson.fromJson(rawBody, JsonObject::class.java)
            parseTrackedDeviceLinkResponse(parsed)
        } catch (_: Exception) {
            TrackedDeviceLinkResponse()
        }
    }

    private fun parseTrackedDeviceLinkResponse(parsed: JsonObject?): TrackedDeviceLinkResponse {
        if (parsed == null) return TrackedDeviceLinkResponse()

        fun stringValue(vararg names: String): String? {
            for (name in names) {
                val value = parsed.get(name) ?: continue
                if (!value.isJsonNull) {
                    value.asString?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            return null
        }

        fun longValue(vararg names: String): Long? {
            for (name in names) {
                val value = parsed.get(name) ?: continue
                if (!value.isJsonNull) {
                    try {
                        return value.asLong
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
            return null
        }

        fun booleanValue(vararg names: String): Boolean? {
            for (name in names) {
                val value = parsed.get(name) ?: continue
                if (!value.isJsonNull) {
                    try {
                        return value.asBoolean
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
            return null
        }

        fun nestedStringValue(container: String, vararg names: String): String? {
            val nested = parsed.getAsJsonObject(container) ?: return null
            for (name in names) {
                val value = nested.get(name) ?: continue
                if (!value.isJsonNull) {
                    value.asString?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            return null
        }

        fun nestedLongValue(container: String, vararg names: String): Long? {
            val nested = parsed.getAsJsonObject(container) ?: return null
            for (name in names) {
                val value = nested.get(name) ?: continue
                if (!value.isJsonNull) {
                    try {
                        return value.asLong
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
            return null
        }

        return TrackedDeviceLinkResponse(
            linkId = longValue("linkId", "id"),
            deviceId = longValue("deviceId", "targetDeviceId")
                ?: nestedLongValue("device", "deviceId", "id"),
            peerId = stringValue("peerId", "devicePeerId", "meshPeerId")
                ?: nestedStringValue("device", "peerId", "devicePeerId", "meshPeerId"),
            deviceName = stringValue("deviceName", "name", "nickname")
                ?: nestedStringValue("device", "deviceName", "name"),
            ownerUserId = longValue("ownerUserId", "userId")
                ?: nestedLongValue("owner", "userId", "id"),
            ownerUsername = stringValue("ownerUsername", "username")
                ?: nestedStringValue("owner", "username"),
            permissionType = stringValue("permissionType"),
            active = booleanValue("active", "isActive"),
            lastSeenAt = stringValue("lastSeenAt", "updatedAt", "createdAt")
                ?: nestedStringValue("device", "lastSeenAt", "updatedAt", "createdAt")
        )
    }

    private fun parseTrackedDeviceLinkResponse(rawArray: Array<JsonObject>?): List<TrackedDeviceLinkResponse> {
        if (rawArray == null) return emptyList()
        return rawArray.map { parseTrackedDeviceLinkResponse(it) }
            .filter { it.peerId != null || it.deviceId != null || it.linkId != null }
    }

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
