package com.iffy.android

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom

object SpotifyAuth {

    private const val AUTH_URL = "https://accounts.spotify.com/authorize"
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    const val REDIRECT_URI = "iffy://callback"

    private val SCOPES = listOf(
        "user-read-currently-playing",
        "user-read-playback-state",
        "user-modify-playback-state",
        "playlist-read-private",
        "playlist-modify-private",
        "playlist-modify-public"
    )

    private var pendingCodeVerifier: String? = null
    private var pendingState: String? = null

    fun startAuth(context: Context, clientId: String) {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()

        pendingCodeVerifier = codeVerifier
        pendingState = state

        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }

    /** Returns (codeVerifier, expectedState) if a pending auth exists, then clears it. */
    fun consumePendingAuth(): Pair<String, String>? {
        val verifier = pendingCodeVerifier ?: return null
        val state = pendingState ?: return null
        pendingCodeVerifier = null
        pendingState = null
        return Pair(verifier, state)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateState(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
