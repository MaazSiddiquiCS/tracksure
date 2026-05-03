package com.tracksure.android.identity

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tracksure.android.config.ApiConfig
import com.tracksure.android.net.DeviceLinkApiClient
import com.tracksure.android.ui.TrackingShareInvite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceLinkRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val apiClient = DeviceLinkApiClient(ApiConfig.baseUrl(appContext))
    private val workManager = WorkManager.getInstance(appContext)
    private val prefs: SharedPreferences

    private val linkedDevicesByPeerId = linkedMapOf<String, LinkedTrackingDevice>()
    private val _linkedDevices = MutableStateFlow<List<LinkedTrackingDevice>>(emptyList())

    val linkedDevices: StateFlow<List<LinkedTrackingDevice>> = _linkedDevices.asStateFlow()

    init {
        val masterKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        loadFromStorage()
    }

    fun snapshotLinkedDevices(): List<LinkedTrackingDevice> = _linkedDevices.value

    fun registerVerifiedInvite(invite: TrackingShareInvite): LinkedTrackingDevice {
        val record = LinkedTrackingDevice(
            peerId = invite.ownerPeerId.trim().lowercase(),
            displayName = invite.ownerNickname.trim().ifBlank { invite.ownerPeerId.trim() },
            deviceName = invite.ownerNickname.trim().ifBlank { null },
            isActive = true,
            syncState = DeviceLinkSyncState.PENDING,
            linkedAtEpochMs = System.currentTimeMillis()
        )
        upsert(record)
        enqueueBackgroundSync()
        return record
    }

    fun startTracking(peerId: String): LinkedTrackingDevice? {
        return updateDevice(peerId) { it.copy(isActive = true) }
    }

    fun stopTracking(peerId: String): LinkedTrackingDevice? {
        return updateDevice(peerId) { it.copy(isActive = false) }
    }

    fun removeLinkedDevice(peerId: String, backendLinkId: Long? = null) {
        val normalizedPeerId = normalizePeerId(peerId)
        val keysToRemove = linkedDevicesByPeerId
            .filter { (_, device) ->
                device.peerId == normalizedPeerId
                    || (backendLinkId != null && device.backendLinkId == backendLinkId)
            }
            .keys

        if (keysToRemove.isEmpty()) {
            linkedDevicesByPeerId.remove(normalizedPeerId)
        } else {
            keysToRemove.forEach { key -> linkedDevicesByPeerId.remove(key) }
        }

        persistAndPublish()
    }

    suspend fun refreshFromBackend(accessToken: String): DeviceLinkApiClient.Result<Unit> = withContext(Dispatchers.IO) {
        when (val result = apiClient.getTrackedDevices(accessToken)) {
            is DeviceLinkApiClient.Result.Success -> {
                mergeRemoteDevices(result.value)
                DeviceLinkApiClient.Result.Success(Unit)
            }

            is DeviceLinkApiClient.Result.Error -> result
        }
    }

    suspend fun syncPendingLinks(accessToken: String): DeviceLinkApiClient.Result<Unit> = withContext(Dispatchers.IO) {
        val pending = snapshotLinkedDevices().filter { it.syncState != DeviceLinkSyncState.SYNCED }
        if (pending.isEmpty()) {
            return@withContext DeviceLinkApiClient.Result.Success(Unit)
        }

        for (device in pending) {
            val request = DeviceLinkApiClient.DeviceLinkCreateRequest(
                peerId = device.peerId,
                deviceName = device.deviceName ?: device.displayName,
                permissionType = "TRACK"
            )

            when (val result = apiClient.createLink(accessToken, request)) {
                is DeviceLinkApiClient.Result.Success -> {
                    val response = result.value
                    updateDevice(device.peerId) {
                        it.copy(
                            // DELETE endpoint requires device-link id, not device id.
                            backendLinkId = response.linkId ?: it.backendLinkId,
                            displayName = response.deviceName?.trim().orEmpty().ifBlank { it.displayName },
                            deviceName = response.deviceName?.trim()?.ifBlank { null } ?: it.deviceName,
                            syncState = DeviceLinkSyncState.SYNCED,
                            lastSyncedAtEpochMs = System.currentTimeMillis(),
                            lastSyncError = null
                        )
                    }
                }

                is DeviceLinkApiClient.Result.Error -> {
                    updateDevice(device.peerId) {
                        it.copy(
                            syncState = DeviceLinkSyncState.FAILED,
                            lastSyncError = result.message
                        )
                    }
                    return@withContext result
                }
            }
        }

        enqueueBackgroundSync()
        DeviceLinkApiClient.Result.Success(Unit)
    }

    suspend fun syncWithBackend(accessToken: String): DeviceLinkApiClient.Result<Unit> {
        when (val refresh = refreshFromBackend(accessToken)) {
            is DeviceLinkApiClient.Result.Success -> Unit
            is DeviceLinkApiClient.Result.Error -> return refresh
        }

        return syncPendingLinks(accessToken)
    }

    suspend fun deleteLink(accessToken: String, linkId: Long): DeviceLinkApiClient.Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext apiClient.deleteLink(accessToken, linkId)
    }

    suspend fun resolveBackendLinkIdByPeer(accessToken: String, peerId: String): DeviceLinkApiClient.Result<Long?> = withContext(Dispatchers.IO) {
        val normalizedPeerId = normalizePeerId(peerId)
        when (val result = apiClient.getTrackedDevices(accessToken)) {
            is DeviceLinkApiClient.Result.Success -> {
                val resolved = result.value.firstOrNull {
                    normalizePeerId(it.peerId.orEmpty()) == normalizedPeerId
                }?.linkId
                DeviceLinkApiClient.Result.Success(resolved)
            }

            is DeviceLinkApiClient.Result.Error -> result
        }
    }

    fun enqueueBackgroundSync() {
        val request = OneTimeWorkRequestBuilder<DeviceLinkSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun mergeRemoteDevices(remoteDevices: List<DeviceLinkApiClient.TrackedDeviceLinkResponse>) {
        remoteDevices.forEach { response ->
            val matchedLocalPeerId = linkedDevicesByPeerId.values.firstOrNull { local ->
                local.backendLinkId != null && (local.backendLinkId == response.linkId || local.backendLinkId == response.deviceId)
            }?.peerId

            // Ignore backend-only numeric IDs when no stable peerId exists yet.
            if (response.peerId.isNullOrBlank() && matchedLocalPeerId == null) {
                return@forEach
            }

            val peerId = normalizePeerId(
                response.peerId ?: matchedLocalPeerId ?: response.deviceId?.toString().orEmpty()
            )
            if (peerId.isBlank()) return@forEach

            val local = linkedDevicesByPeerId[peerId]
            val displayName = response.deviceName?.trim().takeUnless { it.isNullOrBlank() }
                ?: response.ownerUsername?.trim().takeUnless { it.isNullOrBlank() }
                ?: local?.displayName
                ?: peerId

            linkedDevicesByPeerId[peerId] = (local ?: LinkedTrackingDevice(
                peerId = peerId,
                displayName = displayName,
                deviceName = response.deviceName?.trim().takeUnless { it.isNullOrBlank() },
                linkedAtEpochMs = System.currentTimeMillis()
            )).copy(
                displayName = displayName,
                deviceName = response.deviceName?.trim().takeUnless { it.isNullOrBlank() } ?: local?.deviceName,
                backendLinkId = response.linkId ?: local?.backendLinkId,
                syncState = DeviceLinkSyncState.SYNCED,
                lastSyncedAtEpochMs = System.currentTimeMillis(),
                lastSyncError = null,
                isActive = local?.isActive ?: false
            )
        }

        persistAndPublish()
    }

    private fun updateDevice(peerId: String, transform: (LinkedTrackingDevice) -> LinkedTrackingDevice): LinkedTrackingDevice? {
        val normalizedPeerId = normalizePeerId(peerId)
        val current = linkedDevicesByPeerId[normalizedPeerId] ?: return null
        val updated = transform(current).copy(peerId = normalizedPeerId)
        linkedDevicesByPeerId[normalizedPeerId] = updated
        persistAndPublish()
        return updated
    }

    private fun upsert(record: LinkedTrackingDevice) {
        val normalizedPeerId = normalizePeerId(record.peerId)
        linkedDevicesByPeerId[normalizedPeerId] = record.copy(peerId = normalizedPeerId)
        persistAndPublish()
    }

    private fun persistAndPublish() {
        val deduped = linkedDevicesByPeerId.values
            .groupBy { device -> device.backendLinkId?.let { "id:$it" } ?: "peer:${device.peerId}" }
            .values
            .map { duplicates ->
                duplicates.sortedWith(
                    compareBy<LinkedTrackingDevice>(
                        { it.peerId.all(Char::isDigit) },
                        { it.displayName.trim().isBlank() }
                    ).thenByDescending { it.isActive }
                        .thenByDescending { it.lastSyncedAtEpochMs ?: 0L }
                        .thenByDescending { it.linkedAtEpochMs }
                ).first()
            }

        linkedDevicesByPeerId.clear()
        deduped.forEach { device ->
            linkedDevicesByPeerId[normalizePeerId(device.peerId)] = device.copy(peerId = normalizePeerId(device.peerId))
        }

        val ordered = linkedDevicesByPeerId.values
            .sortedWith(compareByDescending<LinkedTrackingDevice> { it.isActive }.thenByDescending { it.linkedAtEpochMs })

        prefs.edit()
            .putString(PREFS_LINKED_DEVICES, gson.toJson(ordered))
            .apply()

        _linkedDevices.value = ordered
    }

    private fun loadFromStorage() {
        val payload = prefs.getString(PREFS_LINKED_DEVICES, null).orEmpty()
        if (payload.isBlank()) {
            _linkedDevices.value = emptyList()
            return
        }

        try {
            val type = object : TypeToken<List<LinkedTrackingDevice>>() {}.type
            val stored = gson.fromJson<List<LinkedTrackingDevice>>(payload, type).orEmpty()
            linkedDevicesByPeerId.clear()
            stored.forEach { device ->
                linkedDevicesByPeerId[normalizePeerId(device.peerId)] = device.copy(peerId = normalizePeerId(device.peerId))
            }
            persistAndPublish()
        } catch (_: Exception) {
            linkedDevicesByPeerId.clear()
            _linkedDevices.value = emptyList()
        }
    }

    private fun normalizePeerId(value: String): String {
        return value.trim().lowercase()
    }

    companion object {
        private const val PREFS_NAME = "tracksure_linked_devices"
        private const val PREFS_LINKED_DEVICES = "linked_devices_json"
        private const val WORK_NAME = "device_link_sync"
    }
}