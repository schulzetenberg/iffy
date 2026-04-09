package com.iffy.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "iffy_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    var clientId: String
        get() {
            val stored = prefs.getString(KEY_CLIENT_ID, null)
            if (!stored.isNullOrEmpty()) return stored
            val fromBuild = BuildConfig.SPOTIFY_CLIENT_ID
            if (fromBuild.isNotEmpty()) return fromBuild
            return ""
        }
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && !refreshToken.isNullOrEmpty()

    val isTokenExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt

    fun saveTokens(response: TokenResponse) {
        accessToken = response.accessToken
        if (response.refreshToken != null) {
            refreshToken = response.refreshToken
        }
        // Expire 1 minute early to avoid edge-case failures
        expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L) - 60_000
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_CLIENT_ID = "client_id"
    }
}
