package com.iffy.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpotifyService : Service() {

    companion object {
        const val ACTION_START = "com.iffy.android.action.START"
        const val ACTION_STOP = "com.iffy.android.action.STOP"
        const val ACTION_REMOVE_SKIP = "com.iffy.android.action.REMOVE_SKIP"
        const val ACTION_FAVORITE = "com.iffy.android.action.FAVORITE"
        const val ACTION_SKIP = "com.iffy.android.action.SKIP"

        const val CHANNEL_ID = "iffy_playback"
        const val NOTIFICATION_ID = 1

        private val _playbackState = MutableStateFlow<PlaybackState?>(null)
        val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastAction = MutableStateFlow<String?>(null)
        val lastAction: StateFlow<String?> = _lastAction.asStateFlow()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private lateinit var api: SpotifyApi

    override fun onCreate() {
        super.onCreate()
        api = SpotifyApi(TokenManager(this))
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification(null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startPolling()
            }
            ACTION_STOP -> {
                stopPolling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_REMOVE_SKIP -> scope.launch { handleRemoveSkip() }
            ACTION_FAVORITE -> scope.launch { handleFavorite() }
            ACTION_SKIP -> scope.launch { handleSkip() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        _isRunning.value = false
        _playbackState.value = null
        _lastAction.value = null
    }

    // --- Polling ---

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                fetchNowPlaying()
                delay(5_000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
    }

    private suspend fun fetchNowPlaying() {
        try {
            val state = api.getPlayerState()
            _playbackState.value = state
            updateNotification(state)
        } catch (e: Exception) {
            Log.e("SpotifyService", "Failed to fetch now playing", e)
        }
    }

    // --- Action handlers ---

    private suspend fun handleRemoveSkip() {
        val state = _playbackState.value ?: return
        val trackUri = state.item?.uri ?: return
        val playlistUri = state.context?.uri ?: return
        if (state.context.type != "playlist") return

        val playlistId = playlistUri.removePrefix("spotify:playlist:")

        try {
            _lastAction.value = "Removing..."
            api.removeTracksFromPlaylist(playlistId, listOf(trackUri))
            api.skipToNext()
            delay(300)

            val prefs = getSharedPreferences("iffy_prefs", Context.MODE_PRIVATE)
            val offsetSeconds = prefs.getInt("skip_offset_seconds", 30)
            api.seekToPosition(offsetSeconds * 1000)

            _lastAction.value = "Removed & skipped"
            delay(300)
            fetchNowPlaying()
            delay(2_000)
            _lastAction.value = null
        } catch (e: Exception) {
            Log.e("SpotifyService", "Remove+Skip failed", e)
            _lastAction.value = "Failed: ${e.message}"
            delay(3_000)
            _lastAction.value = null
        }
    }

    private suspend fun handleFavorite() {
        val state = _playbackState.value ?: return
        val trackUri = state.item?.uri ?: return

        val prefs = getSharedPreferences("iffy_prefs", Context.MODE_PRIVATE)
        val defaultPlaylistId = prefs.getString("default_playlist_id", "") ?: ""
        if (defaultPlaylistId.isEmpty()) {
            _lastAction.value = "No favorites playlist set"
            delay(2_000)
            _lastAction.value = null
            return
        }

        try {
            _lastAction.value = "Adding to favorites..."
            api.addTracksToPlaylist(defaultPlaylistId, listOf(trackUri))

            val currentPlaylistUri = state.context?.uri
            if (currentPlaylistUri != null && state.context?.type == "playlist") {
                val currentPlaylistId = currentPlaylistUri.removePrefix("spotify:playlist:")
                api.removeTracksFromPlaylist(currentPlaylistId, listOf(trackUri))
                api.skipToNext()
            }

            _lastAction.value = "Added to favorites"
            delay(500)
            fetchNowPlaying()
            delay(2_000)
            _lastAction.value = null
        } catch (e: Exception) {
            Log.e("SpotifyService", "Favorite failed", e)
            _lastAction.value = "Failed: ${e.message}"
            delay(3_000)
            _lastAction.value = null
        }
    }

    private suspend fun handleSkip() {
        try {
            _lastAction.value = "Skipping..."
            api.skipToNext()
            delay(500)
            fetchNowPlaying()
            _lastAction.value = null
        } catch (e: Exception) {
            Log.e("SpotifyService", "Skip failed", e)
            _lastAction.value = "Failed: ${e.message}"
            delay(3_000)
            _lastAction.value = null
        }
    }

    // --- Notification ---

    private fun updateNotification(state: PlaybackState?) {
        val notification = createNotification(state)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(state: PlaybackState?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val track = state?.item
        if (track != null) {
            val artistText = track.artists?.joinToString(", ") { it.name } ?: ""
            builder.setContentTitle(track.name)
            builder.setContentText(artistText)

            val isFromPlaylist = state.context?.type == "playlist"
            if (isFromPlaylist) {
                builder.setSubText("Playing from playlist")
            }

            var actionIndex = 0
            if (isFromPlaylist) {
                builder.addAction(buildAction(ACTION_REMOVE_SKIP, "Remove+Skip", R.drawable.ic_remove, 1))
                actionIndex++
            }
            builder.addAction(buildAction(ACTION_FAVORITE, "Favorite", R.drawable.ic_favorite, 2))
            actionIndex++
            builder.addAction(buildAction(ACTION_SKIP, "Skip", R.drawable.ic_skip_next, 3))
            actionIndex++

            val compactView = if (isFromPlaylist) intArrayOf(0, 1, 2) else intArrayOf(0, 1)
            builder.setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(*compactView)
            )
        } else {
            builder.setContentTitle("Iffy")
            builder.setContentText("Waiting for playback...")
        }

        return builder.build()
    }

    private fun buildAction(
        action: String,
        title: String,
        icon: Int,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(this, SpotifyService::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }
}
