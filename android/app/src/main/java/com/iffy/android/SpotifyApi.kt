package com.iffy.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SpotifyApi(private val tokenManager: TokenManager) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun ensureValidToken() {
        if (tokenManager.isTokenExpired && tokenManager.refreshToken != null) {
            refreshAccessToken()
        }
    }

    suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", SpotifyAuth.REDIRECT_URI)
                .add("client_id", tokenManager.clientId)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url(SpotifyAuth.TOKEN_URL)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from token exchange")

            if (!response.isSuccessful) {
                throw Exception("Token exchange failed (${response.code}): $responseBody")
            }

            json.decodeFromString<TokenResponse>(responseBody).also {
                tokenManager.saveTokens(it)
            }
        }

    private suspend fun refreshAccessToken() = withContext(Dispatchers.IO) {
        val refreshToken = tokenManager.refreshToken
            ?: throw Exception("No refresh token available")

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", tokenManager.clientId)
            .build()

        val request = Request.Builder()
            .url(SpotifyAuth.TOKEN_URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from token refresh")

        if (!response.isSuccessful) {
            throw Exception("Token refresh failed (${response.code}): $responseBody")
        }

        json.decodeFromString<TokenResponse>(responseBody).also {
            tokenManager.saveTokens(it)
        }
    }

    // --- Spotify Web API endpoints ---

    suspend fun getPlayerState(): PlaybackState? {
        val body = authorizedGet("$BASE_URL/me/player") ?: return null
        return json.decodeFromString<PlaybackState>(body)
    }

    suspend fun getUserPlaylists(limit: Int = 50, offset: Int = 0): PlaylistPage {
        val body = authorizedGet("$BASE_URL/me/playlists?limit=$limit&offset=$offset")
            ?: throw Exception("Empty playlist response")
        return json.decodeFromString<PlaylistPage>(body)
    }

    suspend fun removeTracksFromPlaylist(playlistId: String, trackUris: List<String>) {
        val payload = json.encodeToString(
            RemoveTracksBody.serializer(),
            RemoveTracksBody(tracks = trackUris.map { TrackUri(it) })
        )
        authorizedDelete("$BASE_URL/playlists/$playlistId/tracks", payload)
    }

    suspend fun addTracksToPlaylist(playlistId: String, trackUris: List<String>) {
        val payload = json.encodeToString(
            AddTracksBody.serializer(),
            AddTracksBody(uris = trackUris)
        )
        authorizedPost("$BASE_URL/playlists/$playlistId/tracks", payload)
    }

    suspend fun skipToNext() {
        authorizedPost("$BASE_URL/me/player/next")
    }

    suspend fun seekToPosition(positionMs: Int) {
        authorizedPut("$BASE_URL/me/player/seek?position_ms=$positionMs")
    }

    // --- HTTP helpers ---

    private suspend fun authorizedGet(url: String): String? {
        ensureValidToken()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${tokenManager.accessToken}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.code == 204) return@withContext null
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("GET $url failed (${response.code}): $body")
            }
            body
        }
    }

    private suspend fun authorizedPost(url: String, jsonBody: String? = null) {
        ensureValidToken()
        withContext(Dispatchers.IO) {
            val requestBody = jsonBody?.toRequestBody(JSON_MEDIA_TYPE)
                ?: "".toRequestBody(null)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${tokenManager.accessToken}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 204) {
                val body = response.body?.string()
                throw Exception("POST $url failed (${response.code}): $body")
            }
        }
    }

    private suspend fun authorizedPut(url: String, jsonBody: String? = null) {
        ensureValidToken()
        withContext(Dispatchers.IO) {
            val requestBody = jsonBody?.toRequestBody(JSON_MEDIA_TYPE)
                ?: "".toRequestBody(null)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${tokenManager.accessToken}")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 204) {
                val body = response.body?.string()
                throw Exception("PUT $url failed (${response.code}): $body")
            }
        }
    }

    private suspend fun authorizedDelete(url: String, jsonBody: String) {
        ensureValidToken()
        withContext(Dispatchers.IO) {
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${tokenManager.accessToken}")
                .delete(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 204) {
                val body = response.body?.string()
                throw Exception("DELETE $url failed (${response.code}): $body")
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.spotify.com/v1"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
