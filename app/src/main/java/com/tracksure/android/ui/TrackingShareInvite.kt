package com.tracksure.android.ui

import org.json.JSONObject
import java.security.SecureRandom

private const val TRACKING_SHARE_VERSION = 1
private const val TRACKING_INVITE_TTL_MS = 24 * 60 * 60 * 1000L
private val PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
private val random = SecureRandom()

data class TrackingShareInvite(
    val inviteId: String,
    val ownerPeerId: String,
    val ownerNickname: String,
    val password: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val oneTimeUse: Boolean = false
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean = nowEpochMs > expiresAtEpochMs

    fun toPayload(): String = JSONObject().apply {
        put("v", TRACKING_SHARE_VERSION)
        put("inviteId", inviteId)
        put("ownerPeerId", ownerPeerId)
        put("ownerNickname", ownerNickname)
        put("password", password)
        put("issuedAt", issuedAtEpochMs)
        put("expiresAt", expiresAtEpochMs)
        put("oneTimeUse", oneTimeUse)
    }.toString()

    companion object {
        fun generate(ownerPeerId: String, ownerNickname: String): TrackingShareInvite {
            val now = System.currentTimeMillis()
            return TrackingShareInvite(
                inviteId = randomString(12),
                ownerPeerId = ownerPeerId,
                ownerNickname = ownerNickname.ifBlank { "Unknown User" },
                password = randomString(8),
                issuedAtEpochMs = now,
                expiresAtEpochMs = now + TRACKING_INVITE_TTL_MS,
                oneTimeUse = false
            )
        }

        fun fromPayload(payload: String): TrackingShareInvite? {
            return try {
                val json = JSONObject(payload)
                if (json.optInt("v", 0) != TRACKING_SHARE_VERSION) return null
                TrackingShareInvite(
                    inviteId = json.getString("inviteId"),
                    ownerPeerId = json.getString("ownerPeerId"),
                    ownerNickname = json.optString("ownerNickname", "Unknown User"),
                    password = json.getString("password"),
                    issuedAtEpochMs = json.getLong("issuedAt"),
                    expiresAtEpochMs = json.getLong("expiresAt"),
                    oneTimeUse = json.optBoolean("oneTimeUse", false)
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun randomString(length: Int): String = buildString(length) {
            repeat(length) {
                append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.size)])
            }
        }
    }
}

