# Iffy

Quick Spotify playlist management for **macOS** and **Android**. Remove songs, move tracks between playlists, and control playback — all without opening Spotify.

| macOS | Android |
|-------|---------|
| Menu bar app with global keyboard shortcuts | Notification with action buttons |
| <img width="610" height="720" alt="Iffy macOS" src="https://github.com/user-attachments/assets/d4bb54b7-5948-485e-9cad-d02e683327cb" /> | <img width="350" height="715" alt="Iffy Android" src="https://github.com/user-attachments/assets/b0bd9d63-02f3-41d2-b940-70b0b51f75b4" /> |

## Features

Both platforms share the same core features:

- **Remove + Skip**: Remove the current song from its playlist and skip to the next track
- **Add to Favorites**: Add to your designated favorites playlist (with optional remove from current)
- **Move to Playlist**: Add the current track to any playlist, optionally removing from the source
- **Persistent Auth**: Sign in once, stay signed in (tokens stored securely)

No background polling — data is fetched on demand when you interact with the app.

## Project Structure

```
iffy/
├── macos/      macOS menu bar app (Swift/SwiftUI)
├── android/    Android app (Kotlin/Jetpack Compose)
└── README.md   ← you are here
```

Each platform is a fully independent native codebase. See the platform-specific READMEs for setup, build, and usage instructions:

- **[macOS README](macos/README.md)** — menu bar app with global keyboard shortcuts
- **[Android README](android/README.md)** — persistent notification with action buttons

## Quick Start

Both platforms need a **Spotify Premium** account and a Spotify Developer app.

### 1. Create a Spotify Developer App

This step is shared — if you've done it for one platform, reuse the same app for the other.

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in:
   - App name: `Iffy` (or anything you like)
   - App description: `Personal playlist manager`
   - Redirect URI: **`iffy://callback`**
4. Check the **Web API** checkbox
5. Click **Save**
6. Copy the **Client ID** from your app's settings

### 2. Platform Setup

**macOS**: Add your Client ID to `macos/Sources/Iffy/Resources/Secrets.plist`, then build with Xcode or `bash macos/build.sh`. See the [macOS README](macos/README.md) for full instructions.

**Android**: Add your Client ID to `android/secrets.properties`, then build with Android Studio. See the [Android README](android/README.md) for full instructions.
