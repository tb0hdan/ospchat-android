# OSPChat — Android

Open-source LAN chat for Android, written in Kotlin + Jetpack Compose.

The first milestone (v0.1) implements **peer discovery only**: devices running
OSPChat on the same Wi-Fi network discover each other via mDNS / DNS-SD and
appear in a live list. Messaging is intentionally deferred.

## Requirements

- JDK 17+
- Android Studio Iguana (2023.2) or newer, **or** standalone Gradle 8.10+
- Two devices on the same Wi-Fi network (or an emulator + a phone bridged
  to the host network) to actually see discovery in action

Min Android version: **8.0 (API 26)**. Target / compile SDK: **35**.

## First-time setup: Gradle wrapper

The Gradle wrapper JAR is **not committed** to this repo. Generate it once:

```bash
gradle wrapper --gradle-version 8.10.2
```

(or simply open the project in a recent Android Studio — Sync will create the
wrapper for you). After that, the usual `./gradlew …` commands work.

## Build

```bash
make build       # ./gradlew assembleDebug
make install     # install debug APK on connected device
make clean       # ./gradlew clean
make lint        # ./gradlew lint
```

## Project Layout

See [`docs/PROJECT_NOTES.md`](docs/PROJECT_NOTES.md) for the architecture
diagram and a breakdown of every package. The change history lives in
[`docs/CHANGELOG.md`](docs/CHANGELOG.md).

## Try it out

1. Install the debug APK on two devices on the same Wi-Fi network.
2. Launch — pick a nickname on each device.
3. The Peers screen should list the other device(s) within a few seconds.
4. Tapping a peer is a no-op for now (messaging arrives in a follow-up).
