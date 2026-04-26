package com.tracksure.android.identity

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
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
        val meshPeerId = resolveMyMeshPeerId() ?: existing?.meshPeerId ?: return LinkStatus.Failed(
            message = "Mesh peer identity unavailable",
            retryable = true
        )

        if (existing != null) {
            Log.d(TAG, "Refreshing linked device backendDeviceId=${existing.backendDeviceId} peerId=$meshPeerId")
        } else {
            Log.d(TAG, "Linking device with peerId=$meshPeerId")
        }
        val publishedDeviceName = resolvePublishedDeviceName()

        return when (val result = deviceLinkApiClient.linkDevice(accessToken, meshPeerId, deviceName = publishedDeviceName)) {
            is DeviceLinkApiClient.Result.Success -> {
                val identity = BackendDeviceIdentity(
                    backendDeviceId = result.value.deviceId,
                    meshPeerId = result.value.peerId.trim().lowercase().ifBlank { meshPeerId }
                )
                identityStore.save(identity)
                BridgeUploadRuntime.restartWithLatestIdentity(context.applicationContext)
                Log.i(TAG, "Linked backendDeviceId=${identity.backendDeviceId} meshPeerId=${identity.meshPeerId}")
                LinkStatus.Linked(identity)
            }

            is DeviceLinkApiClient.Result.Error -> {
                Log.w(TAG, "Device link failed code=${result.code} msg=${result.message}")
                if (existing != null) {
                    // Keep user signed in with cached identity if refresh-upsert fails temporarily.
                    Log.w(TAG, "Using cached linked device identity after refresh failure")
                    LinkStatus.Linked(existing)
                } else {
                    LinkStatus.Failed(result.message, result.retryable)
                }
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

    private fun resolvePublishedDeviceName(): String {
        val bluetoothName = try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.name
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        val systemDeviceName = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (_: Exception) {
            null
        }

        val modelFallback = listOfNotNull(
            Build.MANUFACTURER?.trim()?.takeIf { it.isNotBlank() },
            Build.MODEL?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim().takeIf { it.isNotBlank() }

        return listOf(bluetoothName, systemDeviceName, modelFallback)
            .mapNotNull { it?.trim()?.takeIf { name -> name.isUsableDeviceNameForRegistration() } }
            .firstOrNull()
            ?: "android-device"
    }

    private fun String.isUsableDeviceNameForRegistration(): Boolean {
        if (isBlank()) return false

        val normalized = lowercase()
            .replace("_", "-")
            .replace(" ", "-")

        // Filter known legacy/debug placeholders so backend doesn't persist them.
        val blocked = setOf(
            "tracksure-android",
            "tracksureandroid",
            "android-device",
            "android",
            "unknown",
            "unknown-device"
        )

        return normalized !in blocked
    }
}
