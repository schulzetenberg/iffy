# Iffy for macOS

A menu bar app for quick Spotify playlist management. Remove songs, move tracks between playlists, and control playback — all from the menu bar or global keyboard shortcuts.

| Menu Bar | Settings |
|----------|----------|
| <img width="610" height="720" alt="Iffy macOS" src="https://github.com/user-attachments/assets/d4bb54b7-5948-485e-9cad-d02e683327cb" /> | Configure shortcuts, default playlist, and launch at login |

## Features

- **Remove + Skip**: Remove the current song from its playlist, skip to the next track, and seek past the intro
- **Add to Favorites**: One-click add to your designated favorites playlist (removes from current and skips)
- **Move to Playlist**: Add the current track to any playlist, optionally removing from the source
- **Global Keyboard Shortcuts**: Control Spotify from any app without switching focus
- **Launch at Login**: Start automatically when you log in
- **Persistent Auth**: Sign in once — tokens stored securely in the macOS Keychain

## Requirements

- macOS 13.0 (Ventura) or later
- Spotify Premium account (required for playback control)
- Xcode 15+ (for building)

## Setup

### 1. Spotify Developer App

If you already have a Spotify Developer app (e.g. from the Android version), you can reuse it — just make sure `iffy://callback` is registered as a redirect URI.

Otherwise:

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

Copy the example file and add your Client ID:

```bash
cp Sources/Iffy/Resources/Secrets.example.plist Sources/Iffy/Resources/Secrets.plist
```

Edit `Sources/Iffy/Resources/Secrets.plist` and replace:

```xml
<string>YOUR_CLIENT_ID_HERE</string>
```

with your actual Client ID:

```xml
<string>abc123your_actual_client_id</string>
```

The real `Secrets.plist` is gitignored.

### 3. Build & Run

**Xcode (recommended):**

```bash
open Package.swift
```

Wait for dependencies to resolve, select **My Mac** as the destination, and hit **Run** (Cmd+R).

**Command line:**

```bash
bash build.sh
```

The built `.app` bundle will appear in this directory. Run it with `open Iffy.app` or copy it to `/Applications/`.

### 4. First Launch

1. Click the music note icon in your menu bar
2. Click **Sign in to Spotify**
3. Authorize the app in your browser
4. You'll be redirected back to Iffy automatically

## Usage

### Menu Bar

Click the menu bar icon to see:

- **Now Playing** — current track and artist (fetched when you open the menu)
- **Remove + Skip** — delete from playlist and play the next track
- **Add to [Favorites]** — add to your default playlist
- **Add to Playlist...** — choose any playlist to add to
- **Move to Playlist...** — add to another playlist and remove from current
- **Settings** — configure default playlist, keyboard shortcuts, launch at login

### Global Keyboard Shortcuts

Default shortcuts (customizable in Settings):

| Action | Default Shortcut |
|--------|------------------|
| Remove + Skip | Cmd+Shift+R |
| Add to Favorites | Cmd+Shift+F |
| Skip Track | Cmd+Shift+S |

These work from any app — no need to have Iffy focused. Each shortcut fetches the current track from Spotify before acting, so there's no background polling.

### Settings

Open Settings (Cmd+,) to:

- **Set Default Playlist**: Choose which playlist "Add to Favorites" uses
- **Customize Shortcuts**: Record your own key combinations
- **Launch at Login**: Toggle auto-start
- **Skip Offset**: How many seconds to seek into the next track after a Remove+Skip

## Architecture

```
Sources/Iffy/
├── IffyApp.swift         # App entry point, MenuBarExtra, global shortcuts
├── MenuBarView.swift     # Menu UI and actions
├── SettingsView.swift    # Settings window
├── SpotifyManager.swift  # Spotify API client, OAuth PKCE, state management
├── KeychainManager.swift # Secure token storage via macOS Keychain
└── Resources/
    ├── Info.plist        # App configuration
    ├── Iffy.entitlements # Keychain & network permissions
    └── Secrets.plist     # Your Spotify Client ID (gitignored)
```

Data is fetched on demand — when you open the menu or trigger an action — rather than polling in the background.

## Troubleshooting

### Shortcuts not working globally

Go to **System Settings > Privacy & Security > Accessibility** and make sure Iffy is listed and enabled. You may need to remove and re-add it.

### "Authorization failed" on sign-in

Make sure `iffy://callback` is a Redirect URI in your Spotify app settings, and that your Client ID is correct in `Secrets.plist`.

### Track not removing from playlist

You must be playing from a playlist (not an album, liked songs, or radio). The menu shows "Playing from playlist" when Remove+Skip is available.

### Token expired / had to re-login

The refresh token was likely revoked (e.g. you revoked access in Spotify settings). Sign out and sign in again.
