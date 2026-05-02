package com.tracksure.android.net

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ProfileApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "ProfileApiClient"
    }

    data class ProfileRequest(
        @SerializedName("fullName") val fullName: String?,
        @SerializedName("phoneNumber") val phoneNumber: String?,
        @SerializedName("bio") val bio: String?,
        @SerializedName("profilePic") val profilePic: String?
    )

    data class ProfileResponse(
        @SerializedName("profileId") val profileId: Long?,
        @SerializedName("fullName") val fullName: String?,
        @SerializedName("phoneNumber") val phoneNumber: String?,
        @SerializedName("bio") val bio: String?,
        @SerializedName("profilePic") val profilePic: String?,
        @SerializedName("userId") val userId: Long?
    )

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Error(val message: String, val code: Int? = null, val retryable: Boolean = false) : Result<Nothing>()
    }

    private data class ApiErrorResponse(
        @SerializedName("message") val message: String? = null,
        @SerializedName("detail") val detail: String? = null
    )

    private val normalizedBaseUrl: String = normalizeBaseUrl(baseUrl)

    init {
        Log.i(TAG, "Initialized baseUrl='$normalizedBaseUrl' (raw='${baseUrl.trim()}')")
    }

    suspend fun getMine(accessToken: String): Result<ProfileResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/profile/me")
            Log.d(TAG, "GET $endpoint")
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = gson.fromJson(body, ProfileResponse::class.java)
                    if (parsed == null) Result.Error("Invalid profile response", response.code)
                    else Result.Success(parsed)
                } else {
                    Result.Error(
                        extractErrorMessage(body, "Profile fetch failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile get exception baseUrl=$normalizedBaseUrl type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Profile fetch failed", e), retryable = true)
        }
    }

    suspend fun createMine(accessToken: String, request: ProfileRequest): Result<ProfileResponse> {
        return sendJson("POST", "/api/profile", accessToken, request)
    }

    suspend fun updateMine(accessToken: String, request: ProfileRequest): Result<ProfileResponse> {
        return sendJson("PUT", "/api/profile/me", accessToken, request)
    }

    suspend fun deleteMine(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/profile/me")
            Log.d(TAG, "DELETE $endpoint")
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Result.Success(Unit)
                else Result.Error(
                    extractErrorMessage(body, "Profile delete failed"),
                    response.code,
                    response.code in 500..599 || response.code == 429
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile delete exception baseUrl=$normalizedBaseUrl type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Profile delete failed", e), retryable = true)
        }
    }

    private suspend fun sendJson(
        method: String,
        path: String,
        accessToken: String,
        payload: Any
    ): Result<ProfileResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url(path)
            val body = gson.toJson(payload)
            Log.d(TAG, "$method $endpoint payloadSize=${body.length}")
            val requestBody = body.toRequestBody("application/json".toMediaType())
            val reqBuilder = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $accessToken")

            val req = when (method) {
                "POST" -> reqBuilder.post(requestBody).build()
                "PUT" -> reqBuilder.put(requestBody).build()
                else -> reqBuilder.method(method, requestBody).build()
            }

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = gson.fromJson(responseBody, ProfileResponse::class.java)
                    if (parsed == null) Result.Error("Invalid profile response", response.code)
                    else Result.Success(parsed)
                } else {
                    Result.Error(
                        extractErrorMessage(responseBody, "Profile request failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile $method exception baseUrl=$normalizedBaseUrl type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Profile request failed", e), retryable = true)
        }
    }

    private fun url(path: String): String = "${normalizedBaseUrl.trimEnd('/')}$path"

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

    private fun composeExceptionMessage(fallback: String, e: Exception): String {
        val detail = e.message?.takeIf { it.isNotBlank() }
            ?: e.cause?.message?.takeIf { it.isNotBlank() }
            ?: "${e.javaClass.simpleName}"
        return when (e) {
            is IOException -> "$fallback (network): $detail"
            else -> "$fallback: $detail"
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }
}
