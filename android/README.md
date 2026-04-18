# Iffy for Android

An Android app for quick Spotify playlist management. Remove songs, move tracks between playlists, and skip — all from a persistent notification without ever opening Spotify.

## Features

- **Notification Controls**: Remove+Skip, Favorite, and Skip buttons right on the notification
- **Remove + Skip**: Remove the current song from its playlist, skip to the next track, and seek past the intro
- **Add to Favorites**: One-tap add to your designated favorites playlist (removes from current and skips)
- **Skip**: Skip to the next track
- **Lock Screen Access**: Actions available from the lock screen and notification shade
- **Persistent Auth**: Sign in once — tokens stored securely with Android's EncryptedSharedPreferences
- **Dark Theme**: Material 3 dark mode UI

No background polling — each action fetches the current track from Spotify's API before executing, and the app refreshes when you open it.

## Requirements

- Android 8.0 (Oreo / API 26) or later
- Spotify Premium account (required for playback control)
- Android Studio (for building)

## Setup

### 1. Spotify Developer App

If you already have a Spotify Developer app (e.g. from the macOS version), you can reuse it — just make sure `iffy://callback` is registered as a redirect URI.

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

You have two options:

#### Option A: Properties file (recommended for development)

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties`:

```properties
SPOTIFY_CLIENT_ID=your_actual_client_id_here
```

This file is gitignored and the value is injected at build time via `BuildConfig`.

#### Option B: Enter in the app

Skip the properties file entirely. When you first open the app, there's a text field where you can paste your Client ID before signing in. It's stored securely on-device.

### 3. Build & Run

1. Open the `android/` folder in **Android Studio** (File > Open > select the `android` directory)
2. Wait for Gradle to sync and download dependencies
3. Connect a physical Android device or start an emulator
4. Click **Run** or press Shift+F10

> **Note**: If Gradle complains about missing `local.properties`, Android Studio typically creates it automatically. If not, create `android/local.properties` with:
> ```properties
> sdk.dir=/path/to/your/Android/Sdk
> ```

### 4. First Launch

1. Open the Iffy app
2. Enter your Spotify Client ID if you didn't use the properties file
3. Tap **Sign in with Spotify**
4. Authorize the app in the browser
5. Grant notification permission when prompted
6. A persistent notification with action buttons will appear

## Release Build (Self-Signed APK)

If you want to install Iffy on your phone as a proper release build — no debug watermark, stable identity for updates, no need to keep your phone tethered to Android Studio — use the self-signed release flow below.

> **About "Install unknown apps":** Android always requires you to grant "Install unknown apps" permission to whichever app opens a sideloaded APK (your file manager, browser, etc.). This is a platform requirement, not a signing issue — it can't be bypassed by signing. The only way to install without that prompt is via ADB over USB.

### 1. Configure the keystore password

Copy the example file and set your own password:

```bash
cp android/keystore.properties.example android/keystore.properties
```

Edit `android/keystore.properties`:

```properties
storeFile=release.keystore
storePassword=your-strong-password-here
keyAlias=iffy-release
keyPassword=your-strong-password-here
```

> Both `keystore.properties` and the generated `*.keystore` are gitignored. **Back them up somewhere safe** (password manager, encrypted cloud folder). If you lose the keystore you won't be able to publish updates that install on top of the existing APK — the phone will reject them with a signature mismatch and you'd have to uninstall first (losing app data).

### 2. Build the signed APK

```bash
bash android/build-signed.sh
```

On first run the script will:

1. Generate `android/release.keystore` with `keytool` (RSA 2048, 25-year validity)
2. Locate a Gradle binary (prefers `gradle` on `PATH`, falls back to the Android Studio cached wrapper at `~/.gradle/wrapper/dists/gradle-*-bin/*/gradle-*/bin/gradle`)
3. Run `gradle :app:assembleRelease`
4. Verify the signature with `apksigner` from the Android SDK `build-tools`

Subsequent runs reuse the existing keystore. The signed APK will be at:

```
android/app/build/outputs/apk/release/app-release.apk
```

> **Prerequisites:** Java 17+, a working Android SDK at `~/Library/Android/sdk` (install via Android Studio), and either a `gradle` on `PATH` or a prior Android Studio Gradle sync so the wrapper cache exists. Run `brew install gradle` if neither is available.

### 3. Install on your phone

You have two options.

#### Option A: ADB over USB (recommended — no "unknown sources" prompt)

1. On your phone, enable **Developer options** (Settings > About phone > tap "Build number" 7 times)
2. In Developer options, enable **USB debugging**
3. Plug the phone into your Mac via USB and tap **Allow** on the phone's debugging prompt
4. Install:

```bash
~/Library/Android/sdk/platform-tools/adb install -r android/app/build/outputs/apk/release/app-release.apk
```

The `-r` flag reinstalls over an existing install while keeping app data. On a first install of a differently-signed build, uninstall first: `adb uninstall com.iffy.android`.

#### Option B: File manager transfer

1. Transfer `app-release.apk` to your phone — AirDrop, Google Drive, email, USB-MTP, or `adb push android/app/build/outputs/apk/release/app-release.apk /sdcard/Download/`
2. On the phone, open your file manager (Files by Google, Samsung My Files, etc.) and tap the APK
3. Android will prompt: **"For your security, your phone currently isn't allowed to install unknown apps from this source."** Tap **Settings** and toggle **Allow from this source** for your file manager
4. Go back and tap **Install**
5. The permission stays granted for that file manager, so future APKs install with one tap

### 4. Updating the app

As long as you always sign with the same `release.keystore`, you can rebuild and install over the previous version without uninstalling — app data and tokens will be preserved.

If you ever see **"App not installed as package conflicts with an existing package"**, it means the signature doesn't match the installed copy. Either:

- Restore the original keystore from backup, or
- `adb uninstall com.iffy.android` (or uninstall from Settings) — you'll lose tokens and will need to sign in again

## Usage

### Notification Controls

Once signed in, a persistent notification provides three action buttons:

```
┌──────────────────────────────────────┐
│ Iffy                                 │
│ Spotify controls ready               │
│                                      │
│  [Remove+Skip]  [Favorite]  [Skip]  │
└──────────────────────────────────────┘
```

- **Remove+Skip**: Fetches the current track, removes it from its playlist, skips to next, and seeks forward by your configured offset (default 30s). Shows an error if not playing from a playlist.
- **Favorite**: Fetches the current track, adds it to your favorites playlist, removes from current playlist, and skips.
- **Skip**: Skips to the next track.
- **Tap the notification**: Opens the full app.

Each button fetches the latest playback state from Spotify before acting, so the notification doesn't need to display the current track.

### In-App

The app shows the current track (refreshed when you open the app) and mirrors the same three actions as buttons. Settings include:

- **Favorites Playlist**: Choose which playlist the Favorite button adds to
- **Skip offset after remove**: Seconds to seek into the next track after Remove+Skip (default 30)
- **Notification Controls toggle**: Start or stop the background service
- **Sign Out**: Clears tokens and stops the service

## Architecture

```
app/src/main/java/com/iffy/android/
├── IffyApp.kt          # Application class, notification channel setup
├── MainActivity.kt      # Entry point, OAuth callback, service lifecycle
├── MainScreen.kt        # Jetpack Compose UI (now playing, settings, actions)
├── SpotifyService.kt    # Foreground service, notification, action handlers
├── SpotifyApi.kt        # Spotify Web API client (OkHttp + kotlinx.serialization)
├── SpotifyAuth.kt       # OAuth PKCE flow via Chrome Custom Tabs
├── TokenManager.kt      # Encrypted token storage (EncryptedSharedPreferences)
└── Models.kt            # Data classes for Spotify API responses
```

## Troubleshooting

### "Sign in" button does nothing

Make sure you've entered a valid Spotify Client ID (either in `secrets.properties` or in the text field). The field must not be empty.

### Authorization fails or redirects to a blank page

Verify that `iffy://callback` is registered as a Redirect URI in your [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) app settings, and that the Client ID is correct.

### Notification doesn't appear

- Check that you granted notification permission when prompted
- On Android 13+, go to **Settings > Apps > Iffy > Notifications** and make sure they're enabled
- Make sure the "Notification Controls" toggle is on in the app

### Notification buttons don't respond

Make sure Spotify is actively playing on some device (phone, desktop, or web player).

### "Not playing from a playlist" after tapping Remove+Skip

This action only works when Spotify is playing from a playlist context. Albums, liked songs, and radio don't support track removal.

### Token expired / had to re-login

The refresh token was likely revoked (e.g. you revoked app access in Spotify account settings). Sign out and sign in again.

### `build-signed.sh` can't find Gradle

Either install Gradle (`brew install gradle`) or open `android/` once in Android Studio and let it do a Gradle sync — that populates the wrapper cache the script falls back to.

### "App not installed" when installing the APK

Usually a signature conflict with a previously installed version (e.g. a debug build from Android Studio or an older self-signed APK with a different key). Uninstall first: `adb uninstall com.iffy.android` (or uninstall via Settings), then install the new APK.

### Forgot the keystore password

The password is stored in `android/keystore.properties` — check there first. If you've genuinely lost it and lost your backup, you'll need to delete `android/release.keystore`, set a new password in `keystore.properties`, re-run `build-signed.sh` to generate a fresh keystore, and uninstall the app from your phone before installing the newly-signed build.
