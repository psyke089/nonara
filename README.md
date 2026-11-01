# Nonara

A private, local-first note-taking app for Android, built with Kotlin and Jetpack Compose.

Nonara keeps notes and speech recognition on the device: no accounts, no analytics and no ads.
Network access is allowed for downloading speech models; transcription will work offline once
the required models are installed. German is the primary dictation language. Downloadable
models include multilingual Whisper Tiny/Base/Small/Medium/Turbo/Large v3, Parakeet TDT 0.6B v3,
Canary 180M Flash, Omnilingual ASR 300M and Nemotron 3.5;
all offered models support German, with English available as a secondary language.
The [German model catalogue](docs/MODEL_CATALOGUE.md) lists the complete official selection,
available variants, sources and which choices Nonara offers.

## Status

The app includes Home/search, a Markdown editor with automatic saves, trash/restore, ZIP
import/export, theme settings, model downloads, recoverable Sherpa recording/transcription,
and optional Android live dictation. Android 13 / API 33 is the minimum.

The [product and technical specification](docs/SPEC.md) remains the target. See
[implementation status](docs/IMPLEMENTATION.md) for remaining work and validation limits.

## Requirements

- JDK 17 or newer
- Android SDK platform 37 and build-tools 36.0.0
- Android Studio (open this directory as a project) or a terminal
- A physical Android device with USB debugging enabled (Android 13 / API 33 or newer)

## Build, test and run

```sh
./gradlew testDebugUnitTest              # JVM unit tests
./gradlew lintDebug assembleDebug        # lint + debug APK
./gradlew assembleDebugAndroidTest       # instrumented test APK
./gradlew installDebug                   # install on the attached device
./gradlew assembleRelease                # release APK, signed with the release key
tools/release.sh 0.4.0                   # bump, test, build, tag, push
tools/upload-apk.sh                      # newest release APK -> Google Drive (rclone)

adb shell am instrument -w app.nonara.notes.test/androidx.test.runner.AndroidJUnitRunner
```

JVM download tests use small local HTTP fixtures and persisted transfer-state tests; they require
no phone, emulator or real speech models. Model downloads can be cancelled with progress kept,
resumed/retried, or explicitly discarded in Settings.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`; the release APK to
`app/build/outputs/apk/release/nonara-<version>.apk` (arm64-v8a). Releases are signed with the
key configured through `nonara.*` Gradle properties (or `NONARA_*` environment variables); a
checkout without that key silently uses debug signing, and CI rejects such an APK when
publishing. `tools/upload-apk.sh` copies the newest release APK plus its SHA-256 to a Google
Drive remote and needs a one-time `rclone config` setup; credentials stay in
`~/.config/rclone/rclone.conf`, never in the repo.

Codeberg is the source of truth. A push mirror copies `main` and tags to the public GitHub
repository, where GitHub Actions runs the tests on every push and publishes the APK as a GitHub
release for every `v*` tag.

## Layout

```
app/src/main/java/app/nonara/notes/       application code (package app.nonara.notes)
app/src/main/java/app/nonara/notes/ui/    Compose screens
app/src/test/java/app/nonara/notes/       JVM unit tests
app/src/androidTest/java/app/nonara/notes/ instrumented tests
tmp/                                      local scratch space (gitignored)
```

Guidance for coding agents lives in [AGENTS.md](AGENTS.md).
