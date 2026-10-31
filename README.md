# Nonara

A private, local-first note-taking app for Android, built with Kotlin and Jetpack Compose.

Nonara keeps notes and speech recognition on the device: no accounts, no analytics and no ads.
Network access is allowed for downloading speech models; transcription will work offline once
the required models are installed.

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

adb shell am instrument -w app.nonara.notes.test/androidx.test.runner.AndroidJUnitRunner
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

```
app/src/main/java/app/nonara/notes/       application code (package app.nonara.notes)
app/src/main/java/app/nonara/notes/ui/    Compose screens
app/src/test/java/app/nonara/notes/       JVM unit tests
app/src/androidTest/java/app/nonara/notes/ instrumented tests
tmp/                                      local scratch space (gitignored)
```

Guidance for coding agents lives in [AGENTS.md](AGENTS.md).
