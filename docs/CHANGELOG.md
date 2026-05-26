# Changelog

All notable changes to OSPChat are recorded here. The format roughly follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
semantic versioning.

## [Unreleased]

### Added — Phase 5 multi-network bridging — relayed call signaling (PR 3)

- A1→D→A2 smoke test exposed that phase-3 TURN alone couldn't bridge
  cross-network calls: the `/v1/call/offer` POST went direct to A1 and
  was refused because A1 wasn't on A2's LAN. Phase-3 TURN only handles
  the media plane; SDP signaling needs phase-4-style relay too.
- **Fix in shared:** all 4 call DTOs gained `toUuid` / `via` / `hopTtl`
  (signature commits `toUuid` append-only); MessageRoutes' 4 call
  handlers run `relayDecision` and forward when `toUuid != self`;
  `CallRepository` resolves the wire-level next-hop via `PeerRouter`,
  routes answers / ICE / hangups back through the same bridge.
- **Android wiring:** Hilt `provideCallRepository` adds `peerRouter`
  injection; no new permissions or manifest changes.
- OpenAPI bumped to 0.14.0 in shared.

### Added — Phase 3 multi-network bridging — consumer wiring + wire protocol (PR 1.5 + PR 2)

- **PR 1.5 (consumer wiring):** Hilt `SharedModule.provideOspChatTurnServer`
  added; `DiscoveryForegroundService` injects it, calls `turnServer.start()`
  when `relayEnabled=true` after `discoveryRepository.start()` succeeds, and
  calls `turnServer.stop()` in `onDestroy`. Tied to the existing foreground
  service lifecycle; no new permission or manifest change.
- **PR 2 (wire protocol):**
  - `AndroidAudioCallSession(factory, iceServers)` — `RTCConfiguration`
    populated with TURN entries when present; empty list preserves the
    LAN-only host-candidates-only behaviour.
  - `AndroidAudioCallSessionFactory.create(iceServers)` — passes through.
  - Hilt `provideCallRepository` adds `relayBridgeRegistry`; Hilt
    `provideMessageServer` adds `turnServer` → `turnCredentialService`.
  - About-screen "Relay messages for other peers" copy updated to
    "Relay for contacts (messages + voice)" with the explanation of TURN.
  - All 4 existing call DTOs and the new `RelayCredRequest/ResponseDto`
    are signed end-to-end via shared (tolerate-unsigned rollout).
  - OpenAPI bumped to 0.13.0 in shared.

### Added — Phase 3 multi-network bridging — embedded TURN server (shared, PR 1)

- **Pure-Kotlin RFC 5766 TURN subset** added to `ospchat-shared`
  under `com.ospchat.shared.turn.*`. Mirrored in `androidMain`
  via `OspChatTurnServer` (identical to the desktop copy, like
  `bouncycastle`). Wraps `java.net.DatagramSocket` with coroutine
  I/O. PR 1 ships the protocol layer + tests in shared; Android
  consumer wiring (`SharedModule` provider + `DiscoveryForegroundService`
  start/stop tied to `relayEnabled`) lands in PR 1.5 after shared
  is published.
- **One opt-in flag, two purposes:** the existing phase-4
  `relayEnabled` flag now gates both signed-DTO forwarding (phase 4)
  and TURN voice relay (phase 3). One toggle in About already
  exists; copy will be updated in PR 2.
- **No new permissions or manifest changes** — the TURN listener
  uses the existing INTERNET permission. Will live inside
  `DiscoveryForegroundService`'s already-declared
  `connectedDevice` foreground service.

### Added — Phase 4 multi-network bridging — cross-LAN avatar sync

- Hilt `SharedModule.provideMessageServer` now injects `PeerDao` so
  the local server's gossip carries `avatarHash` per gossiped peer
  and the new `GET /v1/peer-avatar/{uuid}` route serves cached
  bytes. Lets a phone behind a bridge fetch the other phone's
  avatar through the multi-homed desktop.

### Added — Phase 4 multi-network bridging — android consumer wiring

- **Hilt `SharedModule`** provides `GossipedPeerStore`,
  `RelayBridgeRegistry`, and `PeerRouter` singletons; threads them
  into `PeerAvatarSync`, `MessageRepository`, and `MessageServer`.
- **`DiscoveryForegroundService`** reads
  `IdentityRepository.currentRelayEnabled()` and passes it to
  `MessageServer.start`; also injects `GossipedPeerStore` and
  collects its peers to auto-record phantom `PeerEntity` rows so
  gossiped peers surface in the contacts UI.
- **`AboutScreen`** has a Switch for the relay opt-in (bound to
  `AboutViewModel.relayEnabled` / `setRelayEnabled`). Takes effect
  on next service restart.
- **`ChatViewModel`** detects phantom peers (`host.isEmpty()`) and
  routes through `MessageRepository.sendToUuid`; direct peers keep
  the existing `send(peer)` path.

### Added — Phase 4 multi-network bridging — consumer-side foundation

- **`ospchat-shared`** now has `PeerRouter`, `RelayBridgeRegistry`,
  gossip-pubkey lookup in `MessageRoutes`, and
  `MessageRepository.sendToUuid` / auto-record-gossip-sender on
  `receive`. Android wiring still pending: provide
  `GossipedPeerStore` + `RelayBridgeRegistry` + `PeerRouter` to Hilt;
  thread them into `PeerAvatarSync` / `MessageRepository` /
  `MessageRoutes`; add the relay opt-in toggle in Settings; surface
  gossiped peers in the contact list.

### Added — Phase 4 multi-network bridging (message-level relay) — server-side foundation

- **Wire format** in `ospchat-shared`: seven signed DTOs gained
  `toUuid`, `via`, `hopTtl` fields. `toUuid` is in the signature via
  an append-only extension; phase-2b and phase-4 clients with
  `toUuid == null` produce byte-identical signature bytes.
- **`/v1/info` gossip + `relayEnabled`** — responder returns
  currently-discovered peers (uuid/nickname/pubkey only) so a
  requester can learn about peers reachable through a bridge.
- **`MessageRoutes` forwards** signed DTOs whose `toUuid != self`
  when relay is opted in. Hop-TTL, loop detection, target lookup,
  and per-decision error codes (`relay_refused`,
  `relay_unroutable`) all handled in the new `relayDecision` helper.
- **In-memory `GossipedPeerStore`** + `PeerAvatarSync` populates it
  on each `/v1/info` fetch.
- **`IdentityRepository.relayEnabledFlow` / `setRelayEnabled`** for
  the user-facing opt-in.
- **OpenAPI 0.12.0**. New `docs/SECURITY.md` F10 (relay trust model).
- **Consumer-side bridge routing pending.** Server side of relay is
  complete; Android still needs (a) UI surface for the relay opt-in
  toggle and (b) outbound-send changes that consult
  `GossipedPeerStore` and route through a bridge when the target
  isn't directly discovered. Both are follow-ups.

### Added — Phase 2b multi-network bridging (signed DTOs + persistent pinning)

- **Signed message DTOs in `ospchat-shared`.** Seven DTOs
  (`IncomingMessageDto`, `ReadReceiptDto`, `ReactionDto`,
  `GroupSnapshotDto`, `GroupMessageDto`, `GroupSyncRequestDto`,
  `GroupLeaveDto`) gained nullable `signedAt` + `signature` fields.
  `MessageClient` signs outbound; `MessageRoutes` verifies inbound
  with a ±5-minute replay window. Tolerate-unsigned rollout mode —
  pre-2b peers still inter-operate.
- **Persistent pubkey pinning** via Room migration v10 → v11
  (`peers.pub_key TEXT NULL`). `PeerRepository.recordSeen` TOFU-pins
  on first sight; `PeerDao.loadPinnedPubkeys` warms the discovery
  service at boot via the new
  `DiscoveryRepository.preloadPinnedPubkeys()`. F9 hijack defence
  now survives a process restart.
- **Android consumer wiring landed.** `DiscoveryForegroundService`
  now `@Inject`s `PeerDao` (Hilt resolves it via the existing
  `DatabaseModule.providePeerDao`) and calls
  `peerDao.loadPinnedPubkeys()` then
  `discoveryRepository.preloadPinnedPubkeys(...)` before
  `discoveryRepository.start(...)`. Failure to load falls back to an
  empty map with a WARN. Startup log includes `persistedPins=<count>`
  so the runtime "did pinning warm" check is one log line.
- `docs/SECURITY.md` F9 marked **FULLY MITIGATED**. OpenAPI bumped
  to `0.11.0`.

### Added — Phase 2a multi-network bridging (identity infrastructure)

- **Per-install Ed25519 keypair** in `ospchat-shared`. New
  `IdentityRepository.ensureSigningKeyPair()` generates/persists the
  keypair (32-byte seed stored in DataStore). Backed by
  `SigningCrypto` (expect/actual over Bouncy Castle's lightweight
  Ed25519 API). Android-side note: BC works on every API level we
  support (minSdk 26), so no Android-specific fallback is needed —
  `java.security.Signature("Ed25519")` would have required API 33+.
- **mDNS pubkey advertisement.** `NsdPeerDiscovery` now publishes the
  local pubkey as a `pk=<b64>` TXT attribute alongside `uuid=`.
  `NsdPeerDiscovery.start` gained an optional `publicKeyB64`
  parameter. Resolutions from pre-phase-2a peers (no `pk=`) still
  merge cleanly.
- **TOFU pubkey pinning + F9 restoration.** `protectedInsert` rejects
  same-UUID resolutions whose pubkey differs from the first-seen pin
  (`DROPPED_PKH_MISMATCH`). Restores the F9 protection that phase 1
  intentionally relaxed, without re-introducing the multi-NIC false
  positive (legitimate multi-address peers present the **same** key
  across all addresses). See `docs/SECURITY.md` F9.
- **`/v1/info` returns the pubkey.** `InfoDto.publicKey` (nullable)
  carries the same b64-encoded key. `MessageServer.start` /
  `ServerIdentity` plumb it through.
- No DTO signatures yet — phase 2b.

### Changed — Phase 1 multi-network bridging

- **Candidate-list peer model** in `ospchat-shared`. `Peer` now carries
  a non-empty `List<Endpoint>` of `(host, port)` candidates instead of
  a single pair. `protectedInsert` merges same-UUID resolutions at
  different hosts into the candidate list sorted by `endpointTier`
  (RFC1918 → CGNAT 100.64.0.0/10 → public), capped at
  `MAX_CANDIDATES_PER_PEER = 8`. The previous F9 hard-rejection
  (`DROPPED_HIJACK`) is relaxed pending phase 2 (signed
  advertisements). Android-side change: `NsdPeerDiscovery.handleResolved`
  now feeds an `Endpoint` into `protectedInsert` instead of
  constructing a `Peer` directly, and logs every cross-host same-UUID
  merge at WARN with the `F9 phase-1 merge` tag. The desktop-side
  per-interface JmDNS work doesn't apply on Android — `NsdManager`
  enumerates interfaces itself.
- **MessageClient candidate-fallback.** Per-peer calls now walk
  `peer.candidates` in preference order on TCP-level connect failures
  before falling back to the legacy `forgetPeer` + rediscover retry.
  Application-level failures (HTTP 4xx/5xx) still short-circuit
  immediately.
- **Inbound source-IP trust matches any candidate.** `MessageRoutes`'s
  source-IP-keyed routes (`verifiedPeerOrRespond`,
  `verifiedRequestingPeerOrRespond`) now match against *any* candidate
  of the identified peer, not just the most-preferred one — a peer
  with multiple endpoints legitimately sends from any of them.
- No wire / OpenAPI change.

### Added — Seed Mode (offline bootstrap)

- A phone running OSPChat can now act as an installer distribution point over
  its Wi-Fi hotspot, so a single device can kickstart an entire offline mesh
  from a single newcomer joining the hotspot. About → **Seed Mode** opens a
  new screen with a hotspot indicator, a package checklist (5 installers:
  Android APK, Windows `.msi`, macOS `.dmg` for Apple Silicon and Intel, and
  Linux `.deb`), a manual GitHub refresh button, and Start/Stop controls
  for the embedded HTTP server.
- The Android binary served by the seed is the phone's own installed APK
  (`Context.applicationInfo.sourceDir`) — always available, no download
  required, marked "Built-in" in the checklist. Every phone with OSPChat
  installed is therefore one tap away from re-seeding the Android binary,
  even on first launch without internet. Desktop binaries are pulled from
  the `tb0hdan/ospchat-desktop` GitHub latest release on demand (manual
  Refresh + per-package Download Selected) and cached under
  `filesDir/seed/<id>/`.
- New `SeedForegroundService` (foregroundServiceType `dataSync`, paired
  with `FOREGROUND_SERVICE_DATA_SYNC` permission). Embedded Ktor CIO
  server binds to `0.0.0.0:8080`; routes are generated from a single
  catalog list in `seed/catalog/SeedCatalog.kt`. The notification carries
  a "Stop" action that routes through `MainActivity.ACTION_STOP_SEED`.
  Runs independently of `DiscoveryForegroundService` — peer messaging and
  seed serving can be active simultaneously.
- Hotspot interface auto-detected by enumerating
  `NetworkInterface.getNetworkInterfaces()` and picking the first up,
  non-loopback, IPv4, site-local address (covers `192.168.x.x` and
  `10.x.x.x` regardless of OEM-specific interface names like `ap0`,
  `wlan1`, `softap0`). The user enables the hotspot manually in system
  settings; the Seed Mode screen surfaces an "Open Wi-Fi settings"
  button and a 2-second polling loop that picks up the IP as soon as
  the hotspot comes up.
- QR generation via `com.google.zxing:core:3.5.3`. Renders the URL
  `http://<hotspot-ip>:8080/` into a `Bitmap` displayed in the screen.
- `ktor-server-partial-content` and `ktor-server-auto-head-response`
  added so browsers can resume an interrupted large-file pull via HTTP
  Range requests. Files stream via `respondFile` (no slurp into RAM —
  the existing `MessageServer`'s `respondBytes` pattern would OOM on a
  140 MB DMG).
- Dedicated `@SeedHttpClient`-qualified `HttpClient` for GitHub fetches
  and large-file downloads (no `requestTimeoutMillis`, 30 s socket
  timeout, follows redirects). The default messaging `HttpClient`'s 5 s
  request timeout is fatal for asset downloads — they share Ktor's
  `HttpTimeout` plugin but need very different settings.
- New `docs/api/seed.yaml` documents the public HTTP API. The peer
  protocol's `openapi.yaml` is unchanged.

### Changed — Folded "Serving at" into the Wi-Fi hotspot line

- Removed the separate `Serving at <url>` monospace label that
  appeared above the QR slot whenever the seed server was running.
  The Wi-Fi hotspot line now does double duty: when the server is
  stopped, it shows `Wi-Fi hotspot: 192.168.x.x`; once the server
  is started, the substitution flips to the full URL
  (`Wi-Fi hotspot: http://192.168.x.x:8080`), and reverts to the
  bare IP on stop. One less line in the card, no separate label to
  reflow around. Removed the now-unused
  `seed_mode_server_running` string and the
  `FontFamily.Monospace` / `FontWeight.SemiBold` imports.

### Fixed — Seed Mode QR slot off-center in the placeholder state

- Symptom: when no server was running, the "Start server to get
  QR" placeholder appeared left-aligned in the hotspot card, but
  the rendered QR (server running) sat centered. The two states
  did not occupy the same screen position.
- Cause: `QrSlot` was a single 240×240 `Box` with the white
  background, positioned via `Modifier.align(Alignment.CenterHorizontally)`
  passed in from the caller's `ColumnScope`. The `Modifier.align`
  is a layout-time positioning hint, and in this column with
  default `Alignment.Start` it didn't reliably horizontally center
  the small placeholder, while the QR image fills the inner box
  via `Modifier.fillMaxSize()` so it always *looked* centered.
- Fix: `QrSlot` now wraps the 240×240 white box in an outer
  `Box(fillMaxWidth, contentAlignment = Alignment.Center)`. Both
  states (placeholder text and rendered QR) live inside the same
  inner box, which is always centered in the card regardless of
  which child is drawn. Caller drops the per-call `align` modifier.

### Changed — Wi-Fi hotspot status collapsed to a single line

- The hotspot card's two-line "Wi-Fi hotspot" header + "Hotspot
  detected at <ip>" body is now a single `titleSmall` line:
  `Wi-Fi hotspot: 192.168.x.x` (active) or `Wi-Fi hotspot: not
  detected` (inactive, with the existing "Open Wi-Fi settings"
  button still below). Saves vertical space above the QR slot.
- String changes: `seed_mode_hotspot_header` removed;
  `seed_mode_hotspot_active` reworded to "Wi-Fi hotspot: %1$s";
  `seed_mode_hotspot_inactive` reworded to "Wi-Fi hotspot: not
  detected".

### Fixed — Android APK served by Seed Mode was named "base.apk"

- Symptom: scanning the Seed Mode QR from a peer and tapping the
  Android row downloaded an installer with the generic filename
  `base.apk`, matching the on-disk path Android uses for installed
  APKs (`/data/app/.../base.apk`). Confusing for the receiver and
  collides if multiple seed downloads land in the same Downloads
  folder.
- Root cause in `SeedServer`'s `/download/{id}` route and the
  landing-page row renderer: both used `file.name` for the
  `Content-Disposition` filename and the meta label. For the
  `SelfApk` package the resolved `File` was
  `Context.applicationInfo.sourceDir`, whose `name` is always
  `base.apk` on Android. The repository's manifest correctly
  carried the friendlier `ospchat-android.apk` (now versioned)
  but the server was bypassing the manifest and reading directly
  off the `File`.
- Fix: `SeedRepository.servedFileFor` now returns a `ServedFile`
  pair (`File` + `downloadName`) instead of a raw `File`. SelfApk
  composes `ospchat-android-<version>.apk` (version pulled from
  `R.string.app_version_name`, which is already populated from the
  `VERSION` file in `app/build.gradle.kts`); `GitHubRelease`
  preserves the original asset name. `SeedServer` uses
  `served.downloadName` for both the `Content-Disposition` header
  and the landing-page row label. `SeedRepository.resolveDescriptor`
  for SelfApk also surfaces the same versioned name in the manifest
  so the Seed Mode checklist UI shows it consistently.
- `docs/api/seed.yaml`: `Content-Disposition` example updated.

### Fixed — Seed Mode download progress bar showed a disconnected dot at the end

- Symptom: while a Seed Mode installer was downloading, the row's
  `LinearProgressIndicator` rendered the in-progress portion of the
  bar plus a small disconnected circle pinned to the far-right end
  of the track. The dot only joined the bar once the download
  completed and the bar hit 100%.
- Root cause: Material 3's `LinearProgressIndicator` defaults to
  drawing a "stop indicator" — a filled circle at the trailing end
  of the track per the M3 spec — whenever `progress < 1f`. Not a
  bug, just an opinionated default that read as visual noise here.
- Fix: pass `drawStopIndicator = {}` (no-op lambda) to the
  `LinearProgressIndicator` in `PackageRow`. The bar now grows
  cleanly from 0% to 100% with no trailing dot.

### Changed — QR slot folded into the hotspot card

- The QR / serving-URL card no longer appears separately under the
  Start/Stop server button. Instead, the existing hotspot status
  card (Wi-Fi hotspot header + detected IP) now hosts a fixed-size
  QR slot at the bottom. Stable layout: the slot keeps its
  `QR_DISPLAY_SIZE` (240 dp) footprint whether the server is
  running or not, so nothing reflows on Start/Stop.
- States:
  - Server stopped → white QR-sized box with the placeholder text
    "Start server to get QR".
  - Server starting (URL known, bitmap not rendered yet) → centered
    `CircularProgressIndicator` in the same slot (the QR render
    loop is fast but not instantaneous).
  - Server running → the actual QR plus the "Serving at <url>" line
    in monospace right above the slot.
- Removed the now-redundant `ServerInfoCard` composable. Stop now
  just stops; the slot in the hotspot card flips back to the
  placeholder via the existing
  `SeedModeViewModel.observeServerState` path that nulls
  `qrBitmap` when `serverUrl` becomes null. New string
  `seed_mode_qr_placeholder`.

### Added — Seed Mode "Clear cache" button

- New "Clear cache" outlined button (error-tone) directly under
  "Download Selected" on the Seed Mode screen. Tap opens a Material 3
  `AlertDialog` confirming the destructive action; confirm deletes
  every cached desktop installer (and any leftover `.part` scratch
  files) under `filesDir/seed/<id>/`, then refreshes the manifest so
  every row flips back to "Not downloaded" immediately. The bundled
  Android APK is not in this tree (`Context.applicationInfo.sourceDir`),
  so the built-in Android row is unaffected.
- Plumbing: new `SeedCache.clearAll()` wipes the tree but keeps the
  per-descriptor directories (they're re-created on demand by
  `packageDir`). New `SeedRepository.clearCache()` runs the wipe on
  `Dispatchers.IO`. New `SeedModeViewModel.clearCache()` calls the
  repository, then `refreshManifest()` to pick up the new state.
- Button is disabled while a batch download is running, while the
  seed server is running (so an in-flight peer GET can't race a
  wipe), and when nothing non-builtin is cached. No wire / OpenAPI
  change.

### Fixed — Seed Mode row status stuck at "Not downloaded" after a successful download

- Symptom: in Seed Mode, ticking multiple installers and tapping
  "Download Selected" downloaded every artifact correctly, but each
  per-row status label reverted to "Not downloaded" the instant the
  individual download finished — the label only flipped to "Cached"
  after the *entire batch* completed. Confusing for users staring at
  a row that just hit 100%.
- Root cause in `SeedModeViewModel.downloadSelected`: per-item the
  loop calls `downloadProgress.remove(id)` after `downloadPackage`
  returns and then moves on. The status label is derived from
  `SeedPackageRow.isCached`, which is populated from `latestManifest`
  — but `latestManifest` is only re-fetched inside the `finally`
  block once *all* items in the batch are done. So between
  "this item finished" and "the whole batch finished" the row's
  `isCached` was still `false`, and removing the in-flight progress
  entry made the UI fall back to the "Not downloaded" branch of
  `packageStatusLabel`.
- Fix: split the per-item path into success/failure handling. On
  success call a new `markCached(id)` helper that flips `isCached`
  to `true` on the matching entry in *both* `latestManifest` (so
  later iterations in the same batch see the updated state) and the
  visible `Ready.packages` row (so the label re-renders immediately),
  and clears `downloadProgress` for that row in the same state copy.
  The end-of-batch `refreshManifest()` call is preserved — it now
  just confirms what the optimistic update already showed and picks
  up any GitHub-side metadata changes.

### Fixed — Android → Android calls stuck at `Connecting…` (ICE CHECKING forever)

- Symptom: A→A calls connected on the wire (offer/answer exchange OK,
  call notifications fire correctly), but the callee's
  `AndroidAudioCallSession` logged `ICE connection state: CHECKING` and
  never reached CONNECTED. After 30 s the caller's ring timeout fired
  `NO_ANSWER`. Reproducible 100 % between two Android handsets on the
  same Wi-Fi. A→Desktop and Desktop→A had been fixed in
  `ospchat-shared:0.2.2` / `0.2.4` (replay=64 + buffered ICE on accept).
- Root cause in `ospchat-shared` `CallRepository.applyIce`: when an ICE
  candidate arrived **before** the matching offer's POST had been
  processed, it hit `pendingOffers[callId] == null` and was dropped
  with `applyIce: no active session and no pending offer for callId=… —
  dropping`. The caller starts trickling local host candidates the
  instant `setLocalDescription` returns inside `createOffer`, which on
  Android's fast 1-2-interface gather emits the UDP host candidate
  before the call to `client.sendCallOffer` has even finished writing
  the offer body to the socket. Ktor on the callee then handles the
  three resulting requests (`/v1/call/offer`, `/v1/call/ice` ×3)
  concurrently; `applyOffer` is the heavy handler (DB upsert + ringtone
  + Notification.MediaSession setup, ~190 ms on the test handset) so
  the cheap `applyIce` won the mutex first. The lone useful UDP
  candidate was discarded, leaving only the two `tcptype=active` IPv6
  host candidates that libwebrtc emits as port-9 placeholders — these
  are unconnectable without a TCP-passive listener on the peer, so
  ICE checks never paired and stayed CHECKING. A→Desktop and
  Desktop→A win the same race because Desktop's gather emits enough
  late UDP candidates after applyOffer lands, and Android-as-callee's
  `applyOffer` runs fast enough that the offer usually wins; A→A
  loses both ways.
- Fix in shared: `CallRepository` grows a `preOfferIce` buffer that
  stashes candidates arriving for a callId we haven't yet seen an
  offer for. `applyOffer` drains the buffer into the freshly-created
  `PendingOffer.pendingIce` (sender-validated), so the rest of the
  pipeline (the existing `acceptCall` → `drain remote ICE` path) sees
  every candidate the caller sent. Entries expire after
  `ringTimeoutMs` (30 s) if no offer ever arrives; per-callId cap of
  64 candidates keeps the buffer bounded under flooding. BUSY-rejected
  offers also clear the buffer immediately so it doesn't sit until
  TTL. Wire / OpenAPI unchanged. Will land in the next `ospchat-shared`
  release; Android picks it up via a single version bump in
  `gradle/libs.versions.toml`.

### Fixed — Crash on accepting an incoming call without `RECORD_AUDIO`

- Symptom: app crash-looped (`AndroidRuntime FATAL EXCEPTION` repeatedly
  from `CallForegroundService.onStartCommand:48`) the moment the callee
  tapped Accept on an incoming call, before any UI for the call rendered.
  Stack:
  `SecurityException: Starting FGS with type microphone … requires …
  RECORD_AUDIO and the app must be in the eligible state`. Once the
  `activeCall` was in CONNECTING and the controller kept re-firing the
  service start, the process died again on every restart.
- Root cause: the outgoing-call path (`ChatScreen.launchCallOrRequestPermission`)
  already gated on `RECORD_AUDIO`, but the incoming-call accept path in
  `IncomingCallOverlay` called `viewModel.accept(callId)` directly. On
  API 31+ the resulting CONNECTING transition fires
  `CallServiceController` → `CallForegroundService.start` →
  `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`,
  which the framework rejects with `SecurityException` if `RECORD_AUDIO`
  is not held — unconditional process crash.
- Fix (two layers):
  1. `IncomingCallOverlay` now checks `RECORD_AUDIO` on accept and, if
     missing, launches `ActivityResultContracts.RequestPermission`. On
     grant it accepts and routes to the call screen; on denial it
     auto-declines the call (POSTs hangup) so the caller sees the
     normal decline path instead of a hanging ring.
  2. Defensive guard in `CallForegroundService`: both the static
     `start(context)` and `onStartCommand` short-circuit when
     `RECORD_AUDIO` is not granted — `start` skips
     `startForegroundService` entirely (so the 5-second
     "must call startForeground" deadline is never armed), and the
     service `stopSelf()`s if it somehow gets dispatched without the
     perm (revoked from Settings mid-call). Logs a warning under the
     tag `CallForegroundService` either way.
- No wire / OpenAPI change. No shared-library version bump needed.

### Changed — ospchat-shared bumped from 0.2.2 to 0.2.4

- Picks up the detailed ICE / call-signaling logging added in 0.2.3
  (every offer / answer / ICE candidate, local + remote, and every call
  state transition is now logged with the `callId` for cross-side
  correlation, plus the candidate string itself so CHECKING-forever cases
  are diagnosable from logs alone) and the 0.2.4 release on top. Android's
  `gradle/libs.versions.toml` pin moves from `0.2.2` to `0.2.4`. Wire /
  OpenAPI unchanged.

### Fixed — Android → Desktop calls stuck on "Connecting…"

- Reverse-direction sibling to the Desktop → Android fix below. Symptom:
  Android-initiated calls to Desktop never moved past `Connecting…` (Desktop
  log showed `bufferedIce=0` on accept, no `applyIce ←` lines ever arrived,
  Desktop emitted only a single TCP-passive host candidate, session stayed
  in `NEGOTIATING` until hangup). Root cause in
  `media/AndroidAudioCallSession.kt` (and mirrored in the desktop project's
  `JvmAudioCallSession.kt`): the local-ICE `MutableSharedFlow` was built
  with `replay = 0` and `extraBufferCapacity = 64`. The original comment
  claimed this buffered 64 candidates; it does not — with `replay = 0`,
  `tryEmit` against a flow with zero subscribers is silently discarded
  (`extraBufferCapacity` only kicks in for *existing slow subscribers*).
  libwebrtc's signaling thread starts firing `onIceCandidate` the instant
  `setLocalDescription` returns inside `createOffer` / `acceptOffer` —
  which happens **before** `CallRepository.bindSession`'s
  `scope.launch { collect { … } }` has scheduled its collector. Asymmetry:
  Desktop has many interfaces (loopback + eth + wifi + docker/vpn) and
  gathers slowly enough that some late candidates survived past
  subscription; Android with its 1-2 interfaces gathered fast enough
  that *every* host candidate was lost. Fix: use `replay = 64` instead
  of `extraBufferCapacity = 64`; the most-recent 64 emissions are
  preserved and replayed to the first subscriber when `bindSession`
  attaches. No wire / OpenAPI change.

### Added — audio voice calls (phase 1)

- One-to-one LAN voice calls between OSPChat peers. Audio only — video
  deferred to phase 2. Tap the new phone icon in any chat's `TopAppBar`
  to call; an in-app `IncomingCallDialog` overlay rings (system default
  ringtone via `RingtoneManager`) on the callee side. Accept opens the
  full-screen `CallScreen` (mute + hangup); decline POSTs hangup.
  Outbound rings auto-time-out after 30 s. Incoming calls during another
  active call are auto-rejected with `BUSY` (no queue).
- New `CallForegroundService` (`foregroundServiceType="microphone"`,
  declared in manifest with `FOREGROUND_SERVICE_MICROPHONE` perm) is
  started by `CallServiceController` when a call enters CONNECTING and
  stopped on hangup. Keeps the mic alive when the screen sleeps or the
  app is backgrounded — table stakes on Android 14+.
- `RECORD_AUDIO` runtime permission requested on first call tap via
  `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
  Permission denial silently no-ops (no toast / rationale for phase 1).
- High-importance `ospchat_calls` notification channel for incoming-call
  heads-up display when the app isn't foreground. Tap deep-links to
  `ospchat://call/{callId}` and routes via the existing `ProcessDeepLinks`
  hook into the `CallScreen` composable. No CallStyle / full-screen-intent
  for phase 1 — accept/decline lives in the in-app overlay (avoids the
  Play Store-restricted `USE_FULL_SCREEN_INTENT` permission on Android 14+
  and the `BroadcastReceiver` round-trip CallStyle requires).
- Media stack: `io.getstream:stream-webrtc-android:1.3.10` — Stream's
  actively-maintained Android fork of libwebrtc, keeps the `org.webrtc.*`
  package namespace. `AndroidAudioCallSession` wraps `PeerConnection`;
  `AndroidAudioCallSessionFactory` (Hilt singleton) holds the shared
  `PeerConnectionFactory` and initializes the native lib on first use.
  ICE servers empty — LAN-only, host candidates only, no STUN / TURN.
- Signaling rides the existing Ktor HTTP transport via four new endpoints
  (`/v1/call/{offer,answer,ice,hangup}`) introduced in
  `ospchat-shared:0.2.1`; media itself is UDP via libwebrtc.
- `material-icons-extended` added to `libs.versions.toml` for the
  `CallEnd` / `Mic` / `MicOff` icons used by the call UI. ~5 MB APK
  impact, not worth maintaining custom vector drawables for.
- `mavenLocal()` added to `settings.gradle.kts` so locally-published
  shared snapshots are visible during shared-module dev cycles. The
  Github Packages repo still wins for released versions.

### Fixed — Desktop → Android calls stuck on "Connecting…"

- Symptom: outbound calls from the Desktop client to Android never moved
  past `Connecting…`; the Android logcat showed
  `ICE connection state: CHECKING` and stayed there until the 30 s
  ring timeout. Calls in the reverse direction (Android → Desktop)
  worked.
- Root cause lives in `ospchat-shared`'s `CallRepository.applyIce`:
  the callee dropped every ICE candidate that arrived before the user
  tapped Accept (`val active = current ?: return`, where `current` is
  only created in `acceptCall`). The caller starts trickling host
  candidates the moment `setLocalDescription` returns inside
  `createOffer`, so a multi-interface JVM desktop (loopback + eth +
  wifi + docker/vpn) emits its entire candidate set well before the
  Android user has tapped Accept — all of them landed on Android and
  were silently discarded. Android then had the answer SDP but zero
  remote candidates: ICE pairs from the Desktop side checked
  one-way, never got a STUN binding response, and stayed CHECKING.
  The reverse direction worked in practice because Android typically
  has only one wifi interface and Desktop's user usually accepts
  fast enough that some candidates squeak through after `current`
  is set.
- Fix in `ospchat-shared:0.2.2`: `PendingOffer` grows a `pendingIce`
  buffer; `applyIce` appends to it while ringing; `acceptCall`
  drains it into the session right after `acceptOffer` (which sets
  the remote description, so libwebrtc is ready to accept them).
  Wire-compatible — no OpenAPI change. Android bumps its
  `ospchatShared` pin in `gradle/libs.versions.toml` from `0.2.1`
  to `0.2.2`.

### Added — reactions on group messages

- Long-press a group bubble (own or peer's) to open the emoji picker; the
  selected emoji becomes the user's reaction on that message. Chips render
  inside the bubble under the body.
- Chip display rule:
  - 1 or 2 reacters with the same emoji → tiny initials avatars
    (oldest-first by `reactedAt`).
  - 3+ → numeric count.
- Tapping a chip toggles: if it carries your own reaction it's removed;
  otherwise it's added (matches DM semantics). Self-tinted with
  `tertiaryContainer`, others neutral.
- Storage reuses the existing `reactions` Room table (no migration —
  `(messageId, fromUuid)` PK works for group messages too).
- Delivery is mesh fan-out to every other current group member; offline
  members catch up via the extended `GroupSyncer` payload.
- Wire: `POST /v1/reactions` gains a nullable `groupId`. When set, the
  receiver validates the sender against group membership. OpenAPI 0.9.0.

### Fixed — peer-list flicker regression in `ospchat-shared:0.1.1` (fixed in 0.1.2)
- 0.1.1 introduced `MessageClient` → `DiscoveryRepository.forgetPeer` on
  TCP connect failures. The Android NSD implementation bounced the entire
  service discovery, which fires `onServiceLost` for every peer (not just
  the targeted one), empties `_peers`, and then re-emits `onServiceFound`
  for each. `DiscoveryForegroundService.peerSyncJob` reads
  `(current - previous)` and fires `PeerAvatarSync.sync` +
  `GroupSyncer.sync` for every peer that "newly appeared" — so one
  failed background call would cycle the entire peer list and fan out
  N×2 fresh HTTP requests, any of which could fail and start the loop
  over. Symptom: peers blinking on/off several times per second.
- 0.1.2 ships two fixes in `ospchat-shared`:
  - `NsdPeerDiscovery.forgetPeer` is now surgical — drops the entry
    from `_peers` + `nameToUuid` and feeds a stub `NsdServiceInfo`
    (name + type) through the existing resolve queue, so only the
    targeted peer is re-resolved. No `stopServiceDiscovery`, no global
    snapshot churn.
  - `MessageClient` methods now take `rediscover: Boolean = true`;
    `PeerAvatarSync` (`/v1/info`, `/v1/avatar`), `GroupSyncer`
    (`/v1/groups/sync`), `PeerInfoNotifier`
    (`/v1/notify-refresh`), and `MessageRepository.downloadAttachment`
    (`/v1/attachments/{id}`) all pass `false`. These flows already
    have their own retry/backoff and must not invalidate the
    discovery snapshot on failure. User-initiated sends keep the
    default `true` so the original "peer restarted on a new port" bug
    stays fixed.

### Fixed — one-way messaging after the peer's desktop app restarts
- Restarting the desktop OSPChat app picked a fresh ephemeral port, but
  Android's NSD framework didn't re-fire `onServiceFound` for the
  port-only change, so its cached resolution kept pointing at the dead
  port. Android→Desktop sends silently failed until Android was nudged
  into re-resolving (toggling Wi-Fi, restarting the app, etc.).
- Now Android also persists the embedded server's bound port via
  `IdentityRepository.lastServerPort` and re-binds it on the next boot
  (falls back to ephemeral on `EADDRINUSE`) so its peers stay reachable
  through an Android restart too.
- `MessageClient` now wraps every per-peer call in a one-shot
  rediscover-and-retry. On a TCP connect failure it calls
  `DiscoveryRepository.forgetPeer(uuid)` (which drops the peer from the
  live snapshot and restarts NSD discovery to force a fresh resolve),
  waits up to 3 s for a fresh resolution whose host:port differs from
  the failed address, then retries once. Application-level rejections
  (HTTP non-2xx) are not retried.

### Changed — ospchat-shared from mavenLocal to GitHub Packages
- `tb0hdan/ospchat-shared` is now released (tag `v0.1.0`) and published to
  the GitHub Packages Maven registry. The Android build pulls it from
  `https://maven.pkg.github.com/tb0hdan/ospchat-shared` instead of
  `mavenLocal()`, removing the manual `gradle publishToMavenLocal`
  bootstrap step.
- `settings.gradle.kts` adds the registry and reads credentials from the
  `gprUser` / `gprToken` Gradle properties (fallback: `GITHUB_ACTOR` /
  `GITHUB_TOKEN`). Even public GitHub Packages require an authenticated
  `GET`, so each developer must set a Personal Access Token with
  `read:packages` once — documented in the README.
- `gradle/libs.versions.toml` hoists the version into the `[versions]`
  block as `ospchatShared = "0.1.0"` so future bumps are a one-line edit.
- `.github/workflows/ci.yml` requests `packages: read` and surfaces the
  workflow's built-in `GITHUB_TOKEN` to Gradle as `ORG_GRADLE_PROJECT_*`
  env vars so CI authenticates against the registry without extra
  secrets.

### Added — contacts + foldable peer list
- The Contacts tab is now organized into two foldable sections:
  **Contacts** (peers the user has explicitly saved, online or offline)
  and **Peers** (live LAN discoveries that are not yet saved contacts).
  Both sections start expanded; fold state is preserved across
  recomposition via `rememberSaveable`. Empty sections still show
  the header plus a hint line so the affordance is discoverable.
- **Long-pressing a peer row** anywhere in either section opens a
  Material 3 `DropdownMenu` with two items: **Add to contacts** /
  **Remove from contacts** (the action swaps based on the row's
  current `is_contact` flag), and **Info**.
- **Info dialog** shows the peer's avatar, current `host:port`,
  online state or relative last-seen time, first-seen time, full
  UUID (monospace), all previously-observed addresses, and all
  previously-observed nicknames.
- Contacts persist by UUID: promoting a peer survives IP changes,
  app restarts, and going offline. `PeerRepository.recordSeen`
  preserves the contact flag across re-discovery upserts.
- Room schema **v8** with non-destructive `MIGRATION_7_8`:
  - `peers.is_contact INTEGER NOT NULL DEFAULT 0`
  - `peer_addresses(uuid, host, port, first_seen_at, last_seen_at)`
    with composite PK `(uuid, host, port)`
  - `peer_nicknames(uuid, nickname, first_seen_at, last_seen_at)`
    with composite PK `(uuid, nickname)`
  - Both history tables are backfilled from the existing `peers`
    row on migration so pre-existing peers retain at least one
    history entry each.
- History rows are written from inside `PeerRepository.recordSeen`
  via a dedicated `PeerHistoryRecorder` helper, keeping all NSD
  reconciliation funneled through a single entry point. The
  `INSERT OR IGNORE` + `UPDATE last_seen_at` pair on the composite
  primary key preserves the original `first_seen_at` regardless of
  how many times the same address/nickname tuple recurs.
- New `AddToContactsUseCase` / `RemoveFromContactsUseCase`
  (`domain/contacts/`) keep the ViewModel free of repository
  details and become the natural home for any future contact-side
  effects (e.g. cross-device sync).
- **Per-peer history is bounded to the 10 most-recent rows per
  table.** `PeerHistoryRecorder` calls new `PeerHistoryDao.prune*`
  queries after each upsert; both `peer_addresses` and
  `peer_nicknames` are independently capped via a single SQLite
  `DELETE … WHERE (composite-pk) NOT IN (SELECT … LIMIT N)`
  statement scoped to the peer's partition. Storage no longer
  grows monotonically under DHCP churn or repeated renames.

### Added — group chats + broadcast channels
- The Groups tab now hosts two foldable sections — **Group chats**
  (any member may post) and **Broadcast channels** (only the
  creator may post). A floating "New group" button opens a sheet
  for naming, picking the kind, and selecting initial members
  from the user's saved contacts.
- Long-press on any group row opens a Material 3 `DropdownMenu`.
  Creators see **Add members** / **Remove members** / **Info**.
  Non-creator members see **Leave group** / **Info**. Membership
  changes bump `membership_updated_at` and are pushed to current
  members; offline members converge on next catch-up.
- **Mesh delivery, not creator-hub**. Every member posts directly
  to every other current member. Groups remain usable when the
  creator is offline. Posts to currently-offline members are
  caught up via bidirectional history sync.
- **Catch-up sync** runs on every NSD "absent → present" peer
  transition, mirroring the existing avatar-sync hook. Each side
  exchanges `(groupId, latestSentAt)` cursors with the peer and
  fetches missing messages. History is therefore available even
  when the original sender is permanently offline, as long as
  *any* current member has it.
- **Trust model**: membership changes are only accepted from the
  creator; receivers reject `BROADCAST` posts from non-creators.
  Snapshot version (`membership_updated_at`) provides last-writer-
  wins ordering across rapid creator-side edits.
- Group messages reuse the existing notification channel
  (`ospchat_messages`); titles use the group name, bodies are
  prefixed with the sender nickname. Notifications are suppressed
  when the user is already viewing the group chat. Deep link
  `ospchat://group/{groupId}` routes straight into the chat.
- Room schema **v9** with non-destructive `MIGRATION_8_9` adding
  three tables:
  - `groups(id, name, kind, creator_uuid, created_at,
    membership_updated_at, last_read_at)`
  - `group_members(group_id, member_uuid, member_nickname,
    joined_at)` with composite PK
  - `group_messages(id, group_id, from_uuid, from_nickname, body,
    sent_at, direction, status)` with `(group_id, sent_at)` index
- OpenAPI **0.8.0** adds the `groups` tag with four endpoints:
  - `POST /v1/groups/messages` — mesh-delivered message (carries
    a `GroupSnapshot` so receivers auto-create the group)
  - `POST /v1/groups/membership` — creator pushes snapshot
  - `POST /v1/groups/sync` — bidirectional history catch-up
  - `POST /v1/groups/leave` — self-removal
- Layered architecture: `data/groups/` (entities, DAOs,
  repositories, `GroupHistoryRecorder`-style `GroupSyncer`),
  `domain/groups/` (`CreateGroupUseCase`,
  `AddGroupMembersUseCase`, `RemoveGroupMembersUseCase`,
  `LeaveGroupUseCase`, `GroupBroadcaster`),
  `ui/groups/` + `ui/groupchat/` for the new screens.

### Added — explicit exit
- The persistent foreground-service notification gains an **Exit**
  action button. Tapping it routes through `MainActivity` with a
  new `ACTION_EXIT` intent, which stops `DiscoveryForegroundService`
  (releasing the multicast lock, the Ktor server, and the ongoing
  notification) and calls `finishAndRemoveTask()` so the app
  disappears from the recents list.
- The About screen gains a destructive **Exit OSPChat** button at
  the bottom (error-container tint). It opens a Material 3
  `AlertDialog` confirmation; on confirm it runs the same teardown
  path as the notification action.

## [0.1.13] - 2026-05-16

### Added — message reactions
- Long-pressing a chat bubble (own or peer) opens an emoji picker
  bottom sheet (reusing the AndroidX `EmojiPickerView` we already host
  for the composer). The selected emoji becomes the local user's
  reaction on that message — at most **one reaction per user per
  message**; picking a different emoji replaces the previous one.
- Reactions render as small rounded chips **inside the bubble** —
  directly under the message body — so the bubble itself grows to
  contain them. Chip layout uses Compose `FlowRow`; each chip shows the
  emoji and, when there are multiple reactors, a count. To keep chips
  legible against either bubble tint, chips that *I* contribute to use
  the tertiary-container tint, the rest use a neutral surface tone
  contrasted against the bubble's own colour.
- Tapping a chip toggles: if my current reaction matches the chip's
  emoji, the chip-tap *removes* my reaction; otherwise it upserts mine
  to the chip's emoji.
- New Room schema **v7** with non-destructive `MIGRATION_6_7` creating
  a `reactions` table. Composite primary key `(message_id, from_uuid)`
  enforces the one-per-user-per-message rule via Room's
  `OnConflictStrategy.REPLACE`.
- New `Reaction` domain class, `ReactionEntity`, `ReactionDao`
  (`observeForPeer` joins reactions with messages so the Flow is
  conversation-scoped), and `ReactionRepository` (`react` + local
  upsert/delete + wire send; `applyReaction` for inbound).
- New wire endpoint `POST /v1/reactions` carrying `ReactionDto`
  (`messageId, fromUuid, fromNickname, emoji: String?, reactedAt`).
  `emoji == null` means *remove this user's reaction*; otherwise it's
  an upsert. Same trust check as `POST /v1/messages` (UUID known via
  NSD + source IP matches advertised host).
- `MessageClient.sendReaction`, `ReactionRepository`, and a `ChatViewModel`
  `react(messageId, emoji?)` complete the local pipeline.
- OpenAPI bumped to **0.7.0** with the new path, `Reaction` schema,
  and add/remove examples.

## [0.1.12] - 2026-05-16

### Changed — avatar propagation no longer bounces the service
- Avatar swaps now propagate via a new `POST /v1/notify-refresh` push
  notification instead of bouncing the discovery foreground service.
  `AvatarRepository` (after writing the new self file + persisting the
  hash) calls `PeerInfoNotifier.broadcastRefresh()`, which fires a
  fire-and-forget POST to every currently-known peer. Each peer's route
  handler verifies the requester by source IP and calls
  `PeerAvatarSync.triggerSync(senderPeer)` to schedule the local
  `GET /v1/info` + `GET /v1/avatar` round-trip on a long-lived IO scope.
  The HTTP response goes back as `202 Accepted` immediately. This avoids
  the multicast-lock release that comes with a service bounce — which
  on some Wi-Fi networks (and especially when AP isolation is *not* the
  problem) was leaving the radio in a degraded state for 10+ seconds
  after the bounce. Nickname changes still bounce because the new name
  has to land in NSD's TXT record. OpenAPI bumped to **0.6.0**.

### Fixed — avatar propagation
- Avatar changes now actually propagate to other peers. Two problems
  combined into the bug report ("settings dialog correctly displays
  newly selected avatar, but it doesn't propagate to the other phone"):
  - `PeerAvatarSync` fired exactly once when a peer transitioned from
    absent to present in the NSD snapshot, but the moment immediately
    after a service bounce had the TCP layer in a flaky state — logs
    showed `ConnectTimeoutException` on one side and
    `NoRouteToHostException` on the other right after the new NSD
    advertisement. The sync gave up. It now retries up to four times
    with `0 / 500 ms / 2 s / 5 s` backoff, which clears the transient
    failures.
  - Cached avatar files lived at a stable path (`peer-<uuid>.jpg`,
    `self.jpg`). Coil keys its in-memory cache on the file path, so
    overwriting the same path with new bytes left the old bitmap on
    screen until the process restarted. Filenames now embed the
    SHA-256 hash (`peer-<uuid>-<hash>.jpg`, `self-<hash>.jpg`); a
    content change therefore becomes a path change, the `PeerRecord`
    Flow re-emits with a new `avatarLocalPath`, and `AsyncImage`
    reloads. Old hash-files are cleaned up via
    `AvatarStore.cleanupSelfExcept` / `cleanupPeerExcept`.

## [0.1.11] - 2026-05-16

### Added — user avatars
- Every user now has an avatar. When no custom image is set, the UI
  renders a colored circle with the first two letters of their nickname
  (deterministic per-UUID hue, fixed saturation/lightness; white text).
  The initials regenerate when the nickname changes.
- New **Avatar** subsection in About > Settings: shows the current
  avatar at 96 dp; **Change image** opens the system photo picker
  (gallery only); **Reset to initials** drops back to the colored
  circle.
- A 44 dp avatar now sits on the left of each peer row in the Contacts
  tab; a 32 dp avatar appears next to the nickname in the chat top bar.
- Avatars are shared across peers. `GET /v1/info` carries an optional
  `avatarHash` (SHA-256 hex of the JPEG bytes, or `null` for initials);
  a new `GET /v1/avatar` streams the binary. Receivers cache by hash and
  re-pull only on change.
- Custom-avatar pipeline reuses `AttachmentCompressor` with `maxEdge =
  256` so the JPEG stays small (~10–30 KB) and EXIF rotation is applied
  before encoding. Bytes land at `filesDir/avatar/self.jpg`; SHA-256 is
  persisted via DataStore for fast `/v1/info` responses.
- New Room schema v6 with `MIGRATION_5_6` adding nullable `avatar_hash`
  + `avatar_local_path` columns to `peers`. Existing rows render as
  initials until the peer announces a custom avatar.
- New `PeerAvatarSync` (Hilt singleton): given a `Peer`, fetches
  `/v1/info`, diffs the hash, pulls fresh avatar bytes via
  `MessageClient.fetchAvatar`, persists. Triggered by
  `DiscoveryForegroundService.peerSyncJob` for each peer that transitions
  from absent to present in the NSD snapshot — covers first discovery
  and re-discovery after a peer restarts their service (which is how
  nickname / avatar changes propagate).
- Saving a custom avatar bounces the discovery service the same way
  saving a nickname does, so peers immediately see us drop+rejoin and
  fetch the new hash without needing a push channel. (Superseded in
  **0.1.12** by the `POST /v1/notify-refresh` push path.)
- OpenAPI spec bumped to **0.5.0** with the new `/v1/avatar` GET path
  and the optional `Info.avatarHash` field (SHA-256 hex regex pattern).

## [0.1.10] - 2026-05-16

### Added — continuous integration
- GitHub Actions workflow `.github/workflows/ci.yml`. Every branch push
  and pull request runs `make ktlint` + `make build` (output discarded
  with the runner). Tag pushes (`refs/tags/**`) additionally create a
  GitHub Release via `softprops/action-gh-release@v2` with the generated
  `ospchat-<VERSION>-debug.apk` attached as a downloadable asset and
  auto-generated release notes. Per-ref concurrency cancels superseded
  in-flight runs. Job permission upgraded to `contents: write` so the
  release-creation step has the token scope it needs. Stack: Ubuntu
  runner, Temurin JDK 17, `android-actions/setup-android@v3` with
  `platforms;android-35` + `build-tools;35.0.0`, Gradle 8.10.2 installed
  manually from `services.gradle.org` (the `gradle/actions/setup-gradle`
  action's "Provision Gradle" step collided with the runner's
  pre-installed `/usr/bin/gradle`), `ktlint` 1.8.0 from the official
  GitHub release.

## [0.1.9] - 2026-05-16

### Added — image attachments
- Chat composer gained a `+` button that opens a Material 3
  `ModalBottomSheet` with two options:
  - **Gallery** — launches the system photo picker
    (`ActivityResultContracts.PickVisualMedia`, `ImageOnly`). No
    permission needed on any supported API level.
  - **Camera** — launches `ActivityResultContracts.TakePicture` against
    a freshly-allocated `cacheDir/captures/<uuid>.jpg` exposed via a
    `FileProvider` (`${applicationId}.fileprovider`,
    `res/xml/file_paths.xml`). No `CAMERA` permission required — we
    delegate via `Intent`. AndroidManifest gains a `<queries>` block
    for `MediaStore.ACTION_IMAGE_CAPTURE` (Android 11+ app visibility).
    Stale captures are swept before each new capture so cache can't
    grow unbounded.
- Picked / captured image is shown as a small preview chip above the
  text field; tap the close icon to discard.
- On send, `AttachmentCompressor` decodes the URI with a two-pass
  `BitmapFactory` (sample-size + exact scale), reads
  `ExifInterface.TAG_ORIENTATION` from the source and applies the
  matching `Matrix` rotation/flip, then re-encodes JPEG q85. A portrait
  photo that the camera saved as landscape pixels + `orientation = 6`
  arrives at the recipient already upright. A 10 MB phone photo
  becomes ~500 KB. Bytes are persisted to
  `filesDir/attachments/<messageId>.bin` via the new `AttachmentStore`.
- `POST /v1/messages` body gains an optional `attachment` object with
  `{mimeType, sizeBytes, width, height}` — width/height are post-EXIF
  rotation so the receiver can pre-size its placeholder with the right
  aspect ratio. The binary itself stays on the sender and is fetched by
  the receiver via a new `GET /v1/attachments/{messageId}` (streams the
  file at the recorded MIME; trust-checked by source-IP against the
  NSD snapshot).
- On receive, the metadata is persisted and a long-lived background
  coroutine in `MessageRepository` fires `MessageClient.fetchAttachment`
  to pull the bytes; the Room row's `attachment_local_path` flips
  non-null on success, and the bubble swaps from a placeholder spinner
  to the actual image.
- Image bubbles render via Coil (`AsyncImage`) at bubble width, using
  the announced `width:height` ratio for the placeholder so the layout
  doesn't reflow when the bytes arrive.
- Tap on an image opens a full-screen `Dialog` with pinch-to-zoom +
  pan via `rememberTransformableState`. Tap outside the image
  dismisses.
- Attachment-only messages (no text body) are allowed.

### Changed
- Room database bumped to v5. Non-destructive `MIGRATION_4_5` adds five
  nullable columns to `messages`: `attachment_mime`,
  `attachment_size_bytes`, `attachment_width`, `attachment_height`,
  `attachment_local_path`. Pre-existing rows stay as plain-text messages.
- `OpenAPI` spec bumped to `0.4.0` with the new path, `Attachment`
  schema, and the optional `attachment` property on `IncomingMessage`.
- New dependencies: `io.coil-kt:coil-compose:2.7.0` for image rendering,
  `androidx.exifinterface:exifinterface:1.3.7` for source-side EXIF
  orientation reads.

### Fixed
- Image send was broken from the day attachments first shipped during
  this release cycle: `AttachmentCompressor` did
  `resolver.openInputStream(uri)?.use { stream ->
  BitmapFactory.decodeStream(stream, null, dimensionOptions) } ?: error(...)`
  for the dimensions pass. `decodeStream(..., inJustDecodeBounds = true)`
  is documented to **always return null** — the dimensions are written
  to the options' `outWidth` / `outHeight`. So `use { … }` returned
  null on every successful read and the `?:` fired `error("Could not
  open attachment URI")` regardless. Now the stream is null-checked
  separately and the result of the decode is discarded.
- Sending an image no longer crashes the app even if compression fails.
  `MessageRepository.send` wraps the attachment block in
  `withContext(Dispatchers.IO)` inside a `try { … } catch (t: Throwable)`,
  so OOM from `Bitmap.createScaledBitmap` on huge photos, undecodable
  URIs, and file-I/O failures are logged and turned into
  `Result.failure(t)` rather than killing the process from
  `viewModelScope.launch`'s default dispatcher.
- `MessageRepository.downloadAttachment` now logs failures via `Log.w`
  so a peer disappearing mid-download leaves a breadcrumb instead of
  silently leaving the bubble stuck in the placeholder state.
- New dependency: `io.coil-kt:coil-compose:2.7.0` (~2 MB).

## [0.1.8] - 2026-05-16

### Added — bottom-tab navigation + About / Settings
- Three bottom tabs in a new `MainShell`: **Contacts**, **Groups**,
  **About**. Single Material 3 `Scaffold` with a `TopAppBar` showing the
  app name and a `NavigationBar` with all three. Tab selection persists
  across config changes (and across nav-away to chat) via
  `rememberSaveable`.
- **Contacts** hosts the existing peer list (formerly "Peers on your
  network"). The previous top bar with that label + "You: <nickname>"
  is gone; the app name lives in `MainShell`'s top bar instead.
- **Groups** is a placeholder with a "coming soon" message.
- **About** shows the app name, a `Version 0.1.8` line, a clickable
  `ospchat.com` link (opens in the user's default browser), a divider,
  and a Settings section currently containing a single editable
  nickname field with a Save button.
- Changing the nickname now bounces `DiscoveryForegroundService` so NSD
  re-registers with the new name. Peers see the new nickname within a
  few seconds rather than only after an app restart.
- New tab icons: `Icons.Filled.Person` (Contacts), `Icons.Filled.AccountBox`
  (Groups), `Icons.Filled.Info` (About) — all from `material-icons-core`,
  no new dependency.

### Changed
- `PeersScreen` no longer wraps itself in a `Scaffold` / `TopAppBar`;
  it's now a content-only Composable rendered inside `MainShell`'s body
  slot. `PeersViewModel` dropped its now-unused `ownNickname` flow.
- `AppRoot` navigation root changed: `peers` → `main` (which renders
  `MainShell`); `chat/{peerUuid}` is unchanged. Notification deep links
  (`ospchat://chat/{peerUuid}`) continue to land on the chat screen.
- `app/build.gradle.kts` now declares
  `resValue("string", "app_version_name", projectVersion)` so the About
  screen pulls the version straight from the `VERSION` file without
  enabling `BuildConfig`.
- Dead strings (`peers_title`, `peers_self`, `peers_messaging_soon`)
  removed; new strings for tabs and About added.

## [0.1.7] - 2026-05-16

### Added — notifications + unread indicator
- New system notifications when a message arrives via `POST /v1/messages`.
  Channel `ospchat_messages` (`IMPORTANCE_DEFAULT`, `VISIBILITY_PRIVATE`)
  carries the peer's nickname as the title and the full message text as
  the body (with `BigTextStyle` expansion).
- **Do-Not-Disturb is honoured by suppressing posting entirely** when
  `NotificationManager.currentInterruptionFilter` is anything other than
  `INTERRUPTION_FILTER_ALL`. The message is still stored and the unread
  indicator on the peer list still updates.
- Notifications are also suppressed when the user is currently looking at
  the chat with that peer. Tracked via a new `ActiveChatTracker`
  (Hilt singleton) updated from `ChatViewModel.onChatVisible` /
  `onChatHidden`, which `ChatScreen` drives off the screen's
  `LifecycleEventObserver` (`ON_START` / `ON_STOP`).
- Tapping a notification deep-links to that peer's chat via
  `ospchat://chat/{peerUuid}`. New intent-filter on `MainActivity`
  (`launchMode="singleTask"`); `AppRoot` registers an
  `addOnNewIntentListener` so the deep link routes correctly whether the
  app is cold-started or already running.
- Unread indicator on the peer list: `Icons.Filled.Email` in the primary
  color plus a Material 3 `Badge` with the count, shown immediately to
  the left of the existing online status dot whenever `unreadCount > 0`.
- New `MessageNotifier` encapsulates channel setup, the DND check, the
  POST_NOTIFICATIONS permission check on API 33+, and the deep-link
  `PendingIntent` (`FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`).

### Changed
- Room database bumped to v4. Non-destructive `MIGRATION_3_4` adds
  `last_read_at INTEGER NOT NULL DEFAULT 0` to `peers`; existing rows
  default to epoch so every previously-stored inbound message starts as
  unread until the user opens its chat.
- New DAO query `MessageDao.observeUnreadCounts()` joins `messages` with
  `peers.last_read_at`, returning a per-peer count of inbound rows newer
  than the read mark. `PeerRepository.observeAll` now three-way `combine`s
  the persisted peers, the unread counts, and the live NSD snapshot.
- `PeerRecord` gains `unreadCount: Int`.
- `PeerRepository.recordSeen` now preserves `lastReadAt` across upsert so
  a re-discovery on a new IP doesn't reset the user's read mark.

## [0.1.6] - 2026-05-16

### Added — message delivery status
- Outbound messages now carry a status that progresses through the
  lifecycle **Sending → Delivered → Read**, with **Failed** as the
  error terminal. Inbound messages are always recorded as Delivered.
- Each outbound bubble renders its status below the timestamp:
  `Sending…` while in flight, `✓` once the peer's HTTP server has
  accepted the POST, `✓✓` (primary color) once the peer reports having
  read the message, and `⚠ Tap to retry` (error color) on failure.
- Failed outbound bubbles are tappable: a tap re-attempts the POST,
  flipping the row back to Sending while in flight.
- New endpoint `POST /v1/read-receipts` with body
  `{fromUuid, upToSentAt}`. Same trust check as `/v1/messages`. The
  handler runs an idempotent `UPDATE` that upgrades rows
  `DELIVERED → READ` for the requesting peer's UUID where
  `sent_at <= upToSentAt`.
- `ChatScreen` schedules a `LaunchedEffect(latestInboundSentAt,
  peer.isOnline, peer.uuid) { delay(2_000); viewModel.notifyRead(…) }`
  that fires the receipt only after 2 seconds of chat visibility,
  resets if a new message arrives during the wait, and re-fires when
  the peer transitions to online.

### Changed
- Room database bumped to v3. A non-destructive `MIGRATION_2_3` adds
  the `status TEXT NOT NULL DEFAULT 'DELIVERED'` column to `messages`;
  existing rows keep their bodies and become Delivered.
- `MessageClient` got a `sendReadReceipt` method; the existing
  `send` and the new helper share an internal `postJson` to avoid
  duplicating the response-status check.
- `OpenAPI` spec bumped to `0.3.0` with the new path + `ReadReceipt`
  schema reusing the existing `Error` response envelope.

## [0.1.5] - 2026-05-16

### Added (peer persistence + online status)
- Peers are now persisted by **UUID**, not by IP address. A peer that
  changes Wi-Fi, switches networks, or goes offline keeps its place in
  the list — and its chat history goes with it.
- New `peers` table in Room (schema v2 with a non-destructive
  `MIGRATION_1_2`). Columns: `uuid` (PK), `nickname`, `last_host`,
  `last_port`, `first_seen_at`, `last_seen_at`.
- `PeerRepository` joins the persisted `peers` table with the live NSD
  snapshot from `DiscoveryRepository.peerSnapshot` to produce
  `PeerRecord(isOnline)`. `DiscoveryForegroundService` runs a long-lived
  `peerSyncJob` that upserts freshly-resolved peers into the table.
- `PeersScreen` row now shows a status circle on the right: green
  (`#22C55E`) for online, neutral grey for offline; the secondary line
  shows `host:port` when online and `"offline"` otherwise. Ordering:
  online first, then by most-recently-seen, then alphabetical.
- `ChatScreen` top app bar carries the same online dot. The composer
  shows a hint line and disables Send when the peer is offline so
  messages aren't silently dropped against a stale IP.
- Database renamed `MessageDatabase` → `OspChatDatabase`
  (`com.ospchat.android.data.db`) since it now holds more than one
  entity.

## [0.1.4] - 2026-05-16

### Fixed
- Peers no longer drop off mDNS shortly after navigating Peers → Chat →
  back. Root cause was a regression introduced in 0.1.2: returning to
  `PeersScreen` re-fires its `LaunchedEffect(Unit)`, which calls
  `startForegroundService` again, which triggered a second
  `MessageServer.start()` that threw `IllegalStateException("already
  started")`. The exception was caught by `onStartCommand`'s generic
  handler and `stopSelf()` tore the service down — taking NSD with it.
  - `DiscoveryForegroundService` now tracks a `fullyStarted` flag and
    no-ops if a second `onStartCommand` arrives while we're already
    running.
  - `MessageServer.start` is now idempotent: it returns the previously-
    bound port if the engine is already running, instead of throwing.

## [0.1.3] - 2026-05-16

### Added — emoji
- Emoji picker in the chat composer: tap the 😊 button next to the text
  field to open a Material 3 `ModalBottomSheet` containing the AndroidX
  `EmojiPickerView` (categories, search, recents, skin-tone variants
  handled by the picker itself). Selecting an emoji appends to the draft.
- Bundled emoji font via `androidx.emoji2:emoji2-bundled`, so messages
  render with the same emoji set on every device regardless of OEM
  customisation. Fully offline — no Internet, no downloadable fonts.
- New dependencies: `androidx.emoji2:emoji2-bundled` (~10 MB),
  `androidx.emoji2:emoji2-emojipicker` (~1 MB). APK grew from ~13 MB to
  ~25 MB; this is the cost of shipping the emoji font itself.

## [0.1.2] - 2026-05-16

### Added — REST messaging
- Embedded Ktor (CIO) HTTP server inside `DiscoveryForegroundService`.
  Each peer now exposes `GET /v1/info` and `POST /v1/messages` on the
  same TCP port it advertises via NSD. The placeholder `ServerSocket` in
  `NsdPeerDiscovery` is gone; NSD advertises the Ktor port instead.
- `MessageClient` (Ktor client over CIO) for sending messages to other
  peers.
- Room database (`ospchat.db`) with a single `messages` table; persisted
  across restarts. Indexed on `(peer_uuid, sent_at)`.
- `ChatScreen` + `ChatViewModel`: per-peer chat with self/peer message
  bubbles, send composer, auto-scroll to latest.
- `androidx.navigation:navigation-compose` for routing: `peers` →
  `chat/{peerUuid}`. Tapping a peer in `PeersScreen` now opens chat
  (replaces the "messaging coming soon" toast).
- `docs/api/openapi.yaml` — hand-written OpenAPI 3.0.3 schema documenting
  the wire protocol, schemas, and trust model.
- Trust check on `POST /v1/messages`: receiver verifies `fromUuid` is a
  peer it discovered via NSD and that the request's source IP matches
  that peer's advertised host; otherwise 404 / 401.
- APK output now produced at
  `app/build/outputs/apk/debug/ospchat-<VERSION>-debug.apk` via
  `archivesName` (matches the Makefile's `install` target).
- New Makefile target `debug` (alias used by `install`).
- New dependencies: Ktor 2.3.13 (server-cio, server-content-negotiation,
  server-status-pages, serialization-kotlinx-json, client-cio,
  client-content-negotiation), Room 2.6.1, kotlinx-serialization 1.7.3,
  Navigation Compose 2.8.4, `material-icons-core`, `slf4j-nop` (runtime).

### Fixed (post-review)
- `AndroidManifest.xml` now declares `android:usesCleartextTraffic="true"`
  — required on API 28+ for our `http://` peer-to-peer traffic.
- Trust check in `POST /v1/messages` now compares the request's
  `origin.remoteAddress` (IP literal in Ktor CIO) instead of `remoteHost`
  (which may be a reverse-DNS hostname).
- `DiscoveryRepository` gained a synchronous `findPeer(uuid)` helper; the
  HTTP route uses that instead of subscribing to `peers.first()` on every
  inbound request.
- `MessageServer.start` / `stop` are now thread-safe and cancel-safe:
  `engine` is `@Volatile`, mutations are guarded by a lock, and the
  engine is torn down if anything throws after the bind.
- `DiscoveryForegroundService.onStartCommand` wraps the start coroutine
  in try/catch so a Ktor or NSD failure logs + `stopSelf()` instead of
  leaving a zombie service holding a multicast lock.
- `onDestroy` reordered: cancel the start job → stop NSD → stop server →
  release multicast lock → cancel scope (was: cancel scope first, which
  could leak a Ktor engine).
- `HttpClient` moved to `di/NetworkModule.kt` so it is Hilt-managed and
  swappable for a `MockEngine` in tests; `MessageClient` no longer owns
  it.
- `ChatViewModel.selfUuid` is now a non-null `StateFlow<String>`
  initialised eagerly in `init {}`, eliminating the brief "all bubbles
  look incoming" flash on screen open.
- `ChatScreen` only auto-scrolls when the user is already at (or within
  one item of) the bottom of the list; scrolling up to read history is
  no longer hijacked by inbound messages.
- `@Singleton` added to `provideMessageDao` for consistency.
- OpenAPI spec: error strings are now slug-style (`unknown_peer`,
  `address_mismatch`, `internal_error`) enumerated in the schema; error
  responses extracted into `components/responses/Error`; server `port`
  default changed from `"0"` to `"9000"`.

## [0.1.1] - 2026-05-16

### Added — initial scaffold + LAN peer discovery
- Initial Gradle (Kotlin DSL) + Android scaffold targeting API 35,
  `minSdk` 26, application ID `com.ospchat.android`.
- Jetpack Compose + Material 3 UI shell with Hilt DI and a single
  `MainActivity` that routes between two screens based on persisted
  identity.
- `IdentityRepository` backed by DataStore Preferences: stores a
  user-chosen nickname and a stable per-install UUID.
- `NicknameScreen` for the first-run nickname prompt.
- `NsdPeerDiscovery` wrapping `android.net.nsd.NsdManager` — registers
  the device under service type `_ospchat._tcp.` (with `uuid=<uuid>` TXT
  attribute) and discovers + resolves other peers. Resolve calls are
  serialized to work around the single-in-flight limit on API 26–29.
- `DiscoveryRepository` (Hilt singleton) exposing
  `StateFlow<List<Peer>>`.
- `DiscoveryForegroundService` (`connectedDevice` foreground service
  type) holding a `WifiManager.MulticastLock` for the duration of
  discovery and posting an ongoing notification.
- `PeersScreen` listing live peers (nickname + host:port); tap shows a
  "messaging coming soon" toast.
- Project docs: `docs/PROJECT_NOTES.md` and this changelog.
- Project conveniences: `Makefile`, `README.md`, `.gitignore`, `VERSION`.

### Fixed (post-review)
- `MainActivity` now calls `enableEdgeToEdge()` after `super.onCreate()`.
- `NsdPeerDiscovery` releases the resolve queue lock before issuing the
  `nsdManager.resolveService` binder call, and a `@Volatile running`
  flag is checked in all NSD callbacks so a resolve that completes
  after `stop()` cannot re-populate state. `selfUuid` is now
  `@Volatile`.
- `DiscoveryForegroundService` acquires the multicast lock synchronously
  on the main thread (no async race with `onDestroy`), guards re-entry
  with `startJob?.isActive`, and returns `START_NOT_STICKY` so a
  half-initialised restart cannot loop.
- `PeersScreen`'s `LazyColumn` now passes window insets via
  `contentPadding` so bottom items aren't clipped by the
  gesture-navigation bar.
- Inlined a redundant `LaunchedEffect` wrapper in `PeersScreen`.
- Dropped the dead `Build.VERSION.SDK_INT >= O` branch in
  `DiscoveryForegroundService.start` (always true at `minSdk=26`).
- `IdentityRepository.ensureUuid()` collapsed from three DataStore reads
  to one read + one transactional `edit`.
- `AppModule.provideWifiManager` no longer calls
  `context.applicationContext` on an already-application-scoped context.
- Pre-Compose splash theme switched from `Material.Light` to
  `DeviceDefault.DayNight` so it respects system dark mode.
- Removed the unused `androidx.compose.material:material-icons-extended`
  dependency.
