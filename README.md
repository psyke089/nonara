# Nonara

A private, local-first note-taking app for Android, built with Kotlin and Jetpack Compose.

Nonara runs entirely on the device: no accounts, no analytics, no ads and no network access.

## Status

Project scaffolding. The app launches to an empty notes screen; note capture, editing and
persistence are not implemented yet.

## Requirements

- JDK 17 or newer
- Android SDK platform 37 and build-tools 36.0.0
- Android Studio (open this directory as a project) or a terminal
- A physical Android device with USB debugging enabled (Android 7.0 / API 24 or newer)

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
