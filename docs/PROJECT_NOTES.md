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
- 2026-05-17 — **unreleased**: contacts + foldable Contacts tab. The
  tab now hosts two foldable sections (`Contacts` for saved peers,
  online or offline; `Peers` for live LAN discoveries that are not
  yet saved). Long-press on any row opens a Material 3 `DropdownMenu`
  with Add/Remove and Info; the Info dialog renders avatar, current
  address, online or relative last-seen, first-seen, full UUID, all
  previously-observed addresses, and all previously-observed
  nicknames. Contacts persist by UUID across IP changes and app
  restarts. Room v8 with `MIGRATION_7_8`: new column
  `peers.is_contact INTEGER NOT NULL DEFAULT 0`; new
  `peer_addresses(uuid, host, port, first_seen_at, last_seen_at)`
  and `peer_nicknames(uuid, nickname, first_seen_at, last_seen_at)`
  history tables with composite PKs, backfilled from the existing
  `peers` row. History rows are written from inside
  `PeerRepository.recordSeen` via `PeerHistoryRecorder`. New
  `AddToContactsUseCase` / `RemoveFromContactsUseCase` under
  `domain/contacts/`. UI state moves to a sealed `ContactsUiState`
  (`Loading` / `Ready(contacts, peers)`). History tables are pruned
  to the 10 most-recent rows per peer on every `recordSeen` so
  storage stays bounded under DHCP churn.
- 2026-05-17 — **unreleased**: explicit Exit. The persistent
  notification posts an "Exit" action; the About screen carries an
  "Exit OSPChat" button with a confirmation dialog. Both routes
  funnel through `MainActivity.ACTION_EXIT`, which stops
  `DiscoveryForegroundService` and calls `finishAndRemoveTask()`.
- 2026-05-18 — **unreleased**: `ospchat-shared` is now consumed from
  the GitHub Packages Maven registry (`tb0hdan/ospchat-shared` tag
  `v0.1.0`) rather than `mavenLocal()`. Local developers and CI must
  authenticate with a token carrying `read:packages` — see the README
  for the one-time setup. Version pinned in
  `gradle/libs.versions.toml` under `ospchatShared`.
- 2026-05-19 — **unreleased**: followup to the port-stability fix.
  `ospchat-shared:0.1.1` introduced a peer-list flicker regression
  caused by `MessageClient` invoking `DiscoveryRepository.forgetPeer`
  on TCP connect failures, where Android's `NsdPeerDiscovery.forgetPeer`
  bounced the whole NSD discovery — `onServiceLost` for every peer,
  then `onServiceFound` for every peer, then `DiscoveryForegroundService.peerSyncJob`
  re-firing `PeerAvatarSync` + `GroupSyncer` on each "newly arrived"
  peer, with any failed background call restarting the cycle. Fixed
  in `ospchat-shared:0.1.2`: `forgetPeer` is now surgical (only
  re-resolves the targeted peer via the existing resolve queue —
  no `stopServiceDiscovery`), and `MessageClient` per-method takes a
  `rediscover` flag that all background callers pass as `false`
  (avatar sync, group sync, info refresh, attachment download). The
  original port-restart symmetry — user-initiated sends still
  rediscover on connect failure — is preserved.
- 2026-05-19 — **unreleased**: fixed one-way messaging after the peer's
  desktop app restarts. Symptom: desktop restart picks a new ephemeral
  port; Android NSD doesn't surface port-only changes for an existing
  service name (no `onServiceFound` / `onServiceLost` fires), so the
  framework's cached resolution kept addressing the dead port and
  Android→Desktop POSTs silently failed. Fixes (in `ospchat-shared`):
  (a) `IdentityRepository.lastServerPort` persists the embedded
  server's bound port; `DiscoveryForegroundService` reads it on boot
  and passes `preferredPort` to `MessageServer.start`, which tries
  that port first and falls back to ephemeral on `EADDRINUSE`. Means
  an Android restart also keeps its port stable across reboots.
  (b) `MessageClient` wraps every per-peer call in a one-shot
  rediscover-and-retry: on a TCP connect failure it calls
  `DiscoveryRepository.forgetPeer(uuid)` (the Android implementation
  drops the peer from `_peers` + `nameToUuid` and bounces
  `nsdManager.stopServiceDiscovery` → `discoverServices` to force the
  framework to re-emit `onServiceFound` for every peer), waits ≤3 s
  for a snapshot entry with a different host:port, then retries. No
  OpenAPI changes (wire compatible).
- 2026-05-20 — **unreleased**: group chat reactions. Long-press a group
  bubble (own or peer's) opens the emoji picker; the picked emoji
  becomes the user's reaction on that message. Chips render inside the
  bubble under the body. Display rule per spec: 1–2 reacters with the
  same emoji → tiny initials avatars (16 dp, oldest-first by
  `reactedAt`); 3+ → numeric count. Tap toggles (matches DM semantics).
  Reuses the existing `reactions` Room table — no migration needed.
  New DAO query `ReactionDao.observeForGroup(groupId)` joins through
  `group_messages.group_id`. Mesh fan-out via the shared
  `ReactionRepository.reactToGroup(...)`. Wire:
  `POST /v1/reactions` gains a nullable `groupId`; receivers validate
  the sender against group membership when set. Catch-up: extended
  `GroupSyncPayloadDto.reactions` carries every current reaction in the
  group; `GroupSyncer` packs in `buildResponse` and upserts in
  `applyPayload`. OpenAPI 0.9.0.
- 2026-05-17 — **unreleased**: group chats + broadcast channels.
  Groups tab now has two foldable sections (Group chats / Broadcast
  channels). FAB → new-group sheet. Long-press menu offers
  Add/Remove members (creator), Leave (members), and Info. Posting
  is **mesh-direct** between members — the creator is not a routing
  hub, so the group keeps working when they're offline.
  **History sync** runs on every NSD re-discovery and exchanges
  cursors with shared-group peers, so any member can serve catch-up
  history. Room v9 with `MIGRATION_8_9` adds `groups`,
  `group_members`, `group_messages` tables. OpenAPI 0.8.0 adds
  `/v1/groups/{messages,membership,sync,leave}`. Trust model: only
  the creator's snapshot can change membership; broadcast channels
  reject posts from non-creators.

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
