package com.iffy.android

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tokenManager: TokenManager,
    spotifyApi: SpotifyApi,
    isLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("iffy_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf(tokenManager.clientId) }
    var skipOffset by remember { mutableIntStateOf(prefs.getInt("skip_offset_seconds", 30)) }
    var defaultPlaylistId by remember {
        mutableStateOf(prefs.getString("default_playlist_id", "") ?: "")
    }
    var defaultPlaylistName by remember {
        mutableStateOf(prefs.getString("default_playlist_name", "") ?: "")
    }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val playbackState by SpotifyService.playbackState.collectAsState()
    val serviceRunning by SpotifyService.isRunning.collectAsState()
    val lastAction by SpotifyService.lastAction.collectAsState()

    // Fetch playlists on login
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                val all = mutableListOf<PlaylistItem>()
                var offset = 0
                while (true) {
                    val page = spotifyApi.getUserPlaylists(offset = offset)
                    all.addAll(page.items)
                    offset += page.items.size
                    if (all.size >= page.total || page.items.isEmpty()) break
                }
                playlists = all
            } catch (e: Exception) {
                statusMessage = "Failed to load playlists"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iffy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Now Playing card
            item {
                Spacer(Modifier.height(4.dp))
                NowPlayingCard(playbackState, serviceRunning)
            }

            // Last action feedback
            if (lastAction != null) {
                item {
                    Text(
                        text = lastAction!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error message
            if (statusMessage != null) {
                item {
                    Text(
                        text = statusMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!isLoggedIn) {
                item {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = {
                            clientId = it
                            tokenManager.clientId = it
                        },
                        label = { Text("Spotify Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = clientId.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Spotify")
                    }
                }
            } else {
                // Action buttons (same as notification, but in-app)
                item {
                    ActionButtonsRow(
                        context = context,
                        playbackState = playbackState,
                        defaultPlaylistName = defaultPlaylistName
                    )
                }

                // --- Settings ---
                item {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Favorites playlist picker
                item {
                    OutlinedCard(
                        onClick = { showPlaylistPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Favorites Playlist",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = defaultPlaylistName.ifEmpty { "None selected" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }

                // Skip offset
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Skip offset after remove")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (skipOffset > 0) {
                                        skipOffset = (skipOffset - 5).coerceAtLeast(0)
                                        prefs.edit().putInt("skip_offset_seconds", skipOffset)
                                            .apply()
                                    }
                                }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }
                                Text("${skipOffset}s")
                                IconButton(onClick = {
                                    skipOffset += 5
                                    prefs.edit().putInt("skip_offset_seconds", skipOffset).apply()
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                            }
                        }
                    }
                }

                // Notification service toggle
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Notification Controls")
                            Switch(
                                checked = serviceRunning,
                                onCheckedChange = { enabled ->
                                    if (enabled) onStartService() else onStopService()
                                }
                            )
                        }
                    }
                }

                // Sign out
                item {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showPlaylistPicker) {
            AlertDialog(
                onDismissRequest = { showPlaylistPicker = false },
                title = { Text("Choose Favorites Playlist") },
                text = {
                    LazyColumn {
                        items(playlists) { playlist ->
                            TextButton(
                                onClick = {
                                    defaultPlaylistId = playlist.id
                                    defaultPlaylistName = playlist.name
                                    prefs.edit()
                                        .putString("default_playlist_id", playlist.id)
                                        .putString("default_playlist_name", playlist.name)
                                        .apply()
                                    showPlaylistPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    playlist.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaylistPicker = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun NowPlayingCard(playbackState: PlaybackState?, serviceRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val track = playbackState?.item
            if (track != null) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists?.joinToString(", ") { it.name } ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playbackState.context?.type == "playlist") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Playing from playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (serviceRunning) {
                Text(
                    text = "Waiting for playback...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Service not running",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    context: Context,
    playbackState: PlaybackState?,
    defaultPlaylistName: String
) {
    val hasTrack = playbackState?.item != null
    val isFromPlaylist = playbackState?.context?.type == "playlist"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = {
                context.startService(
                    Intent(context, SpotifyService::class.java).apply {
                        action = SpotifyService.ACTION_REMOVE_SKIP
                    }
                )
            },
            enabled = hasTrack && isFromPlaylist,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Remove", maxLines = 1)
        }

        FilledTonalButton(
            onClick = {
                context.startService(
                    Intent(context, SpotifyService::class.java).apply {
                        action = SpotifyService.ACTION_FAVORITE
                    }
                )
            },
            enabled = hasTrack && defaultPlaylistName.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Fav", maxLines = 1)
        }

        FilledTonalButton(
            onClick = {
                context.startService(
                    Intent(context, SpotifyService::class.java).apply {
                        action = SpotifyService.ACTION_SKIP
                    }
                )
            },
            enabled = hasTrack,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Skip", maxLines = 1)
        }
    }
}
