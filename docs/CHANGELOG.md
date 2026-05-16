# Changelog

All notable changes to OSPChat are recorded here. The format roughly follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
semantic versioning.

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
