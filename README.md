# OSPChat — Android

Open-source LAN chat for Android. Discovers other instances on the same Wi-Fi
network via mDNS / DNS-SD, then exchanges messages, read receipts, and image
attachments peer-to-peer over an HTTP API each device serves to its neighbours.
No central server, no Internet access required.

Built with Kotlin 2.0, Jetpack Compose + Material 3, Hilt DI, Ktor (embedded
server *and* client), Room for persistence, and Coil for image rendering.

- **Project status**: pre-1.0, actively iterating. Current version: see
  [`VERSION`](VERSION) (currently `0.1.9`).
- **Wire protocol**: [`docs/api/openapi.yaml`](docs/api/openapi.yaml) (OpenAPI 3.0.3).
- **Change history**: [`docs/CHANGELOG.md`](docs/CHANGELOG.md).
- **Design notes**: [`docs/PROJECT_NOTES.md`](docs/PROJECT_NOTES.md).

## Features

- **Peer discovery (LAN)** — devices advertise themselves over
  `_ospchat._tcp.` mDNS with a stable per-install UUID. Discovery uses
  Android's built-in `NsdManager`; a multicast lock keeps mDNS packets
  flowing while the app is foreground.
- **Persistent identity** — peers are remembered by UUID. An IP change
  (Wi-Fi reconnect, hotspot switch) doesn't lose a peer or its chat history.
- **REST messaging** — each device runs an embedded Ktor server. Messages
  are `POST`ed as JSON to the recipient, who stores them in Room
  (`ospchat.db`). The full wire contract is in `docs/api/openapi.yaml`.
- **Delivery status** — outbound messages progress through
  Sending → Delivered → Read; failed sends become tap-to-retry bubbles.
  Read receipts are sent automatically after 2 s of chat-screen
  visibility.
- **Notifications** — inbound messages post system notifications, except
  when the user is already viewing that chat or when the device is in
  Do-Not-Disturb (DND is respected explicitly, not just channel-filtered).
  Tapping a notification deep-links to that conversation.
- **Unread indicator** — peer list shows a colored email icon + count
  badge per peer with un-acknowledged inbound messages.
- **Online status dot** — peer rows carry a green/grey dot reflecting
  whether the peer is currently visible on the LAN.
- **Image attachments** — pick a photo from the gallery *or* take a fresh
  one with the device camera (`+` button → bottom sheet). The compressor
  applies EXIF rotation, scales to a 1920 px longest edge, and re-encodes
  JPEG q85. Images render inline in the chat; tap for a full-screen
  pinch-zoom viewer.
- **Emoji** — bundled emoji font (`emoji2-bundled`) so messages render
  consistently on every device, plus the AndroidX `EmojiPickerView`
  available from a bottom sheet.
- **Tab shell** — bottom navigation between **Contacts** (the peer
  list), **Groups** (placeholder), and **About** (version, link to
  [`ospchat.com`](https://ospchat.com), nickname change).
- **No internet dependency** — discovery is LAN-only; emoji rendering is
  fully bundled; the only outbound network is from peer to peer over
  HTTP.

## Architecture at a glance

```
                 ┌─────────────────────────────────────────────────────┐
                 │                MainActivity (Compose)               │
                 │                                                     │
                 │   ┌──────────────┐         ┌──────────────────┐    │
                 │   │NicknameScreen│  or →   │   MainShell      │    │
                 │   └──────────────┘         │  bottom-tabs:    │    │
                 │                            │  Contacts /      │    │
                 │                            │  Groups / About  │    │
                 │                            └────────┬─────────┘    │
                 │                                     ↓ tap          │
                 │                             ┌──────────────┐       │
                 │                             │  ChatScreen  │       │
                 │                             └──────────────┘       │
                 └─────────────────────────────────────────────────────┘
                                                       │
                                                       │ Hilt @Singleton
                                                       ▼
   ┌─────────────────────┐   ┌─────────────────────┐   ┌────────────────────┐
   │ IdentityRepository  │   │  PeerRepository     │   │ MessageRepository  │
   │ (DataStore)         │   │ Room peers +        │   │ Room messages +    │
   │ nickname + UUID     │   │ live NSD snapshot + │   │ attachments +      │
   └─────────────────────┘   │ unread counts       │   │ status pipeline    │
                             └──────────┬──────────┘   └──────┬─────────────┘
                                        │                     │
                                        ▼                     ▼
                             ┌─────────────────────┐   ┌────────────────────┐
                             │ NsdPeerDiscovery    │   │ MessageServer (Ktor)│
                             │ (NsdManager wrap)   │   │ MessageClient (Ktor)│
                             └─────────────────────┘   │ AttachmentStore +  │
                                        │              │ Compressor (Bitmap)│
                                        ▼              └────────────────────┘
                             ┌──────────────────────────────────────────────┐
                             │ DiscoveryForegroundService                   │
                             │  - acquires WifiManager.MulticastLock        │
                             │  - starts Ktor server, advertises NSD record │
                             │  - bridges NSD → PeerRepository sync         │
                             └──────────────────────────────────────────────┘
```

Two ViewModels per active screen (`PeersViewModel`, `ChatViewModel`,
`AboutViewModel`, `NicknameViewModel`, `AppViewModel`) sit between the
Compose tree and the singletons above.

## Requirements

- **JDK 17+** (we set `jvmTarget = "17"`).
- **Android Studio Iguana (2023.2) or newer**, *or* a standalone Gradle 8.10+
  installation if you don't use the IDE.
- **Android device or emulator on API 26+** (Android 8.0). The app's
  `minSdk = 26`, `targetSdk = compileSdk = 35`.
- **Two real devices on the same Wi-Fi network** to exercise discovery
  end-to-end. An emulator + a physical phone bridged to the host network
  also works, but emulator-to-emulator on a single host is iffy because
  AVDs share localhost and don't see each other's mDNS by default.

### One-time SDK setup

If you don't already have an Android SDK installed, install at minimum:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-35" "build-tools;35.0.0"
```

The build also expects a `local.properties` pointing at your SDK root:

```properties
# local.properties (git-ignored)
sdk.dir=/path/to/Android/Sdk
```

### One-time Gradle wrapper

The Gradle wrapper JAR is **not committed** to this repo. Generate it once:

```bash
gradle wrapper --gradle-version 8.10.2
# or simply: make wrapper
```

(or just open the project in a recent Android Studio — Sync creates the
wrapper for you.) After that, the usual `./gradlew …` commands work.

## Build

| Make target          | Equivalent                                                              |
| -------------------- | ----------------------------------------------------------------------- |
| `make build`         | `./gradlew assembleDebug` (alias for `make debug`)                      |
| `make debug`         | Same — assembles `app-<VERSION>-debug.apk` (named `ospchat-<VERSION>`)  |
| `make install`       | builds then runs `adb install -r` against the connected device          |
| `make clean`         | `./gradlew clean`                                                       |
| `make lint`          | runs `ktlint` over `app/`                                               |
| `make gradle-lint`   | `./gradlew lint` (Android Lint, slower)                                 |
| `make test`          | `./gradlew test`                                                        |
| `make bundle`        | `./gradlew bundleRelease` (AAB for Play Store, unsigned)                |
| `make wrapper`       | generates the Gradle wrapper (one-time)                                 |
| `make tools`         | downloads & installs `ktlint` 1.8.0 into `$GOPATH/bin`                  |

The debug APK lands at:

```
app/build/outputs/apk/debug/ospchat-<VERSION>-debug.apk
```

The filename is wired off the `VERSION` file at the project root.

## First launch

1. Install the APK on **two** devices on the same Wi-Fi.
2. Open the app — you'll be prompted for a nickname on first run.
3. Land on the **Contacts** tab. Within a few seconds you should see
   each device listed with a green online dot.
4. Tap a peer to open the chat. Try:
   - A text message
   - The 😊 emoji picker
   - `+` → **Gallery** to send a picture from your photo library
   - `+` → **Camera** to take and send one immediately
5. Watch the message status under each outbound bubble: ✓ (delivered)
   then ✓✓ (read) once the recipient opens the chat.

## Permissions

| Permission                                | Why                                                                 |
| ----------------------------------------- | ------------------------------------------------------------------- |
| `INTERNET`                                | Required for Ktor server/client sockets (LAN traffic).              |
| `ACCESS_NETWORK_STATE`                    | Needed by Ktor / NSD plumbing.                                      |
| `ACCESS_WIFI_STATE`                       | Same.                                                               |
| `CHANGE_WIFI_MULTICAST_STATE`             | Acquire `MulticastLock` so mDNS packets aren't filtered by the radio. |
| `FOREGROUND_SERVICE`                      | Hosts NSD + Ktor while the user wants to be visible.                |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE`     | The semantic foreground-service-type we use.                        |
| `POST_NOTIFICATIONS`                      | Runtime-requested on Android 13+ so message notifications can show. |

No `CAMERA` permission — image capture is delegated to the system camera
app via Intent. No `READ_MEDIA_IMAGES` either — the photo picker
(`PickVisualMedia`) doesn't need it.

## Wire protocol

Each peer exposes:

| Method | Path                            | Purpose                                       |
| ------ | ------------------------------- | --------------------------------------------- |
| `GET`  | `/v1/info`                      | Identity probe (uuid + nickname + apiVersion) |
| `POST` | `/v1/messages`                  | Receive a chat message                        |
| `POST` | `/v1/read-receipts`             | Mark previously-sent messages as read         |
| `GET`  | `/v1/attachments/{messageId}`   | Stream a previously-announced image binary    |

Trust model: requesters must be peers currently visible to the receiver
via NSD, *and* the request's source IP must match the host advertised by
that peer. TOFU; no TLS, no signatures. **LAN-only.**

The full schema with examples, error codes, and response shapes lives
in [`docs/api/openapi.yaml`](docs/api/openapi.yaml).

## Project layout

```
ospchat-android/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/com/ospchat/android/
│   │   ├── OSPChatApp.kt                       @HiltAndroidApp
│   │   ├── MainActivity.kt
│   │   ├── di/                                 Hilt modules
│   │   ├── data/
│   │   │   ├── identity/                       nickname + UUID (DataStore)
│   │   │   ├── discovery/                      NSD wrapper + Peer model
│   │   │   ├── peers/                          PeerEntity + Room + repo
│   │   │   ├── messages/                       Message + Room + repo
│   │   │   ├── attachments/                    Store + Compressor
│   │   │   └── db/                             OspChatDatabase + migrations
│   │   ├── net/
│   │   │   ├── dto/                            Wire DTOs (kotlinx.serialization)
│   │   │   ├── client/MessageClient.kt         Ktor HttpClient wrapper
│   │   │   └── server/                         Ktor embedded server + routes
│   │   ├── notifications/                      ActiveChatTracker + MessageNotifier
│   │   ├── service/DiscoveryForegroundService  service hosting NSD + Ktor
│   │   └── ui/
│   │       ├── theme/                          Material 3 colors / typography
│   │       ├── main/MainShell.kt               bottom-tab Scaffold
│   │       ├── nickname/                       first-run prompt
│   │       ├── peers/                          Contacts tab content
│   │       ├── chat/                           ChatScreen + ViewModel
│   │       ├── groups/                         placeholder tab
│   │       ├── about/                          About tab + nickname setting
│   │       ├── AppRoot.kt                      top-level NavHost
│   │       └── AppViewModel.kt
│   └── res/                                    strings, themes, icons,
│                                                file_paths, backup rules
├── docs/
│   ├── CHANGELOG.md
│   ├── PROJECT_NOTES.md
│   └── api/openapi.yaml
├── gradle/libs.versions.toml                   version catalog
├── Makefile
├── README.md                                   (this file)
└── VERSION
```

## Known limitations

- **Single network only**. Hotspot / dual-Wi-Fi / VPN scenarios aren't
  tested and discovery may misbehave.
- **No TLS, no end-to-end encryption.** TOFU trust model; only use on
  networks you trust (home Wi-Fi, not coffee-shop public networks).
- **No group chats yet** — the Groups tab is a placeholder.
- **No message editing or deletion.**
- **Attachment retention is forever.** Sent and received images live in
  `filesDir/attachments/` and aren't pruned. A 'forget peer' / 'clear
  conversation' UI is on the roadmap.
- **No automated tests yet** — manual smoke testing only.

## Roadmap

Not committed, but the obvious next steps:

1. Group chats (multi-peer rooms).
2. TLS + an authenticated handshake (Noise framework, or similar).
3. Message editing / deletion (with tombstones, since peers may be
   offline).
4. Voice notes (audio attachments).
5. Forwarding messages between peers.
6. Backup / restore.
7. CI: a GitHub Actions workflow running `make build` + `make lint`.
8. Migration tests for the Room schema.

## Contributing

Pull requests welcome on the GitHub mirror. Style is enforced by
`ktlint` (the only thing `make lint` runs); a Compose-friendly
`function-naming` override lives in [`.editorconfig`](.editorconfig).
Commits should not include co-author lines.

## License

TBD (the project is currently unlicensed — please check before
redistributing). Project home: <https://ospchat.com>.
