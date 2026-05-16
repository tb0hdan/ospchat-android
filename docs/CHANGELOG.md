# Changelog

All notable changes to OSPChat are recorded here. The format roughly follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
semantic versioning.

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
