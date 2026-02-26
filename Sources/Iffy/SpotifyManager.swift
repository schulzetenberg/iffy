import Foundation
import SpotifyWebAPI
import Combine
import AuthenticationServices

/// Manages Spotify authentication and API interactions
@MainActor
final class SpotifyManager: ObservableObject {
    static let shared = SpotifyManager()

    // MARK: - Configuration

    private static let clientId: String = {
        let url = Bundle.module.url(forResource: "Secrets", withExtension: "plist")
        guard let url = url else {
            print("[Iffy] Secrets.plist not found in bundle.")
            fatalError("Secrets.plist not found in bundle.")
        }
        guard let data = try? Data(contentsOf: url) else {
            print("[Iffy] Could not read Secrets.plist data.")
            fatalError("Could not read Secrets.plist data.")
        }
        guard let plist = try? PropertyListSerialization.propertyList(from: data, options: [], format: nil) as? [String: Any] else {
            print("[Iffy] Could not parse Secrets.plist.")
            fatalError("Could not parse Secrets.plist.")
        }
        guard let id = plist["SpotifyClientId"] as? String else {
            print("[Iffy] SpotifyClientId not found in Secrets.plist.")
            fatalError("SpotifyClientId not found in Secrets.plist.")
        }
        return id
    }()
    private static let redirectURI = URL(string: "iffy://callback")!

    private static let scopes: Set<Scope> = [
        .userReadCurrentlyPlaying,
        .userReadPlaybackState,
        .userModifyPlaybackState,
        .playlistReadPrivate,
        .playlistModifyPrivate,
        .playlistModifyPublic
    ]

    // MARK: - Published State

    @Published private(set) var isAuthorized = false
    @Published private(set) var currentTrack: Track?
    @Published private(set) var currentPlaylistURI: String?
    @Published private(set) var playlists: [Playlist<PlaylistItemsReference>] = []
    @Published private(set) var isLoading = false
    @Published private(set) var lastError: String?

    // MARK: - Private Properties

    let spotify: SpotifyAPI<AuthorizationCodeFlowPKCEManager>
    private var cancellables = Set<AnyCancellable>()
    private var pollingTimer: Timer?

    // MARK: - Initialization

    private init() {
        let authManager = AuthorizationCodeFlowPKCEManager(clientId: Self.clientId)
        self.spotify = SpotifyAPI(authorizationManager: authManager)

        setupAuthStateObserver()
        restoreAuthFromKeychain()
    }

    // MARK: - Auth State Observer

    private func setupAuthStateObserver() {
        spotify.authorizationManagerDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.handleAuthChange()
            }
            .store(in: &cancellables)

        spotify.authorizationManagerDidDeauthorize
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.handleDeauthorization()
            }
            .store(in: &cancellables)
    }

    private func handleAuthChange() {
        isAuthorized = spotify.authorizationManager.isAuthorized()
        saveAuthToKeychain()

        if isAuthorized {
            Task {
                await fetchPlaylists()
                startPollingNowPlaying()
            }
        }
    }

    private func handleDeauthorization() {
        isAuthorized = false
        currentTrack = nil
        currentPlaylistURI = nil
        playlists = []
        stopPollingNowPlaying()
        try? KeychainManager.shared.deleteAuthorizationData()
    }

    // MARK: - Keychain Persistence

    private func saveAuthToKeychain() {
        do {
            let data = try JSONEncoder().encode(spotify.authorizationManager)
            try KeychainManager.shared.saveAuthorizationData(data)
        } catch {
            print("Failed to save auth to keychain: \(error)")
        }
    }

    private func restoreAuthFromKeychain() {
        guard let data = try? KeychainManager.shared.loadAuthorizationData() else {
            return
        }

        do {
            let authManager = try JSONDecoder().decode(
                AuthorizationCodeFlowPKCEManager.self,
                from: data
            )
            spotify.authorizationManager = authManager

            // Refresh tokens if needed
            if spotify.authorizationManager.isAuthorized() {
                Task {
                    await refreshTokensIfNeeded()
                }
            }
        } catch {
            print("Failed to restore auth from keychain: \(error)")
            try? KeychainManager.shared.deleteAuthorizationData()
        }
    }

    private func refreshTokensIfNeeded() async {
        do {
            try await spotify.authorizationManager.refreshTokens(
                onlyIfExpired: true
            ).async()
            isAuthorized = spotify.authorizationManager.isAuthorized()

            if isAuthorized {
                await fetchPlaylists()
                startPollingNowPlaying()
            }
        } catch {
            print("Token refresh failed: \(error)")
            handleDeauthorization()
        }
    }

    // MARK: - Authorization Flow

    func authorize() {
        let codeVerifier = String.randomURLSafe(length: 64)
        let codeChallenge = String.makeCodeChallenge(codeVerifier: codeVerifier)
        let state = String.randomURLSafe(length: 32)

        let authURL = spotify.authorizationManager.makeAuthorizationURL(
            redirectURI: Self.redirectURI,
            codeChallenge: codeChallenge,
            state: state,
            scopes: Self.scopes
        )!

        // Store state for validation
        UserDefaults.standard.set(state, forKey: "spotify_auth_state")
        UserDefaults.standard.set(codeVerifier, forKey: "spotify_code_verifier")

        // Open in browser
        NSWorkspace.shared.open(authURL)
    }

    /// Handle the OAuth callback URL
    func handleCallback(url: URL) async {
        guard let state = UserDefaults.standard.string(forKey: "spotify_auth_state"),
              let codeVerifier = UserDefaults.standard.string(forKey: "spotify_code_verifier") else {
            lastError = "Missing auth state"
            return
        }

        // Clean up stored state
        UserDefaults.standard.removeObject(forKey: "spotify_auth_state")
        UserDefaults.standard.removeObject(forKey: "spotify_code_verifier")

        do {
            try await spotify.authorizationManager.requestAccessAndRefreshTokens(
                redirectURIWithQuery: url,
                codeVerifier: codeVerifier,
                state: state
            ).async()

            lastError = nil
        } catch {
            lastError = "Authorization failed: \(error.localizedDescription)"
            print("Authorization error: \(error)")
        }
    }

    func signOut() {
        spotify.authorizationManager.deauthorize()
    }

    // MARK: - Now Playing Polling

    private func startPollingNowPlaying() {
        stopPollingNowPlaying()

        // Poll every 5 seconds
        pollingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                await self?.fetchNowPlaying()
            }
        }

        // Fetch immediately
        Task {
            await fetchNowPlaying()
        }
    }

    private func stopPollingNowPlaying() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }

    // MARK: - API Methods

    func fetchNowPlaying() async {
        guard isAuthorized else { return }

        do {
            let context = try await spotify.currentPlayback().async()

            if let item = context?.item, case .track(let track) = item {
                currentTrack = track

                // Extract playlist URI if playing from a playlist
                if let playlistContext = context?.context,
                   playlistContext.type == .playlist {
                    currentPlaylistURI = playlistContext.uri
                } else {
                    currentPlaylistURI = nil
                }
            } else {
                currentTrack = nil
                currentPlaylistURI = nil
            }
        } catch {
            print("Failed to fetch now playing: \(error)")
        }
    }

    func fetchPlaylists() async {
        guard isAuthorized else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            var allPlaylists: [Playlist<PlaylistItemsReference>] = []
            var offset = 0
            let limit = 50

            while true {
                let page = try await spotify.currentUserPlaylists(
                    limit: limit,
                    offset: offset
                ).async()

                allPlaylists.append(contentsOf: page.items)

                if page.items.count < limit || allPlaylists.count >= page.total {
                    break
                }
                offset += limit
            }

            playlists = allPlaylists
        } catch {
            lastError = "Failed to fetch playlists: \(error.localizedDescription)"
            print("Failed to fetch playlists: \(error)")
        }
    }

    /// Remove current track from its playlist and skip to next
    func removeAndSkip() async -> Bool {
        guard let track = currentTrack,
              let trackURI = track.uri,
              let playlistURI = currentPlaylistURI else {
            lastError = "No track playing from a playlist"
            print("[Iffy] Remove+Skip failed: No track or not from playlist")
            return false
        }

        // Ensure playlist URI is in full format
        let fullPlaylistURI: String
        if playlistURI.hasPrefix("spotify:playlist:") {
            fullPlaylistURI = playlistURI
        } else {
            fullPlaylistURI = "spotify:playlist:\(playlistURI)"
        }
        print("[Iffy] Removing '\(track.name)' (URI: \(trackURI)) from playlist \(fullPlaylistURI)")

        do {
            // Remove track from playlist
            let _ = try await spotify.removeAllOccurrencesFromPlaylist(
                fullPlaylistURI,
                of: [trackURI]
            ).async()

            print("[Iffy] Track removed, skipping to next...")

            // Skip to next
            try await spotify.skipToNext().async()

            // Seek into the next track by configured offset
            let skipOffsetSeconds = UserDefaults.standard.integer(forKey: "skipOffsetSeconds")
            let offsetMs = (skipOffsetSeconds > 0 ? skipOffsetSeconds : 30) * 1000
            try? await Task.sleep(nanoseconds: 300_000_000) // 0.3s delay for skip to register
            try await spotify.seekToPosition(offsetMs).async()

            // Refresh now playing
            try? await Task.sleep(nanoseconds: 300_000_000)
            await fetchNowPlaying()

            lastError = nil
            print("[Iffy] Remove+Skip completed successfully")
            return true
        } catch let spotifyError as SpotifyGeneralError {
            let message = "Spotify error: \(spotifyError)"
            lastError = message
            print("[Iffy] Remove+Skip FAILED: \(spotifyError)")
            print("[Iffy] Full error details: \(String(describing: spotifyError))")
            return false
        } catch {
            lastError = "Failed: \(error.localizedDescription)"
            print("[Iffy] Remove+Skip FAILED: \(error)")
            print("[Iffy] Error type: \(type(of: error))")
            return false
        }
    }

    /// Add current track to a playlist, optionally removing from current playlist
    func addToPlaylist(_ targetPlaylistId: String, removeFromCurrent: Bool) async -> Bool {
        guard let track = currentTrack,
              let trackURI = track.uri else {
            lastError = "No track currently playing"
            return false
        }

        do {
            // Ensure target playlist is in full URI format
            let fullTargetURI: String
            if targetPlaylistId.hasPrefix("spotify:playlist:") {
                fullTargetURI = targetPlaylistId
            } else {
                fullTargetURI = "spotify:playlist:\(targetPlaylistId)"
            }

            // Add to target playlist
            _ = try await spotify.addToPlaylist(
                fullTargetURI,
                uris: [trackURI]
            ).async()

            // Optionally remove from current playlist
            if removeFromCurrent, let currentURI = currentPlaylistURI {
                let fullCurrentURI: String
                if currentURI.hasPrefix("spotify:playlist:") {
                    fullCurrentURI = currentURI
                } else {
                    fullCurrentURI = "spotify:playlist:\(currentURI)"
                }
                _ = try await spotify.removeAllOccurrencesFromPlaylist(
                    fullCurrentURI,
                    of: [trackURI]
                ).async()
                // Skip to next after removing
                try await spotify.skipToNext().async()
            }

            // Refresh now playing
            try? await Task.sleep(nanoseconds: 500_000_000)
            await fetchNowPlaying()

            return true
        } catch {
            lastError = "Failed: \(error.localizedDescription)"
            print("Add to playlist failed: \(error)")
            return false
        }
    }

    /// Skip to next track
    func skipToNext() async {
        do {
            try await spotify.skipToNext().async()
            try? await Task.sleep(nanoseconds: 500_000_000)
            await fetchNowPlaying()
        } catch {
            print("Skip failed: \(error)")
        }
    }
}

// MARK: - Publisher Extension for async/await

extension Publisher where Failure: Error {
    func async() async throws -> Output {
        try await withCheckedThrowingContinuation { continuation in
            var cancellable: AnyCancellable?
            var didReceiveValue = false

            cancellable = self.first().sink(
                receiveCompletion: { completion in
                    switch completion {
                    case .finished:
                        if !didReceiveValue {
                            continuation.resume(throwing: NSError(domain: "SpotifyManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No value received"]))
                        }
                    case .failure(let error):
                        continuation.resume(throwing: error)
                    }
                    cancellable?.cancel()
                },
                receiveValue: { value in
                    didReceiveValue = true
                    continuation.resume(returning: value)
                }
            )
        }
    }
}

extension Publisher where Failure == Never {
    func async() async -> Output {
        await withCheckedContinuation { continuation in
            var cancellable: AnyCancellable?

            cancellable = self.first().sink { value in
                continuation.resume(returning: value)
                cancellable?.cancel()
            }
        }
    }
}
