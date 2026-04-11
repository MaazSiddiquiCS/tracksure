package com.tracksure.android.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthTokenStore(context: Context) {
    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: Long,
        val username: String,
        val email: String
    )

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "tracksure_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun load(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getLong(KEY_USER_ID, -1L)
        if (userId <= 0L) return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            username = username,
            email = email
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
    }
}
