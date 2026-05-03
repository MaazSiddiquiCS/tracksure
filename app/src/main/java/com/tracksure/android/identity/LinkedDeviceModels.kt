package com.tracksure.android.identity

import com.google.gson.annotations.SerializedName

enum class DeviceLinkSyncState {
    PENDING,
    SYNCED,
    FAILED
}

data class LinkedTrackingDevice(
    @SerializedName("peerId") val peerId: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("deviceName") val deviceName: String? = null,
    @SerializedName("backendLinkId") val backendLinkId: Long? = null,
    @SerializedName("isActive") val isActive: Boolean = false,
    @SerializedName("syncState") val syncState: DeviceLinkSyncState = DeviceLinkSyncState.PENDING,
    @SerializedName("linkedAtEpochMs") val linkedAtEpochMs: Long = System.currentTimeMillis(),
    @SerializedName("lastSyncedAtEpochMs") val lastSyncedAtEpochMs: Long? = null,
    @SerializedName("lastSyncError") val lastSyncError: String? = null
) {
    fun title(): String {
        val normalizedName = displayName.trim()
        if (normalizedName.isNotBlank()) return normalizedName

        val fallback = deviceName?.trim().orEmpty()
        if (fallback.isNotBlank()) return fallback

        return peerId
    }
}