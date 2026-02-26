import SwiftUI
import KeyboardShortcuts

@main
struct IffyApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @ObservedObject var spotify = SpotifyManager.shared

    var body: some Scene {
        // Menu bar item
        MenuBarExtra {
            MenuBarView()
        } label: {
            Image(systemName: "music.note.list")
        }
        .menuBarExtraStyle(.menu)
    }
}

// MARK: - App Delegate

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        // Setup global keyboard shortcuts
        setupGlobalShortcuts()
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        // Handle OAuth callback URL
        guard let url = urls.first,
              url.scheme == "iffy",
              url.host == "callback" else {
            return
        }

        Task { @MainActor in
            await SpotifyManager.shared.handleCallback(url: url)
        }
    }

    private func setupGlobalShortcuts() {
        KeyboardShortcuts.onKeyUp(for: .removeAndSkip) {
            Task { @MainActor in
                _ = await SpotifyManager.shared.removeAndSkip()
            }
        }

        KeyboardShortcuts.onKeyUp(for: .addToFavorites) {
            Task { @MainActor in
                let defaultPlaylistId = UserDefaults.standard.string(forKey: "defaultPlaylistId") ?? ""
                guard !defaultPlaylistId.isEmpty else { return }
                _ = await SpotifyManager.shared.addToPlaylist(defaultPlaylistId, removeFromCurrent: true)
            }
        }

        KeyboardShortcuts.onKeyUp(for: .skipTrack) {
            Task { @MainActor in
                await SpotifyManager.shared.skipToNext()
            }
        }
    }
}
