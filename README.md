# Iffy

Quick Spotify playlist management for **macOS** and **Android**. Remove songs and skip, move tracks between playlists, and control playback — all without opening Spotify.

| macOS | Android |
|-------|---------|
| Menu bar app with global keyboard shortcuts | Persistent notification with action buttons |
| <img width="610" height="720" alt="Iffy macOS" src="https://github.com/user-attachments/assets/d4bb54b7-5948-485e-9cad-d02e683327cb" /> | <img width="350" height="715" alt="Iffy Android" src="https://github.com/user-attachments/assets/b0bd9d63-02f3-41d2-b940-70b0b51f75b4" /> |

## Features

Both platforms share the same core features:

- **Remove + Skip**: Remove the current song from its playlist and skip to the next track
- **Add to Favorites**: One-click/tap add to your designated favorites playlist (with optional remove from current)
- **Move to Playlist**: Add current track to any playlist, optionally removing from the source
- **Persistent Auth**: Sign in once, stay signed in (tokens stored securely)

### macOS-specific
- **Global Keyboard Shortcuts**: Control Spotify from any app
- **Launch at Login**: Start automatically when you log in
- **Menu Bar UI**: Everything accessible from the menu bar icon

### Android-specific
- **Notification Controls**: Remove+Skip, Favorite, and Skip buttons right on the notification — no need to open the app
- **Lock Screen Access**: Actions available from the lock screen and notification shade
- **Dark Theme**: Material 3 dark mode UI

## Requirements

- **Spotify Premium** account (required for playback control on both platforms)
- **macOS**: macOS 13.0 (Ventura) or later, Xcode 15+ (for building)
- **Android**: Android 8.0 (Oreo / API 26) or later, Android Studio (for building)

## Setup

### 1. Create a Spotify Developer App

This step is shared between both platforms. If you've already done it for one, you can reuse the same app for the other.

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in:
   - App name: `Iffy` (or anything you like)
   - App description: `Personal playlist manager`
   - Redirect URI: **`iffy://callback`**
4. Check the **Web API** checkbox
5. Click **Save**
6. Go to your app's settings and copy the **Client ID**

### 2. Platform Setup

#### macOS

**Add your Client ID:**

Copy `Secrets.example.plist` to `Secrets.plist` and add your Client ID. The real `Secrets.plist` is not tracked in version control.

Open `Sources/Iffy/Resources/Secrets.plist` and replace:

```xml
<string>YOUR_CLIENT_ID_HERE</string>
```

with your actual Client ID:

```xml
<string>abc123your_actual_client_id</string>
```

**Build & Run (Xcode — recommended):**

1. Open the project folder in Xcode:
   ```bash
   cd /path/to/iffy
   open Package.swift
   ```
2. Wait for Xcode to resolve package dependencies
3. Select **My Mac** as the run destination
4. Click **Run** (⌘R)

**Build & Run (command line):**

```bash
$ bash build.sh
```

The built executable will be at `.build/release/Iffy`

**First Launch:**

1. Click the music note icon (♪) in your menu bar
2. Click **Sign in to Spotify**
3. Your browser will open — authorize the app
4. You'll be redirected back to Iffy (the app handles the `iffy://callback` URL)
5. Done! The menu will now show your current track

#### Android

See the full [Android README](android/README.md) for detailed instructions. Quick start:

1. Copy `android/secrets.properties.example` to `android/secrets.properties` and add your Client ID
2. Open the `android/` folder in Android Studio
3. Run on your device
4. Sign in with Spotify when prompted
5. Grant notification permission — action buttons appear in your notification shade

## Usage

### macOS — Menu Bar

Click the menu bar icon to see:
- **Now Playing**: Current track and artist
- **Remove + Skip** (⌘R): Delete from playlist, play next
- **Add to [Favorites]** (⌘F): Add to your default playlist
- **Add to Playlist...**: Choose any playlist to add to
- **Move to Playlist...**: Add to another playlist AND remove from current
- **Settings**: Configure default playlist, keyboard shortcuts, launch at login

### macOS — Global Keyboard Shortcuts

Default shortcuts (customizable in Settings):

| Action | Default Shortcut |
|--------|------------------|
| Remove + Skip | ⌘⇧R |
| Add to Favorites | ⌘⇧F |
| Skip Track | ⌘⇧S |

These work from any app — no need to have Iffy focused.

### macOS — Settings

Open Settings (⌘,) to:
- **Set Default Playlist**: Choose which playlist "Add to Favorites" uses
- **Customize Shortcuts**: Record your own key combinations
- **Launch at Login**: Toggle auto-start

### Android — Notification Controls

A persistent notification shows the current track with action buttons:
- **Remove+Skip**: Removes from playlist, skips, and seeks past the intro
- **Favorite**: Adds to your favorites playlist, removes from current, and skips
- **Skip**: Skips to the next track

Tap the notification body to open the full app for settings (favorites playlist, skip offset, etc.).

## Architecture

### macOS

```
Sources/Iffy/
├── IffyApp.swift         # App entry point, menu bar setup
├── MenuBarView.swift     # Menu UI and actions
├── SettingsView.swift    # Settings window
├── SpotifyManager.swift  # Spotify API & OAuth
├── KeychainManager.swift # Secure token storage
└── Resources/
    ├── Info.plist        # App configuration
    └── Iffy.entitlements # Keychain & network permissions
```

### Android

```
android/app/src/main/java/com/iffy/android/
├── IffyApp.kt          # Application class, notification channel
├── MainActivity.kt      # Entry point, OAuth callback, service lifecycle
├── MainScreen.kt        # Jetpack Compose UI
├── SpotifyService.kt    # Foreground service + notification actions
├── SpotifyApi.kt        # Spotify Web API client
├── SpotifyAuth.kt       # OAuth PKCE flow
├── TokenManager.kt      # Encrypted token storage
└── Models.kt            # API response data classes
```

## Troubleshooting

### Both platforms

- **"Authorization failed" on sign-in**: Make sure `iffy://callback` is a Redirect URI in your Spotify app settings, and that your Client ID is correct
- **Track not removing from playlist**: You must be playing from a playlist (not an album, liked songs, or radio). Both apps indicate "Playing from playlist" when this is available.
- **Token expired / had to re-login**: The refresh token was likely revoked (e.g., you revoked access in Spotify settings). Sign out and sign in again.

### macOS

- **Shortcuts not working globally**: Go to **System Settings → Privacy & Security → Accessibility** and make sure Iffy is in the list and enabled. You may need to remove and re-add it.

### Android

- **Notification doesn't appear**: Make sure you granted notification permission. On Android 13+, check **Settings → Apps → Iffy → Notifications**.
- **"Remove+Skip" button missing from notification**: This only appears when playing from a playlist context.
- See the [Android README](android/README.md) for more troubleshooting tips.
