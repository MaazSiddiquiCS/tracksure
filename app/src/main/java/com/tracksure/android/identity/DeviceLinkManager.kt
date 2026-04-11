package com.tracksure.android.identity

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.tracksure.android.bridgeupload.BridgeUploadRuntime
import com.tracksure.android.mesh.BluetoothMeshService
import com.tracksure.android.model.BackendDeviceIdentity
import com.tracksure.android.net.DeviceLinkApiClient

class DeviceLinkManager(
    private val context: Context,
    private val identityStore: BackendDeviceIdentityStore,
    private val deviceLinkApiClient: DeviceLinkApiClient
) {
    companion object {
        private const val TAG = "DeviceLinkManager"
    }

    sealed class LinkStatus {
        data class Linked(val identity: BackendDeviceIdentity) : LinkStatus()
        data class Failed(val message: String, val retryable: Boolean) : LinkStatus()
    }

    suspend fun ensureLinked(accessToken: String): LinkStatus {
        val existing = identityStore.load()
        if (existing != null) {
            Log.d(TAG, "Using persisted identity backendDeviceId=${existing.backendDeviceId} meshPeerId=${existing.meshPeerId}")
            return LinkStatus.Linked(existing)
        }

        val meshPeerId = resolveMyMeshPeerId() ?: return LinkStatus.Failed(
            message = "Mesh peer identity unavailable",
            retryable = true
        )

        Log.d(TAG, "Linking device with peerId=$meshPeerId")

        return when (val result = deviceLinkApiClient.linkDevice(accessToken, meshPeerId, deviceName = "tracksure-android")) {
            is DeviceLinkApiClient.Result.Success -> {
                val identity = BackendDeviceIdentity(
                    backendDeviceId = result.value.deviceId,
                    meshPeerId = meshPeerId
                )
                identityStore.save(identity)
                BridgeUploadRuntime.restartWithLatestIdentity(context.applicationContext)
                Log.i(TAG, "Linked backendDeviceId=${identity.backendDeviceId} meshPeerId=${identity.meshPeerId}")
                LinkStatus.Linked(identity)
            }

            is DeviceLinkApiClient.Result.Error -> {
                Log.w(TAG, "Device link failed code=${result.code} msg=${result.message}")
                LinkStatus.Failed(result.message, result.retryable)
            }
        }
    }

    private fun resolveMyMeshPeerId(): String? {
        val fromMesh = try {
            BluetoothMeshService.getInstance(context.applicationContext).myPeerID.trim().lowercase()
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve mesh peer id from mesh service: ${e.message}")
            null
        }
        if (fromMesh != null) return fromMesh

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                ?.lowercase()
                ?.filter { it.isLetterOrDigit() }
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve ANDROID_ID fallback: ${e.message}")
            null
        }

        val fallback = androidId?.padEnd(16, '0')?.take(16)
        if (fallback != null) {
            Log.i(TAG, "Using ANDROID_ID fallback peerId=$fallback")
        }
        return fallback
    }
}
