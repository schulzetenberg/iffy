// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Iffy",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "Iffy", targets: ["Iffy"])
    ],
    dependencies: [
        .package(url: "https://github.com/Peter-Schorn/SpotifyAPI.git", from: "3.0.0"),
        .package(url: "https://github.com/sindresorhus/KeyboardShortcuts.git", from: "2.0.0")
    ],
    targets: [
        .executableTarget(
            name: "Iffy",
            dependencies: [
                .product(name: "SpotifyAPI", package: "SpotifyAPI"),
                .product(name: "KeyboardShortcuts", package: "KeyboardShortcuts")
            ],
            path: "Sources/Iffy",
            exclude: [
                "Resources/Iffy.entitlements",
                "Resources/Info.plist",
                "Resources/Secrets.example.plist"
            ],
            resources: [
                .process("Resources/Secrets.plist")
            ]
        )
    ]
)
