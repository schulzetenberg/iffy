import SwiftUI
import ServiceManagement
import KeyboardShortcuts

// MARK: - Keyboard Shortcut Names

extension KeyboardShortcuts.Name {
    static let removeAndSkip = Self("removeAndSkip", default: .init(.r, modifiers: [.command, .shift]))
    static let addToFavorites = Self("addToFavorites", default: .init(.f, modifiers: [.command, .shift]))
    static let skipTrack = Self("skipTrack", default: .init(.s, modifiers: [.command, .shift]))
}

// MARK: - Settings View

struct SettingsView: View {
    @ObservedObject var spotify = SpotifyManager.shared
    @AppStorage("defaultPlaylistId") private var defaultPlaylistId: String = ""
    @AppStorage("defaultPlaylistName") private var defaultPlaylistName: String = ""
    @AppStorage("launchAtLogin") private var launchAtLogin: Bool = false
    @AppStorage("skipOffsetSeconds") private var skipOffsetSeconds: Int = 30

    var body: some View {
        Form {
            Section {
                defaultPlaylistPicker
            } header: {
                Text("Default Playlist")
            } footer: {
                Text("This playlist is used for the \"Add to Favorites\" action")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Keyboard Shortcuts") {
                keyboardShortcutsSection
            }

            Section("Playback") {
                skipOffsetField
            }

            Section("General") {
                launchAtLoginToggle
            }

            Section {
                refreshPlaylistsButton
            }
        }
        .formStyle(.grouped)
        .padding()
        .frame(width: 400, height: 400)
        .onAppear {
            if spotify.playlists.isEmpty {
                Task {
                    await spotify.fetchPlaylists()
                }
            }
        }
    }

    // MARK: - Default Playlist Picker

    private var defaultPlaylistPicker: some View {
        Picker("Favorites Playlist", selection: $defaultPlaylistId) {
            Text("None selected").tag("")
            ForEach(spotify.playlists, id: \.uri) { playlist in
                Text(playlist.name).tag(playlist.id)
            }
        }
        .onChange(of: defaultPlaylistId) { newValue in
            if let playlist = spotify.playlists.first(where: { $0.id == newValue }) {
                defaultPlaylistName = playlist.name
            } else {
                defaultPlaylistName = ""
            }
        }
    }

    // MARK: - Keyboard Shortcuts

    private var keyboardShortcutsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Remove + Skip")
                Spacer()
                KeyboardShortcuts.Recorder(for: .removeAndSkip)
            }

            HStack {
                Text("Add to Favorites")
                Spacer()
                KeyboardShortcuts.Recorder(for: .addToFavorites)
            }

            HStack {
                Text("Skip Track")
                Spacer()
                KeyboardShortcuts.Recorder(for: .skipTrack)
            }
        }
    }

    // MARK: - Skip Offset

    private var skipOffsetField: some View {
        HStack {
            Text("Skip offset after remove")
            Spacer()
            TextField("", value: $skipOffsetSeconds, format: .number)
                .textFieldStyle(.roundedBorder)
                .frame(width: 60)
            Text("sec")
                .foregroundColor(.secondary)
        }
    }

    // MARK: - Launch at Login

    private var launchAtLoginToggle: some View {
        Toggle("Launch at Login", isOn: $launchAtLogin)
            .onChange(of: launchAtLogin) { newValue in
                updateLaunchAtLogin(enabled: newValue)
            }
    }

    private func updateLaunchAtLogin(enabled: Bool) {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
        } catch {
            print("Failed to update launch at login: \(error)")
        }
    }

    // MARK: - Refresh Button

    private var refreshPlaylistsButton: some View {
        Button {
            Task {
                await spotify.fetchPlaylists()
            }
        } label: {
            if spotify.isLoading {
                ProgressView()
                    .scaleEffect(0.7)
            } else {
                Label("Refresh Playlists", systemImage: "arrow.clockwise")
            }
        }
        .disabled(spotify.isLoading || !spotify.isAuthorized)
    }
}

// MARK: - Preview

#Preview {
    SettingsView()
}
