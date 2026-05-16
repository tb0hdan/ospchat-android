# Changelog

All notable changes to OSPChat are recorded here. The format roughly follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
semantic versioning.

## [Unreleased]

### Added
- Initial Gradle (Kotlin DSL) + Android scaffold targeting API 35,
  `minSdk` 26, application ID `com.ospchat.android`.
- Jetpack Compose + Material 3 UI shell with Hilt DI and a single
  `MainActivity` that routes between two screens based on persisted identity.
- `IdentityRepository` backed by DataStore Preferences: stores a user-chosen
  nickname and a stable per-install UUID.
- `NicknameScreen` for the first-run nickname prompt.
- `NsdPeerDiscovery` wrapping `android.net.nsd.NsdManager` — registers the
  device under service type `_ospchat._tcp.` (with `uuid=<uuid>` TXT
  attribute) and discovers + resolves other peers. Resolve calls are
  serialized to work around the single-in-flight limit on API 26–29.
- `DiscoveryRepository` (Hilt singleton) exposing `StateFlow<List<Peer>>`.
- `DiscoveryForegroundService` (`connectedDevice` foreground service type)
  holding a `WifiManager.MulticastLock` for the duration of discovery and
  posting an ongoing notification.
- `PeersScreen` listing live peers (nickname + host:port); tap shows a
  "messaging coming soon" toast.
- Project docs: `docs/PROJECT_NOTES.md` (this file’s sibling).
- Project conveniences: `Makefile`, `README.md`, `.gitignore`, `VERSION`.

### Fixed (post-review)
- `MainActivity` now calls `enableEdgeToEdge()` after `super.onCreate()`.
- `NsdPeerDiscovery` releases the resolve queue lock before issuing the
  `nsdManager.resolveService` binder call, and a `@Volatile running` flag is
  checked in all NSD callbacks so a resolve that completes after `stop()`
  cannot re-populate state. `selfUuid` is now `@Volatile`.
- `DiscoveryForegroundService` acquires the multicast lock synchronously on
  the main thread (no async race with `onDestroy`), guards re-entry with
  `startJob?.isActive`, and returns `START_NOT_STICKY` so a half-initialised
  restart cannot loop.
- `PeersScreen`'s `LazyColumn` now passes window insets via `contentPadding`
  so bottom items aren't clipped by the gesture-navigation bar.
- Inlined a redundant `LaunchedEffect` wrapper in `PeersScreen`.
- Dropped the dead `Build.VERSION.SDK_INT >= O` branch in
  `DiscoveryForegroundService.start` (always true at `minSdk=26`).
- `IdentityRepository.ensureUuid()` collapsed from three DataStore reads to
  one read + one transactional `edit`.
- `AppModule.provideWifiManager` no longer calls `context.applicationContext`
  on an already-application-scoped context.
- Pre-Compose splash theme switched from `Material.Light` to
  `DeviceDefault.DayNight` so it respects system dark mode.
- Removed the unused `androidx.compose.material:material-icons-extended`
  dependency.
