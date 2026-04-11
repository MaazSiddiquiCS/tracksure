package com.tracksure.android.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tracksure.android.model.BackendDeviceIdentity

class BackendDeviceIdentityStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "tracksure_backend_device",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(identity: BackendDeviceIdentity) {
        prefs.edit()
            .putLong(KEY_BACKEND_DEVICE_ID, identity.backendDeviceId)
            .putString(KEY_MESH_PEER_ID, identity.meshPeerId)
            .apply()
    }

    fun load(): BackendDeviceIdentity? {
        val id = prefs.getLong(KEY_BACKEND_DEVICE_ID, -1L)
        val peerId = prefs.getString(KEY_MESH_PEER_ID, null)?.trim()?.lowercase()
        if (id <= 0L || peerId.isNullOrBlank()) return null
        return BackendDeviceIdentity(backendDeviceId = id, meshPeerId = peerId)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BACKEND_DEVICE_ID = "backend_device_id"
        private const val KEY_MESH_PEER_ID = "mesh_peer_id"
    }
}
