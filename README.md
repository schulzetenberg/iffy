# Iffy

A lightweight macOS menu bar app for quick Spotify playlist management. Remove songs and skip, move tracks between playlists, and control playback—all from your menu bar or global keyboard shortcuts.

<img width="610" height="720" alt="image" src="https://github.com/user-attachments/assets/d4bb54b7-5948-485e-9cad-d02e683327cb" />


## Features

- **Remove + Skip**: Remove the current song from its playlist and skip to the next track
- **Add to Favorites**: One-click add to your designated favorites playlist (with optional remove from current)
- **Move to Playlist**: Add current track to any playlist, optionally removing from the source
- **Global Keyboard Shortcuts**: Control Spotify from any app
- **Persistent Auth**: Sign in once, stay signed in (tokens stored securely in Keychain)
- **Launch at Login**: Start automatically when you log in

## Requirements

- macOS 13.0 (Ventura) or later
- Spotify Premium account (required for playback control)
- Xcode 15+ (for building)

## Setup

### 1. Create a Spotify Developer App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in:
   - App name: `Iffy` (or anything you like)
   - App description: `Personal playlist manager`
   - Redirect URI: `iffy://callback`
4. Check the **Web API** checkbox
5. Click **Save**
6. Go to your app's settings and copy the **Client ID**

### 2. Add Your Client ID

The real `Secrets.plist` is not tracked in version control. Copy `Secrets.example.plist` to `Secrets.plist` and add your Client ID.
Open `Sources/Iffy/Resources/Secrets.plist` and replace:

```xml
<string>YOUR_CLIENT_ID_HERE</string>
```

with your actual Client ID:

```xml
<string>abc123your_actual_client_id</string>
```

### 3. Build & Run

#### Option A: Using Xcode (Recommended)

1. Open the project folder in Xcode:
   ```bash
   cd /path/to/iffy
   open Package.swift
   ```
2. Wait for Xcode to resolve package dependencies
3. Select **My Mac** as the run destination
4. Click **Run** (⌘R)

#### Option B: Command Line

```bash
$ bash build.sh
```

The built executable will be at `.build/release/Iffy`

### 4. First Launch

1. Click the music note icon (♪) in your menu bar
2. Click **Sign in to Spotify**
3. Your browser will open—authorize the app
4. You'll be redirected back to Iffy (the app handles the `iffy://callback` URL)
5. Done! The menu will now show your current track

## Usage

### Menu Bar

Click the menu bar icon to see:
- **Now Playing**: Current track and artist
- **Remove + Skip** (⌘R): Delete from playlist, play next
- **Add to [Favorites]** (⌘F): Add to your default playlist
- **Add to Playlist...**: Choose any playlist to add to
- **Move to Playlist...**: Add to another playlist AND remove from current
- **Settings**: Configure default playlist, keyboard shortcuts, launch at login

### Global Keyboard Shortcuts

Default shortcuts (customizable in Settings):
| Action | Default Shortcut |
|--------|------------------|
| Remove + Skip | ⌘⇧R |
| Add to Favorites | ⌘⇧F |
| Skip Track | ⌘⇧S |

These work from any app—no need to have Iffy focused.

### Settings

Open Settings (⌘,) to:
- **Set Default Playlist**: Choose which playlist "Add to Favorites" uses
- **Customize Shortcuts**: Record your own key combinations
- **Launch at Login**: Toggle auto-start

## Architecture

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

## Troubleshooting

### "Authorization failed" on sign-in
- Make sure you added `iffy://callback` as a Redirect URI in your Spotify app settings
- Check that your Client ID is correct

### Shortcuts not working globally
- Go to **System Settings → Privacy & Security → Accessibility**
- Make sure Iffy is in the list and enabled
- You may need to remove and re-add it

### Track not removing from playlist
- Make sure you're playing from a playlist (not an album, liked songs, or radio)
- The menu shows "Playing from playlist" in green when this works

### Token expired / had to re-login
- This usually means the refresh token was revoked (e.g., you revoked access in Spotify settings)
- Sign out in Iffy, then sign in again

