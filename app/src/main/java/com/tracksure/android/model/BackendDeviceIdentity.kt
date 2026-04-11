package com.tracksure.android.model

/**
 * Persisted mapping for this app install: mesh identity -> backend device identity.
 */
data class BackendDeviceIdentity(
    val backendDeviceId: Long,
    val meshPeerId: String
)
