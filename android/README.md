# Iffy for Android

A companion Android app for quick Spotify playlist management. Remove songs, move tracks between playlists, and skip—all from a persistent notification without ever opening the app.

## Features

- **Persistent Notification Controls**: Remove+Skip, Favorite, and Skip buttons right on the notification — no need to open the app
- **Remove + Skip**: Remove the current song from its playlist, skip to the next track, and seek past the intro
- **Add to Favorites**: One-tap add to your designated favorites playlist (removes from current and skips)
- **Skip**: Skip to the next track
- **Now Playing**: See the current track, artist, and whether it's playing from a playlist
- **Persistent Auth**: Sign in once — tokens stored securely with Android's EncryptedSharedPreferences
- **Dark Theme**: Full dark mode UI built with Material 3

## Requirements

- Android 8.0 (Oreo / API 26) or later
- Spotify Premium account (required for playback control)
- Android Studio (for building)

## Setup

### 1. Spotify Developer App

If you already have a Spotify Developer app from the macOS version of Iffy, you can reuse it — just make sure `iffy://callback` is registered as a redirect URI.

If you need to create one:

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in:
   - App name: `Iffy` (or anything you like)
   - App description: `Personal playlist manager`
   - Redirect URI: **`iffy://callback`**
4. Check the **Web API** checkbox
5. Click **Save**
6. Go to your app's settings and copy the **Client ID**

### 2. Add Your Client ID

You have two options:

#### Option A: Properties file (recommended for development)

Copy the example file and add your Client ID:

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties`:

```properties
SPOTIFY_CLIENT_ID=your_actual_client_id_here
```

This file is gitignored and the value is injected at build time via `BuildConfig`.

#### Option B: Enter in the app

You can skip the properties file entirely. When you first open the app, there's a text field where you can paste your Client ID before signing in. It's stored securely on-device.

### 3. Build & Run

1. Open the `android/` folder in **Android Studio** (File → Open → select the `android` directory)
2. Wait for Gradle to sync and download dependencies
3. If prompted about the Gradle wrapper, allow Android Studio to generate it
4. Connect a physical Android device or start an emulator
5. Click **Run** (▶) or press Shift+F10

> **Note**: If Gradle complains about missing `local.properties`, Android Studio will typically create it automatically. If not, create `android/local.properties` with:
> ```properties
> sdk.dir=/path/to/your/Android/Sdk
> ```

### 4. First Launch

1. Open the Iffy app
2. Enter your Spotify Client ID if you didn't use the properties file
3. Tap **Sign in with Spotify**
4. A browser tab will open — authorize the app with your Spotify account
5. You'll be redirected back to Iffy automatically
6. The app will ask for notification permission — **grant it** (required for the notification controls)
7. A persistent notification will appear with your current track and action buttons

## Usage

### Notification Controls

Once signed in, a persistent notification shows:

```
┌──────────────────────────────────────┐
│ ♪ Song Name                          │
│   Artist Name                        │
│   Playing from playlist              │
│                                      │
│  [Remove+Skip]  [Favorite]  [Skip]  │
└──────────────────────────────────────┘
```

- **Remove+Skip**: Removes the track from the playlist it's playing from, skips to the next track, and seeks forward by your configured offset (default 30 seconds). Only appears when playing from a playlist.
- **Favorite**: Adds the track to your chosen favorites playlist, removes it from the current playlist, and skips. Requires a favorites playlist to be set in the app settings.
- **Skip**: Skips to the next track.
- **Tap the notification body**: Opens the full app for settings and additional controls.

The notification updates automatically every 5 seconds to reflect the current track.

### In-App Controls

The app mirrors the same three actions as buttons, plus provides settings:

- **Favorites Playlist**: Choose which playlist the "Favorite" button adds to
- **Skip offset after remove**: How many seconds to seek into the next track after a Remove+Skip (default 30)
- **Notification Controls toggle**: Start or stop the background service
- **Sign Out**: Clears your tokens and stops the service

## Architecture

```
android/app/src/main/java/com/iffy/android/
├── IffyApp.kt          # Application class, notification channel setup
├── MainActivity.kt      # Entry point, OAuth callback handling, service lifecycle
├── MainScreen.kt        # Jetpack Compose UI (now playing, settings, actions)
├── SpotifyService.kt    # Foreground service: polling, notification, action handlers
├── SpotifyApi.kt        # Spotify Web API HTTP client (OkHttp + kotlinx.serialization)
├── SpotifyAuth.kt       # OAuth PKCE flow via Chrome Custom Tabs
├── TokenManager.kt      # Encrypted token storage (EncryptedSharedPreferences)
└── Models.kt            # Data classes for Spotify API responses
```

### How it maps to the macOS version

| macOS | Android | Notes |
|-------|---------|-------|
| Menu bar icon + dropdown | Persistent notification with action buttons | Always accessible from notification shade / lock screen |
| Global keyboard shortcuts | Notification action buttons | Tap directly without opening the app |
| Settings window | In-app settings screen | Favorites playlist, skip offset, service toggle |
| Keychain | EncryptedSharedPreferences | Same security model, different platform API |
| SpotifyWebAPI Swift package | Direct HTTP calls via OkHttp | Same Spotify Web API endpoints |
| Polling timer (5s) | Coroutine polling loop (5s) | Same interval, same approach |

## Troubleshooting

### "Sign in" button does nothing
- Make sure you've entered a valid Spotify Client ID (either in `secrets.properties` or in the text field)
- The Client ID field must not be empty

### Authorization fails or redirects to a blank page
- Verify that `iffy://callback` is registered as a Redirect URI in your [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) app settings
- Make sure you copied the Client ID correctly (no extra spaces)

### Notification doesn't appear
- Check that you granted notification permission when prompted
- On Android 13+, go to **Settings → Apps → Iffy → Notifications** and make sure they're enabled
- Make sure the "Notification Controls" toggle is on in the app

### Notification buttons don't respond
- Make sure Spotify is actively playing on some device (phone, desktop, or web)
- The "Remove+Skip" button only appears when Spotify is playing from a playlist (not an album, liked songs, or radio)

### "Remove+Skip" button is missing from the notification
- This button only shows when the current track is playing from a playlist context
- If playing from an album, artist page, or search results, only Favorite and Skip will appear

### Token expired / had to re-login
- This usually means the refresh token was revoked (e.g., you revoked app access in your Spotify account settings)
- Open the app, sign out, then sign in again

### Battery usage concerns
- The service polls Spotify's API every 5 seconds while active
- You can toggle the service off in the app when not using it
- The notification uses `IMPORTANCE_LOW` so it makes no sound or vibration
