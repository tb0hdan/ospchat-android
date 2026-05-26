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

- 2026-05-26 — **unreleased**: **Phase 5 multi-network bridging (PR 3:
  relayed call signaling).** A1→D→A2 smoke test surfaced that PR 2's
  TURN media relay alone couldn't bridge cross-network calls because
  the SDP signaling DTOs (`/v1/call/{offer,answer,ice,hangup}`) had no
  bridging fields and went peer-to-peer. PR 3 extends the same
  `toUuid` / `via` / `hopTtl` pattern phase 4 uses for message DTOs to
  the call DTOs. `CallRepository` resolves a route via `PeerRouter`
  before creating the session, stamps `toUuid` on outbound offer /
  answer / ICE / hangup DTOs, and routes replies through the same
  bridge that delivered the offer. Hilt `provideCallRepository` adds
  the `peerRouter` injection. No new permissions or manifest changes.
  OpenAPI bumped to 0.14.0 in shared. **Next smoke test** is the
  re-run of A1→D→A2 with all four PR 1 / 1.5 / 2 / 3 deltas applied.
- 2026-05-26 — **unreleased**: **Phase 3 multi-network bridging
  (PR 1.5 + PR 2: consumer wiring + wire protocol).** Hilt
  `SharedModule.provideOspChatTurnServer` provides the singleton;
  `DiscoveryForegroundService` starts/stops it tied to the
  `relayEnabled` flag and the foreground service lifecycle. Hilt
  `provideCallRepository` adds the `relayBridgeRegistry` plumbing for
  TURN cred prefetch; Hilt `provideMessageServer` adds
  `turnCredentialService = turnServer` so `/v1/call/relay-cred` can
  issue creds. `AndroidAudioCallSession(factory, iceServers)` —
  `PeerConnection.RTCConfiguration` populated with TURN entries when
  present. About-screen toggle copy updated to "Relay for contacts
  (messages + voice)". Wire-protocol changes (signed call DTOs,
  `/v1/call/relay-cred`, new error codes, OpenAPI 0.13.0) all in
  shared. **Phase 3 complete end-to-end.** Smoke test against a desktop
  acting as TURN bridge is the natural next step.
- 2026-05-26 — **unreleased**: **Phase 3 multi-network bridging
  (PR 1: shared TURN foundation).** Pure-Kotlin RFC 5766 subset
  added to `ospchat-shared` under `com.ospchat.shared.turn.*`
  (STUN/TURN codec, allocation state machine, pure handlers,
  `OspChatTurnServer` wrapping `java.net.DatagramSocket`). Android
  consumes the same actual implementation (duplicated identically
  in `desktopMain` + `androidMain` per the `bouncycastle` pattern,
  no intermediate `jvmMain`). 33 unit tests pass in commonTest.
  Existing `relayEnabled` flag from phase 4 will gate both message-
  DTO forwarding (phase 4) and TURN voice relay (phase 3) — one
  toggle, two purposes. PR 1.5 (consumer wiring) will: (1)
  `SharedModule` provide `OspChatTurnServer` as a Hilt
  `@Singleton`; (2) `DiscoveryForegroundService.onStartCommand`
  call `turnServer.start()` when `relayEnabled=true`, and
  `onDestroy` call `stop()`; (3) About-screen toggle label updated
  in PR 2 to read "messages + voice". No new Android permissions
  needed — the TURN listener uses the existing INTERNET
  permission inside the already-declared `connectedDevice`
  foreground service. PR 2 (deferred) adds `/v1/call/relay-cred`,
  signed call DTOs, and `iceServers` threading into
  `AndroidAudioCallSession`.
- 2026-05-25 — **unreleased**: **Phase 4 consumer-side shared
  foundation.** `ospchat-shared` gained `PeerRouter`,
  `RelayBridgeRegistry`, gossip-pubkey lookup in `MessageRoutes`, and
  `MessageRepository.sendToUuid` + auto-record-gossip-sender on
  `receive`. Android-side wiring still pending: Hilt provider for
  `GossipedPeerStore` / `RelayBridgeRegistry` / `PeerRouter`; pass
  them into `PeerAvatarSync`, `MessageRepository`, and the
  `MessageRoutes` install; add a Settings toggle for the relay
  opt-in; surface gossiped peers in the contact list so the user can
  initiate a cross-LAN DM.
- 2026-05-25 — **unreleased**: **Phase 4 multi-network bridging
  (server-side foundation).** Wire format for message-level relay
  through multi-homed peers shipped in `ospchat-shared`. Seven signed
  DTOs gained nullable `toUuid` (signed) + `via` + `hopTtl`
  (unsigned). `/v1/info` gossips peers + advertises `relayEnabled`.
  `MessageRoutes` forwards when `toUuid != self`. New
  `GossipedPeerStore` populated by `PeerAvatarSync`.
  `IdentityRepository.relayEnabledFlow` for the opt-in toggle.
  Consumer-side wiring still pending — Android needs (a) the relay
  opt-in UI surface in Settings, (b) outbound MessageClient changes
  to consult `GossipedPeerStore` and route through a bridge when the
  target isn't directly discovered. OpenAPI 0.12.0;
  `docs/SECURITY.md` F10 documents the relay trust model.
- 2026-05-25 — **unreleased**: **Phase 2b consumer wiring verified
  (desktop side).** Linups desktop log shows
  `pk=<16-chars> persistedPins=1` at startup — phase 2a pubkey
  populated AND the persistent TOFU pin map loaded from Room before
  discovery started. Android-side will exercise the same path once
  rebuilt against `ospchat-shared:0.2.8` (consumer wiring landed in
  `DiscoveryForegroundService`).
- 2026-05-25 — **unreleased**: **Phase 2b multi-network bridging**
  (signed DTOs + persistent pubkey pinning) shipped in
  `ospchat-shared`. Seven DTOs (`IncomingMessageDto`, `ReadReceiptDto`,
  `ReactionDto`, `GroupSnapshotDto`, `GroupMessageDto`,
  `GroupSyncRequestDto`, `GroupLeaveDto`) gained nullable
  `signedAt` / `signature` fields with per-DTO canonical payload via
  `SignaturePayloadBuilder`. `MessageClient` signs outbound;
  `MessageRoutes` verifies inbound with a ±5-minute replay window.
  Persistent pubkey pinning: Room migration v10 → v11 adds the
  `peers.pub_key` column; `DiscoveryRepository.preloadPinnedPubkeys()`
  warms the discovery service at startup so F9 protection survives a
  restart. `DiscoveryForegroundService` now `@Inject`s `PeerDao` and
  calls `loadPinnedPubkeys()` → `preloadPinnedPubkeys(...)` →
  `start(...)` in order; startup log includes
  `persistedPins=<count>` for runtime verification.
  Tolerate-unsigned rollout mode;
  flip to strict in a follow-up release. `docs/SECURITY.md` F9 marked
  **FULLY MITIGATED**. OpenAPI bumped to 0.11.0.
- 2026-05-25 — **unreleased**: **Phase 1 + 2a verified on real LAN.**
  After consumers picked up `ospchat-shared:0.2.8` (via mavenLocal),
  bidirectional voice calls Android ↔ Desktop and 1:1 text chat both
  work end-to-end. The "Android cannot see desktop" regression caused
  by the legacy single-interface JmDNS bind is resolved. No F9
  pkh-mismatch false positives — every legitimate peer presents one
  pubkey across all its addresses, so the multi-NIC merge path stays
  green. Android-side wiring is in `DiscoveryForegroundService`:
  `identityRepository.ensureSigningKeyPair()` → b64 →
  `messageServer.start(..., publicKeyB64=...)` and
  `discoveryRepository.start(..., publicKeyB64=...)`.
- 2026-05-25 — **unreleased**: **Phase 2a multi-network bridging**
  (identity infrastructure) landed in `ospchat-shared`. Per-install
  Ed25519 keypair (`IdentityRepository.ensureSigningKeyPair`, BC-backed
  via the new `SigningCrypto` expect/actual). Pubkey advertised via
  the `pk=<b64>` mDNS TXT attribute and `GET /v1/info`. F9 hijack
  rejection restored in `protectedInsert` via TOFU pubkey pinning
  (in-memory; phase 2b will persist). Android-side change is the
  `NsdPeerDiscovery.start` signature gaining `publicKeyB64: String?`
  and `handleResolved` reading `pk=` from the TXT attributes. No DTO
  signatures yet (phase 2b). See `docs/SECURITY.md` F9.
- 2026-05-25 — **unreleased**: **Phase 1 multi-network bridging**
  shipped in `ospchat-shared` (see "Suggested Next Steps" item 6 and
  `ospchat-desktop/docs/PROJECT_NOTES.md` item 7 for the four-phase
  plan). Android-side change is in `NsdPeerDiscovery.handleResolved`:
  feeds an `Endpoint` into the new candidate-merging `protectedInsert`
  instead of constructing a `Peer` directly. `Peer` now carries
  `List<Endpoint>` candidates sorted by RFC1918 > CGNAT > public.
  `MessageClient` walks candidates on TCP-level failures before
  `forgetPeer`-and-retry; `MessageRoutes` source-IP trust matches
  against any candidate. F9 hijack rejection is relaxed pending
  phase 2 (signed advertisements) — see `docs/SECURITY.md` F9. No
  wire / OpenAPI change. Android `NsdManager` enumerates its own
  interfaces, so the desktop's per-interface JmDNS work doesn't
  apply here.
- 2026-05-22 — **unreleased**: Seed Mode "Serving at" line folded
  into the Wi-Fi hotspot status. The separate monospace
  `Serving at <url>` label above the QR is gone; the hotspot line
  now shows `Wi-Fi hotspot: 192.168.x.x` when the server is
  stopped and flips to `Wi-Fi hotspot: http://192.168.x.x:8080`
  when running, then back. Implementation just picks
  `serverUrl ?: hotspotIp` as the substitution for the existing
  `seed_mode_hotspot_active` format string. Removed the unused
  `seed_mode_server_running` string and Monospace/SemiBold
  imports.
- 2026-05-22 — **unreleased**: Seed Mode QR slot centered in both
  states. The 240×240 white-bg box is now wrapped in an outer
  `Box(fillMaxWidth, contentAlignment = Alignment.Center)`, so the
  placeholder text and the rendered QR occupy the exact same
  screen position. Previous version used a per-call
  `Modifier.align(Alignment.CenterHorizontally)` that read as
  centered for the image (it filled the inner box) but
  inconsistently positioned for the small placeholder text.
- 2026-05-22 — **unreleased**: Wi-Fi hotspot status in the Seed
  Mode hotspot card collapsed from two lines (header + body) to a
  single `titleSmall` line: `Wi-Fi hotspot: 192.168.x.x` when
  detected, `Wi-Fi hotspot: not detected` otherwise. The "Open
  Wi-Fi settings" button below the inactive line is unchanged.
  Removed `seed_mode_hotspot_header`; reworded
  `seed_mode_hotspot_active` / `seed_mode_hotspot_inactive`.
- 2026-05-22 — **unreleased**: Seed Mode Android download filename
  fix. The server used to advertise `base.apk` in
  `Content-Disposition` because for the `SelfApk` package the
  resolved `File` was `Context.applicationInfo.sourceDir`, which on
  Android is `/data/app/.../base.apk`. `SeedRepository.servedFileFor`
  now returns a `ServedFile` pair (file + download name); the
  SelfApk branch composes `ospchat-android-<version>.apk` from
  `R.string.app_version_name`. `SeedServer` uses the paired
  `downloadName` for both `Content-Disposition` and the landing-page
  meta label. Manifest's `SeedPackageInfo.fileName` for SelfApk now
  carries the same versioned name so the Seed Mode checklist UI
  matches. `docs/api/seed.yaml` example updated.
- 2026-05-22 — **unreleased**: Seed Mode download progress bar
  no longer renders a disconnected dot at the trailing end while a
  download is in progress. Material 3's `LinearProgressIndicator`
  defaults to drawing a "stop indicator" (a filled circle at the
  end of the track per spec) whenever `progress < 1f`. Suppressed
  in `PackageRow` by passing `drawStopIndicator = {}`.
- 2026-05-22 — **unreleased**: Seed Mode QR slot folded into the
  hotspot card. The separate QR/serving-URL card under Start/Stop is
  gone; the existing Wi-Fi-hotspot card now hosts a fixed-size
  (`QR_DISPLAY_SIZE = 240 dp`) QR slot at the bottom. Stopped →
  placeholder text "Start server to get QR" on a white background.
  URL-known-but-bitmap-not-yet → centered `CircularProgressIndicator`
  in the slot. Running → actual QR + "Serving at <url>" line above
  the slot. Slot keeps its footprint across states so nothing
  reflows on Start/Stop. New string `seed_mode_qr_placeholder`;
  `ServerInfoCard` removed.
- 2026-05-22 — **unreleased**: Seed Mode "Clear cache" button. New
  error-tone outlined button under "Download Selected" on the Seed
  Mode screen wipes every cached desktop installer under
  `filesDir/seed/<id>/` after a confirmation dialog; the built-in
  Android APK (served from `Context.applicationInfo.sourceDir`) is
  untouched. Plumbed as `SeedCache.clearAll()` →
  `SeedRepository.clearCache()` (IO dispatcher) →
  `SeedModeViewModel.clearCache()` (then `refreshManifest` so every
  row flips back to "Not downloaded"). Button is disabled while a
  batch download is running, while the seed server is running (so an
  in-flight peer GET can't race a wipe), and when nothing
  non-builtin is cached.
- 2026-05-22 — **unreleased**: Seed Mode row status fix. Per-row
  status in the Seed Mode checklist used to revert from
  `Downloading N%` to `Not downloaded` the moment an individual
  download finished, only flipping to `Cached` after the whole
  batch completed. `SeedModeViewModel.downloadSelected` was
  removing the progress entry after each successful
  `downloadPackage`, but `latestManifest` (the source of
  `SeedPackageRow.isCached`) was only refreshed in the `finally`
  block. Fix: new `markCached(id)` helper updates both
  `latestManifest` and the visible state row's `isCached` /
  `downloadProgress` atomically on success, so the label flips to
  `Cached` the instant each artifact finishes. End-of-batch
  `refreshManifest()` retained as a confirmation pass.
- 2026-05-22 — **unreleased**: Seed Mode. A phone running OSPChat can act
  as a bootstrap node for a completely offline mesh. About → Seed Mode
  opens a new full-screen route that detects the Wi-Fi hotspot
  interface (user enables it manually via system settings; the screen
  surfaces an "Open Wi-Fi settings" shortcut and polls
  `NetworkInterface.getNetworkInterfaces()` every 2 s for the first
  up, non-loopback, site-local IPv4 address), lets the user
  selectively download desktop installers from the
  `tb0hdan/ospchat-desktop` GitHub latest release into
  `filesDir/seed/<id>/`, and starts an embedded Ktor CIO server on
  `0.0.0.0:8080` bound to the hotspot interface. The Android APK
  served by the seed is the phone's own installed binary
  (`Context.applicationInfo.sourceDir`) — always available, no
  download required. Routes are generated from a single catalog list
  in `seed/catalog/SeedCatalog.kt`: `GET /` (HTML landing page with
  User-Agent sniffing), `GET /health`, and one
  `GET /download/{id}` per descriptor. `respondFile` + the new
  `PartialContent` + `AutoHeadResponse` plugins give browsers Range-
  request resume. QR rendering via `com.google.zxing:core:3.5.3`.
  New `SeedForegroundService` (type `dataSync`, paired
  `FOREGROUND_SERVICE_DATA_SYNC` permission) keeps the server alive
  when the screen sleeps. Runs independently of
  `DiscoveryForegroundService`; both can be active at the same time.
  A `SeedServerState` `@Singleton` shared between the service and the
  view model lets the screen render the correct Start/Stop state and
  QR when the user navigates back into Seed Mode while the server is
  already running. Wire protocol unchanged; the public-facing HTTP
  endpoints are documented in `docs/api/seed.yaml`, distinct from
  the peer-protocol `openapi.yaml`.
- 2026-05-21 — **unreleased**: fixed Android → Android calls stuck at
  `Connecting…` (ICE never leaves CHECKING, caller hits 30 s
  `NO_ANSWER`). Root cause in `ospchat-shared`'s
  `CallRepository.applyIce`: when an ICE candidate POST arrived before
  the matching offer POST was processed, it hit
  `pendingOffers[callId] == null` and was dropped. The caller fires its
  first local UDP host candidate the instant `setLocalDescription`
  returns inside `createOffer` — fast enough that on the wire the
  resulting `/v1/call/ice` POST overtakes the still-in-flight
  `/v1/call/offer` POST, especially while `applyOffer` is busy doing
  the DB upsert + ringtone + Notification.MediaSession setup (~190 ms
  on the test handset). On Android↔Android both sides race the same
  way, so the only UDP host candidate gets dropped and the remaining
  TCP-active IPv6 host candidates libwebrtc emits at port 9 are
  unconnectable without a TCP-passive listener — ICE pairs never
  succeed. A→Desktop and Desktop→A had been "fixed" earlier because
  Desktop's gather is slow enough to land late UDP candidates after
  `applyOffer`. Fix in shared: `CallRepository` grows a `preOfferIce`
  buffer keyed by `callId` (sender-tagged, 64-candidate cap,
  `ringTimeoutMs` TTL). `applyIce` stashes candidates here when no
  pending offer exists; `applyOffer` drains them into the new
  `PendingOffer.pendingIce` (sender-validated). The rest of the
  pipeline (existing `acceptCall` → `drain remote ICE` path)
  unchanged. BUSY-rejected offers also clear the buffer immediately.
  Wire / OpenAPI unchanged. Will ship in the next `ospchat-shared`
  release; Android consumes via a single version bump in
  `gradle/libs.versions.toml`.
- 2026-05-21 — **unreleased**: fixed crash on accepting incoming calls
  without `RECORD_AUDIO`. The outgoing-call tap in `ChatScreen` already
  gated on the runtime perm, but `IncomingCallOverlay.onAccept` called
  `callRepository.acceptCall` directly — so the CONNECTING transition
  fired `CallServiceController` → `CallForegroundService.start` →
  `startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)` with no
  `RECORD_AUDIO` held, which the framework rejects with
  `SecurityException` on API 31+. Crashed the process on every accept
  for users who hadn't granted the perm yet, and crash-looped while
  the active call sat in CONNECTING. Two-layer fix:
  (a) `IncomingCallOverlay` now requests `RECORD_AUDIO` on accept,
  accepts only on grant, auto-declines on denial via the existing
  hangup path. (b) `CallForegroundService.start` and `onStartCommand`
  both short-circuit when the perm is missing (defensive against
  mid-call revoke from Settings) — `start` skips
  `startForegroundService` so the 5-second startForeground deadline
  is never armed, `onStartCommand` `stopSelf`s if dispatched anyway.
  Wire / OpenAPI unchanged.
- 2026-05-21 — **unreleased**: bumped `ospchat-shared` from `0.2.2` to
  `0.2.4` in `gradle/libs.versions.toml`. Brings in the detailed ICE /
  call-signaling logging added in 0.2.3 (every offer / answer / ICE
  candidate plus call state transitions logged with the `callId` for
  cross-side correlation) and the 0.2.4 release on top. Wire / OpenAPI
  unchanged.
- 2026-05-21 — **unreleased**: fixed Android → Desktop calls hanging at
  `Connecting…` (the reverse direction of the 2026-05-20 fix below).
  Symptom from Desktop's log: `bufferedIce=0` on accept, no `applyIce ←`
  arrived from Android, the session sat in `NEGOTIATING` until Android
  hung up. Root cause in `media/AndroidAudioCallSession.kt` (and the
  mirror `JvmAudioCallSession.kt` in the desktop project): the local-ICE
  `MutableSharedFlow` was built with `replay = 0` and
  `extraBufferCapacity = 64`. With `replay = 0`, a `tryEmit` against a
  flow with zero subscribers is silently discarded —
  `extraBufferCapacity` only buffers for *existing slow subscribers*.
  libwebrtc fires `onIceCandidate` the moment `setLocalDescription`
  returns inside `createOffer` / `acceptOffer`, before
  `CallRepository.bindSession`'s `scope.launch { collect { … } }`
  schedules its collector. Android's 1-2-interface fast gather loses
  every candidate; Desktop's many-interface slow gather loses early
  ones but a few late candidates survive — hence the prior
  Desktop → Android-works-but-Android → Desktop-fails asymmetry. Fix:
  switch to `replay = 64` so emissions are preserved for any future
  subscriber. No wire / OpenAPI change.
- 2026-05-20 — **unreleased**: fixed Desktop → Android calls hanging at
  `Connecting…`. Symptom: Android logcat showed
  `D/JvmAudioCallSession: ICE connection state: CHECKING` and the call
  never reached CONNECTED, eventually NO_ANSWER-ing at 30 s. The reverse
  direction (Android → Desktop) worked. Root cause in
  `ospchat-shared`'s `CallRepository.applyIce`: the callee dropped every
  ICE candidate that arrived before the user tapped Accept
  (`val active = current ?: return`; `current` only gets created in
  `acceptCall`). A multi-interface desktop JVM (loopback + eth + wifi +
  docker/vpn) trickles its entire host-candidate set the moment
  `setLocalDescription` returns inside `createOffer` — well before the
  Android user accepts — so Android ended up with the answer SDP and
  zero remote candidates and Desktop's STUN binding requests had no
  return path. ICE pairs stayed CHECKING one-way forever. The reverse
  direction usually worked because Android typically has only one
  wifi interface and Desktop's user accepts fast enough that some
  candidates squeak through after `current` is set. Fix in shared:
  `PendingOffer` grows a `pendingIce` buffer; `applyIce` appends to
  it while ringing; `acceptCall` drains the buffer into the session
  right after `acceptOffer` (which sets the remote description, so
  libwebrtc is ready to accept them). Wire-compatible — no OpenAPI
  change.
- 2026-05-20 — **Audio voice calls (phase 1, unreleased).** One-to-one LAN
  voice calls between OSPChat peers, audio only. Tap the new phone icon in
  any chat's `TopAppBar` to call; an `IncomingCallDialog` overlay (above
  the NavHost so it survives navigation) rings via `RingtoneManager` on
  the callee side. Accept opens a full-screen `CallScreen` (mute + hangup);
  decline POSTs `/v1/call/hangup`. Both sides honour a 30s no-answer ring
  timeout. Second incoming call during an active call is auto-rejected
  with `BUSY`. New `CallForegroundService`
  (`foregroundServiceType="microphone"`, `FOREGROUND_SERVICE_MICROPHONE`
  perm) is started by `CallServiceController` when a call enters
  CONNECTING and stopped on hangup — keeps the mic alive when the screen
  sleeps. `RECORD_AUDIO` runtime perm requested on first call tap;
  silent no-op on denial for phase 1. New high-importance
  `ospchat_calls` channel for heads-up incoming notifications (tap
  deep-links to `ospchat://call/{callId}`). Media stack:
  `io.getstream:stream-webrtc-android:1.3.10` wrapped in
  `AndroidAudioCallSession` / `AndroidAudioCallSessionFactory` (Hilt
  singleton owning the shared `PeerConnectionFactory`). ICE servers
  empty — LAN-only, host candidates only. Signaling over existing Ktor
  HTTP via 4 new endpoints (`/v1/call/{offer,answer,ice,hangup}`)
  introduced in `ospchat-shared:0.2.1`; media itself is UDP.
  `material-icons-extended` added for `CallEnd` / `Mic` / `MicOff`.
  `mavenLocal()` added to `settings.gradle.kts` for shared-module dev
  cycles. Out of scope phase 1 (deferred): video, call history UI,
  group calls, multiple concurrent calls, retry/reconnect, hold,
  CallStyle / full-screen-intent notifications (Play Store-restricted
  perm on API 34+ + BroadcastReceiver round-trip not worth it for v1).
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
- Discovery is single-network *on Android* — `NsdManager` chooses the
  interface set, not us. Hotspot / dual-Wi-Fi scenarios are untested.
  As of phase 1 multi-network bridging, the shared `Peer` model can
  hold multiple `(host, port)` candidates per UUID and the send
  pipeline tries each, so a peer that Android happens to resolve at
  two addresses is now fully reachable — but Android still surfaces
  whatever the framework decides to surface.
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
6. **Multi-network bridging — phased plan.** Four phases, each
   independently shippable; full description in
   `ospchat-desktop/docs/PROJECT_NOTES.md` "Suggested next steps"
   item 7. Android-side notes:
   - **Phase 1 — Multi-NIC + candidate-list peer model.** The
     desktop-side per-interface `JmDNS` work doesn't apply on
     Android — `NsdManager` enumerates interfaces itself — but the
     candidate-list refactor of `PeerDiscoveryService.Peer` /
     `protectedInsert` lives in `ospchat-shared` and applies to both
     clients. Android-specific change is in
     `NsdPeerDiscovery.handleResolved`: feed an `Endpoint` into the
     merged `protectedInsert` instead of constructing a `Peer`
     directly. F9 hijack rejection is relaxed in this phase
     (see `docs/SECURITY.md` F9) pending phase 2.
   - **Phase 2 — Signed peer advertisements / signed messages.**
     Per-install Ed25519 keypair + signed message DTOs. Restores F9
     properly.
   - **Phase 3 — TURN-as-ICE-relay for voice.** Opt-in per-node TURN
     server; uses libwebrtc's existing ICE-relay primitive.
   - **Phase 4 — `via` relay for text / group messages.** Requires
     phase 2; intermediate nodes forward by uuid hop list. ZeroTier
     does **not** apply on Android (`VpnService` strips
     multicast/broadcast), so the rendezvous-relay path is the only
     viable cross-network discovery option here.

   Updates the existing Known Limitation "Discovery is single-network.
   Hotspot / dual-Wi-Fi scenarios are untested." once phase 1 lands.
   Updates the `Out of scope` line ("Internet / WAN peer discovery")
   once phases 2 + 4 land.
