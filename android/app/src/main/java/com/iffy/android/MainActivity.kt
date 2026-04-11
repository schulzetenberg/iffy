package com.iffy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var spotifyApi: SpotifyApi
    private val isLoggedIn = mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && tokenManager.isLoggedIn) {
            startSpotifyService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        spotifyApi = SpotifyApi(tokenManager)
        isLoggedIn.value = tokenManager.isLoggedIn

        handleOAuthCallback(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(
                    tokenManager = tokenManager,
                    spotifyApi = spotifyApi,
                    isLoggedIn = isLoggedIn.value,
                    onSignIn = ::signIn,
                    onSignOut = ::signOut,
                    onStartService = ::startSpotifyService,
                    onStopService = ::stopSpotifyService
                )
            }
        }

        if (tokenManager.isLoggedIn) {
            requestNotificationPermissionAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (tokenManager.isLoggedIn && SpotifyService.isRunning.value) {
            refreshSpotifyState()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "iffy" || uri.host != "callback") return

        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state")
        val (codeVerifier, expectedState) = SpotifyAuth.consumePendingAuth() ?: return

        if (state != expectedState) return

        lifecycleScope.launch {
            try {
                spotifyApi.exchangeCodeForTokens(code, codeVerifier)
                isLoggedIn.value = true
                requestNotificationPermissionAndStart()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun signIn() {
        val clientId = tokenManager.clientId
        if (clientId.isEmpty()) return
        SpotifyAuth.startAuth(this, clientId)
    }

    private fun signOut() {
        stopSpotifyService()
        tokenManager.clear()
        isLoggedIn.value = false
    }

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startSpotifyService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startSpotifyService()
        }
    }

    private fun startSpotifyService() {
        val intent = Intent(this, SpotifyService::class.java).apply {
            action = SpotifyService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSpotifyService() {
        val intent = Intent(this, SpotifyService::class.java).apply {
            action = SpotifyService.ACTION_STOP
        }
        startService(intent)
    }

    private fun refreshSpotifyState() {
        val intent = Intent(this, SpotifyService::class.java).apply {
            action = SpotifyService.ACTION_REFRESH
        }
        startService(intent)
    }
}
