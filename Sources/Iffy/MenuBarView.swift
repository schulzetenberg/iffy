import SwiftUI
import KeyboardShortcuts

/// The menu bar dropdown content
struct MenuBarView: View {
    @ObservedObject var spotify = SpotifyManager.shared
    @AppStorage("defaultPlaylistId") private var defaultPlaylistId: String = ""
    @AppStorage("defaultPlaylistName") private var defaultPlaylistName: String = ""
    @State private var showSettings = false

    var body: some View {
        Group {
            if spotify.isAuthorized {
                authorizedMenu
            } else {
                unauthorizedMenu
            }
        }
    }

    // MARK: - Unauthorized State

    private var unauthorizedMenu: some View {
        VStack(spacing: 0) {
            Button("Sign in to Spotify") {
                spotify.authorize()
            }
            .keyboardShortcut("l", modifiers: .command)

            Divider()

            Button("Quit Iffy") {
                NSApplication.shared.terminate(nil)
            }
            .keyboardShortcut("q", modifiers: .command)
        }
    }

    // MARK: - Authorized State

    private var authorizedMenu: some View {
        VStack(spacing: 0) {
            // Now Playing Section
            nowPlayingSection

            Divider()

            // Quick Actions
            quickActionsSection

            Divider()

            // Add to Playlist
            addToPlaylistSection

            Divider()

            // Settings & Quit
            footerSection
        }
    }

    // MARK: - Menu Sections

    private var nowPlayingSection: some View {
        Group {
            if let track = spotify.currentTrack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(track.name)
                        .font(.headline)
                        .lineLimit(1)

                    if let artists = track.artists?.compactMap({ $0.name }).joined(separator: ", ") {
                        Text(artists)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }

                    if spotify.currentPlaylistURI != nil {
                        Text("Playing from playlist")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text("Not playing")
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
            }
        }
    }

    private var quickActionsSection: some View {
        Group {
            // Remove + Skip
            Button {
                Task {
                    _ = await spotify.removeAndSkip()
                }
            } label: {
                HStack {
                    Image(systemName: "trash")
                    Text("Remove + Skip")
                }
            }
            .globalKeyboardShortcut(.removeAndSkip)
            .disabled(spotify.currentTrack == nil || spotify.currentPlaylistURI == nil)

            // Add to Favorites (default playlist)
            if !defaultPlaylistId.isEmpty {
                Button {
                    Task {
                        _ = await spotify.addToPlaylist(defaultPlaylistId, removeFromCurrent: true)
                    }
                } label: {
                    HStack {
                        Image(systemName: "heart.fill")
                        Text("Add to \(defaultPlaylistName)")
                    }
                }
                .globalKeyboardShortcut(.addToFavorites)
                .disabled(spotify.currentTrack == nil)
            }

            // Skip only
            Button {
                Task {
                    await spotify.skipToNext()
                }
            } label: {
                HStack {
                    Image(systemName: "forward.fill")
                    Text("Skip")
                }
            }
            .globalKeyboardShortcut(.skipTrack)
            .disabled(spotify.currentTrack == nil)
        }
    }

    @ViewBuilder
    private var addToPlaylistSection: some View {
        Menu {
            ForEach(spotify.playlists, id: \.uri) { playlist in
                Button(playlist.name) {
                    Task {
                        _ = await spotify.addToPlaylist(playlist.id, removeFromCurrent: false)
                    }
                }
            }
        } label: {
            HStack {
                Image(systemName: "plus.circle")
                Text("Add to Playlist...")
            }
        }
        .disabled(spotify.currentTrack == nil || spotify.playlists.isEmpty)

        Menu {
            ForEach(spotify.playlists, id: \.uri) { playlist in
                Button(playlist.name) {
                    Task {
                        _ = await spotify.addToPlaylist(playlist.id, removeFromCurrent: true)
                    }
                }
            }
        } label: {
            HStack {
                Image(systemName: "arrow.right.circle")
                Text("Move to Playlist...")
            }
        }
        .disabled(spotify.currentTrack == nil || spotify.currentPlaylistURI == nil || spotify.playlists.isEmpty)
    }

    private var footerSection: some View {
        Group {
            Button {
                showSettings = true
                if let window = SettingsWindowController.shared.window {
                    window.makeKeyAndOrderFront(nil)
                    NSApp.activate(ignoringOtherApps: true)
                }
            } label: {
                HStack {
                    Image(systemName: "gear")
                    Text("Settings...")
                }
            }
            .keyboardShortcut(",", modifiers: .command)

            if let error = spotify.lastError {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
            }

            Divider()

            Button {
                spotify.signOut()
            } label: {
                HStack {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                    Text("Sign Out")
                }
            }

            Button {
                NSApplication.shared.terminate(nil)
            } label: {
                HStack {
                    Image(systemName: "power")
                    Text("Quit Iffy")
                }
            }
            .keyboardShortcut("q", modifiers: .command)
        }
    }
}

// MARK: - Settings Window Controller

final class SettingsWindowController: NSObject {
    static let shared = SettingsWindowController()

    var window: NSWindow?

    override init() {
        super.init()
        setupWindow()
    }

    private func setupWindow() {
        let settingsView = SettingsView()
        let hostingController = NSHostingController(rootView: settingsView)

        window = NSWindow(contentViewController: hostingController)
        window?.title = "Iffy Settings"
        window?.styleMask = [.titled, .closable]
        window?.setContentSize(NSSize(width: 400, height: 300))
        window?.center()
    }
}
