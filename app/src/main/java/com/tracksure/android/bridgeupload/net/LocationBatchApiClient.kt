package com.tracksure.android.bridgeupload.net

import com.google.gson.Gson
import com.tracksure.android.bridgeupload.model.LocationBatchUploadRequest
import com.tracksure.android.bridgeupload.model.LocationBatchUploadResponse
import com.tracksure.android.net.OkHttpProvider
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Backend client for location batch uploads.
 */
class LocationBatchApiClient(
    private val endpointUrl: String,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "BridgeUpload"
    }

    sealed class UploadResult {
        data class Success(val response: LocationBatchUploadResponse) : UploadResult()
        data class RetryableError(val code: Int?, val message: String) : UploadResult()
        data class FatalError(val code: Int?, val message: String, val cause: Throwable? = null) : UploadResult()
    }

    fun uploadBatch(requestPayload: LocationBatchUploadRequest): UploadResult {
        return try {
            Log.d(
                TAG,
                "POST $endpointUrl subject=${requestPayload.subjectDeviceId} uploader=${requestPayload.uploaderDeviceId} points=${requestPayload.points.size}"
            )
            val requestJson = gson.toJson(requestPayload)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            OkHttpProvider.httpClientForUrl(endpointUrl).newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = gson.fromJson(responseBody, LocationBatchUploadResponse::class.java)
                    if (parsed != null) {
                        Log.i(
                            TAG,
                            "Upload success code=${response.code} inserted=${parsed.inserted} duplicates=${parsed.duplicates} total=${parsed.totalReceived}"
                        )
                        UploadResult.Success(parsed)
                    } else {
                        Log.e(TAG, "Upload success status but invalid response body")
                        UploadResult.FatalError(response.code, "Empty/invalid success response")
                    }
                } else {
                    Log.w(TAG, "Upload failed code=${response.code} body=${responseBody.take(500)}")
                    if (response.code in 500..599 || response.code == 429) {
                        UploadResult.RetryableError(response.code, responseBody.ifBlank { "Server temporary error" })
                    } else {
                        UploadResult.FatalError(response.code, responseBody.ifBlank { "Client/request rejected" })
                    }
                }
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Upload I/O error: ${e.message}")
            UploadResult.RetryableError(null, e.message ?: "I/O error")
        } catch (e: Exception) {
            Log.e(TAG, "Upload unexpected error: ${e.message}", e)
            UploadResult.FatalError(null, e.message ?: "Unexpected error", e)
        }
    }
}



