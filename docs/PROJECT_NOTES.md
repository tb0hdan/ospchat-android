# OSPChat — Project Notes

## Overview

OSPChat is an open-source chat application for Android, written in Kotlin. The
long-term goal is decentralized peer-to-peer messaging; the v0.1 milestone is
**LAN peer discovery only** — devices on the same Wi-Fi network see each other
in a live list. Actual messaging is intentionally deferred.

## Scope of v0.1

In scope:

- Project scaffolding (Gradle, Android manifest, Compose UI shell, Hilt DI).
- First-run nickname prompt, persisted via DataStore. A stable UUID is also
  generated under the hood for peer dedup.
- LAN peer discovery via Android NSD (mDNS/DNS-SD), service type
  `_ospchat._tcp.`. Each device registers an ephemeral TCP port (placeholder
  for future messaging) and publishes its UUID via a TXT attribute.
- A foreground service (`DiscoveryForegroundService`, `connectedDevice` type)
  that owns the NSD registration + discovery lifecycle so peer state survives
  brief UI pauses.
- A `PeersScreen` listing discovered peers with nickname and `host:port`.

Out of scope (deferred):

- Sending/receiving messages over the placeholder TCP socket.
- Encryption / authenticated handshake.
- Peer history persistence.
- Internet / WAN peer discovery.

## Tech Choices

| Decision            | Choice                                           |
| ------------------- | ------------------------------------------------ |
| Min SDK             | 26 (Android 8.0)                                 |
| Target / Compile    | 35 (Android 15)                                  |
| Language            | Kotlin 2.0.x                                     |
| UI                  | Jetpack Compose + Material 3                     |
| Architecture        | MVVM with `ViewModel` + `StateFlow`              |
| DI                  | Hilt (KSP)                                       |
| Persistence         | DataStore Preferences (nickname + uuid)          |
| Peer discovery      | `android.net.nsd.NsdManager` (no extra deps)     |
| Service type        | `_ospchat._tcp.` with TXT `uuid=<uuid>`          |
| Background strategy | Foreground service, type `connectedDevice`       |
| Application ID      | `com.ospchat.android`                            |

## Architecture (v0.1)

```
            ┌─────────────────────────────────────────────────────┐
            │                  MainActivity (Compose)             │
            │                                                     │
            │   ┌──────────────┐        ┌─────────────────────┐  │
            │   │NicknameScreen│   or   │   PeersScreen       │  │
            │   └──────┬───────┘        └──────────┬──────────┘  │
            └──────────┼───────────────────────────┼─────────────┘
                       │                           │
              NicknameViewModel              PeersViewModel
                       │                           │
                       ▼                           ▼
            ┌─────────────────────┐    ┌─────────────────────────┐
            │ IdentityRepository  │    │ DiscoveryRepository      │
            │ (DataStore-backed)  │    │   (Hilt @Singleton)      │
            └─────────────────────┘    │   wraps NsdManager,      │
                                       │   exposes peers Flow.    │
                                       └──────────▲───────────────┘
                                                  │ start/stop
                                                  │
                                ┌─────────────────┴─────────────────┐
                                │ DiscoveryForegroundService         │
                                │  • acquires MulticastLock          │
                                │  • posts ongoing notification      │
                                │  • drives DiscoveryRepository      │
                                └────────────────────────────────────┘
```

The foreground service and the `PeersViewModel` inject the **same singleton**
`DiscoveryRepository`. The service writes; the ViewModel reads. There is no
cross-process IPC.

## Repository Layout

```
ospchat-android/
├── docs/                                  CLAUDE.md-required project docs
├── gradle/                                Version catalog + wrapper
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/ospchat/android/
│       │   ├── OSPChatApp.kt              @HiltAndroidApp
│       │   ├── MainActivity.kt
│       │   ├── di/AppModule.kt
│       │   ├── data/
│       │   │   ├── identity/IdentityRepository.kt
│       │   │   └── discovery/
│       │   │       ├── Peer.kt
│       │   │       ├── NsdPeerDiscovery.kt
│       │   │       └── DiscoveryRepository.kt
│       │   ├── service/DiscoveryForegroundService.kt
│       │   └── ui/
│       │       ├── theme/{Color,Theme,Type}.kt
│       │       ├── nickname/...
│       │       └── peers/...
│       └── res/...
├── settings.gradle.kts, build.gradle.kts  Root Gradle (Kotlin DSL)
├── Makefile, README.md, VERSION, .gitignore
└── CLAUDE.md, AGENTS.md, GEMINI.md
```

## Current Status

- 2026-05-16 — Initial scaffold + LAN peer discovery feature created.
- 2026-05-16 — v0.2: REST messaging over HTTP. Each peer hosts an embedded
  Ktor server on the port it advertises via NSD; `ChatScreen` lets users
  exchange messages persisted in a Room database. Wire protocol documented
  in `docs/api/openapi.yaml`.
- 2026-05-16 — v0.1.3: bundled emoji font + emoji picker.
- 2026-05-16 — v0.1.4: fix for service self-stopping on `PeersScreen`
  re-entry (peers losing each other after navigating back).
- 2026-05-16 — v0.1.5: peer persistence by UUID across IP changes; online
  status dot on the peer list.
- 2026-05-16 — v0.1.6: outbound message status pipeline
  (Sending → Delivered → Read, Failed); `POST /v1/read-receipts`;
  tap-to-retry on failed bubbles.
- 2026-05-16 — v0.1.7: notifications (channel `ospchat_messages`,
  suppressed during DND), unread badge on the peer list,
  `ospchat://chat/{uuid}` deep links.
- 2026-05-16 — v0.1.8: bottom-tab shell (Contacts / Groups / About).
  About hosts version, website link, and a nickname-change setting that
  bounces the discovery service so peers see the new name.
- 2026-05-16 — **unreleased**: user avatars. Initials avatar
  (deterministic colored circle, two-letter nickname-derived label;
  regenerates on rename) and a custom-avatar pickable from About >
  Settings. Avatars rendered on the left of each Contacts row and next
  to the chat top-bar title. Wire: `Info.avatarHash` + new
  `GET /v1/avatar`; receivers cache and refresh on hash change.
  `PeerAvatarSync` runs on every "new peer in NSD snapshot" transition
  in `DiscoveryForegroundService.peerSyncJob`. Room v6 adds
  `peers.avatar_hash` + `avatar_local_path`. OpenAPI 0.5.0.
- 2026-05-16 — **v0.1.9 (released)**: image attachments. Pick from the
  gallery or take a fresh photo via the system camera (composer `+` →
  bottom sheet). Compressor reads source EXIF, applies the matching
  `Matrix` rotation, scales to a 1920 px longest edge, and re-encodes
  JPEG q85; bytes persist under `filesDir/attachments/`. Two-phase wire
  protocol: `POST /v1/messages` carries `{mimeType, sizeBytes, width,
  height}` (post-rotation), `GET /v1/attachments/{messageId}` on the
  sender streams the binary. Coil-rendered bubbles with pinch-zoom
  full-screen viewer. Compression now runs on `Dispatchers.IO` inside a
  `try/catch(Throwable)` so OOM / undecodable URIs surface as
  `Result.failure` instead of killing the process.
- 2026-05-16 — **v0.1.13 (released)**: message reactions. Long-press a
  chat bubble (own or peer) to open an emoji picker; the selected emoji
  becomes the user's reaction on that message (one reaction per user
  per message — a fresh pick replaces the previous one). Chips sit
  inside the bubble directly under the body, so the bubble grows to
  contain them; tertiary-container tint for the user's own reactions,
  neutral surface tone for the rest. Tapping a chip toggles. Room v7
  adds the `reactions` table with composite PK `(message_id, from_uuid)`
  via `MIGRATION_6_7`. Wire: `POST /v1/reactions` (`emoji == null` =
  remove). OpenAPI 0.7.0.

## Known Limitations

- The Gradle wrapper JAR is **not** committed; the developer must run
  `gradle wrapper --gradle-version 8.10.2` once (or open in Android Studio) to
  generate `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.
  See `README.md`.
- On API 26–33 some Wi-Fi networks restrict mDNS multicast; the multicast lock
  acquired by the foreground service is the standard mitigation.
- Discovery is single-network. Hotspot / dual-Wi-Fi scenarios are untested.
- No tests yet — adding a unit-test smoke covering `NsdPeerDiscovery` peer
  bookkeeping is the suggested next step.
- CI (`.github/workflows/ci.yml`) runs `make ktlint` + `make build` on
  every branch push and PR; tag pushes additionally upload the debug APK
  as a workflow artifact.

## Suggested Next Steps

1. Group chats (multi-peer conversations) — Groups tab is currently a
   placeholder.
2. Encrypted handshake (Noise / X3DH-style) so trust isn't pure TOFU.
3. Message editing / deletion with tombstones (since peers may be
   offline when an edit is issued).
4. Migration tests for the Room schema (`MigrationTestHelper`).
5. Unit-test smoke covering `NsdPeerDiscovery` peer bookkeeping.
