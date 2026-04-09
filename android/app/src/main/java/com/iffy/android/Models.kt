package com.iffy.android

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class PlaybackState(
    val item: TrackItem? = null,
    val context: PlaybackContext? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long? = null
)

@Serializable
data class TrackItem(
    val name: String,
    val uri: String,
    val artists: List<Artist>? = null,
    val album: Album? = null
)

@Serializable
data class Artist(val name: String)

@Serializable
data class Album(
    val name: String,
    val images: List<AlbumImage>? = null
)

@Serializable
data class AlbumImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class PlaybackContext(
    val type: String? = null,
    val uri: String? = null
)

@Serializable
data class PlaylistPage(
    val items: List<PlaylistItem>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

@Serializable
data class PlaylistItem(
    val id: String,
    val name: String,
    val uri: String
)

@Serializable
data class RemoveTracksBody(val tracks: List<TrackUri>)

@Serializable
data class TrackUri(val uri: String)

@Serializable
data class AddTracksBody(val uris: List<String>)
