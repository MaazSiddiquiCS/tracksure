package com.tracksure.android.net

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class AuthApiClient(
    private val baseUrl: String,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "AuthApiClient"
    }

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Error(val message: String, val code: Int? = null, val retryable: Boolean = false) : Result<Nothing>()
    }

    data class SignupRequest(
        val username: String,
        val email: String,
        val password: String,
        val confirmPassword: String
    )

    data class LoginRequest(
        val username: String,
        val password: String
    )

    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val userId: Long,
        val username: String,
        val email: String
    )

    private data class ApiErrorResponse(
        val message: String? = null,
        val detail: String? = null
    )

    private val normalizedBaseUrl: String = normalizeBaseUrl(baseUrl)

    init {
        Log.i(TAG, "Initialized baseUrl='$normalizedBaseUrl' (raw='${baseUrl.trim()}')")
    }

    suspend fun signup(request: SignupRequest): Result<LoginResponse> {
        return postJson("/api/auth/register", request)
    }

    suspend fun login(request: LoginRequest): Result<LoginResponse> {
        return postJson("/api/auth/login", request)
    }

    suspend fun refresh(refreshToken: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/auth/refresh")
            Log.d(TAG, "POST $endpoint (refresh)")
            val req = Request.Builder()
                .url(endpoint)
                .header("X-Refresh-Token", refreshToken)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Log.i(TAG, "Refresh success code=${response.code}")
                    val parsed = gson.fromJson(body, LoginResponse::class.java)
                    if (parsed == null) Result.Error("Invalid refresh response", response.code)
                    else Result.Success(parsed)
                } else {
                    Log.w(TAG, "Refresh failed code=${response.code} body=${body.take(300)}")
                    Result.Error(
                        extractErrorMessage(body, "Refresh failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh exception baseUrl=$normalizedBaseUrl type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Refresh failed", e), retryable = true)
        }
    }

    suspend fun logout(refreshToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url("/api/auth/logout")
            Log.d(TAG, "POST $endpoint (logout)")
            val req = Request.Builder()
                .url(endpoint)
                .header("X-Refresh-Token", refreshToken)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                if (response.isSuccessful) Result.Success(Unit)
                else {
                    val body = response.body?.string().orEmpty()
                    Log.w(TAG, "Logout failed code=${response.code} body=${body.take(300)}")
                    Result.Error(extractErrorMessage(body, "Logout failed"), response.code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception baseUrl=$normalizedBaseUrl type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Logout failed", e), retryable = true)
        }
    }

    private suspend fun postJson(path: String, payload: Any): Result<LoginResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = url(path)
            val body = gson.toJson(payload)
            Log.d(TAG, "POST $endpoint payloadSize=${body.length}")
            val req = Request.Builder()
                .url(endpoint)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(endpoint).newCall(req).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Log.i(TAG, "Auth success endpoint=$endpoint code=${response.code}")
                    val parsed = gson.fromJson(responseBody, LoginResponse::class.java)
                    if (parsed == null) Result.Error("Invalid auth response", response.code)
                    else Result.Success(parsed)
                } else {
                    Log.w(TAG, "Auth failed endpoint=$endpoint code=${response.code} body=${responseBody.take(300)}")
                    Result.Error(
                        extractErrorMessage(responseBody, "Authentication failed"),
                        response.code,
                        response.code in 500..599 || response.code == 429
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth exception endpoint=${normalizeBaseUrl(baseUrl)}$path type=${e.javaClass.simpleName} msg=${e.message}", e)
            Result.Error(composeExceptionMessage("Authentication failed", e), retryable = true)
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
