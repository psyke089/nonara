# AGENTS.md

Guidance for coding agents working in this repository.

## Project

Nonara is a private, local-first note-taking Android app built with Kotlin and Jetpack Compose.
Package and application ID: `app.nonara.notes`. The current scaffold has minSdk 24; the STT
implementation will raise it to Android 13 (API 33), as specified in [docs/SPEC.md](docs/SPEC.md).
Compile and target SDK 37.

- [README.md](README.md) — human-facing overview and commands.
- Remote: `ssh://git@codeberg.org/k4r/nonara.git` (private), default branch `main`.

## Ownership and generation

The user owns the product copy, feature decisions and any assets they provide.

Every asset the app needs — launcher icons, artwork, illustrations, sounds, fonts — must be
generated locally (vector drawables, code-drawn Compose graphics, or scripts in `./tmp`) unless
the user supplies it. Never download assets from random sources, stock libraries, icon packs or
image sites. Do not stall waiting for the user: generate a reasonable asset, state the decision
and continue.

Commit generated assets the build needs; keep intermediate and scratch files in `./tmp`.
The launcher icons are user-supplied artwork from `tmp/NONARA_gothic_android_icons/res/`,
installed in `app/src/main/res` (legacy, adaptive and themed resources). Preserve these assets;
the previous placeholder icon generator has been retired. Never commit binary or third-party
assets the user did not provide.

## Test-driven development

Every behavioural change starts with a failing test:

1. Write a test that expresses the expected behaviour and watch it fail for the right reason.
2. Implement the smallest change that makes it pass.
3. Refactor with the suite green; keep the test as a regression guard.

Test placement:

- `app/src/test` — JVM unit tests for pure logic (domain models, state machines, formatting).
  `./gradlew testDebugUnitTest`.
- `app/src/androidTest` — instrumented tests for Compose UI, persistence, permissions and
  anything that needs the platform. `./gradlew assembleDebugAndroidTest` then run the runner on
  the device.

Run the whole relevant suite before handing work back. New wiring, copies, mock echoes and
"does not throw" checks are not tests; assert observable behaviour.

## Build, test and run

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w app.nonara.notes.test/androidx.test.runner.AndroidJUnitRunner
```

`./gradlew installDebug` is equivalent to the first install step. Releases are debug-signed; the
project has no release signing configuration and no store publication target.

## Device testing

A physical Android phone is connected over ADB for testing — currently a OnePlus 5
(`ONEPLUS A5010`, Android 13 / API 33). Check with `adb devices`; expect one `device` entry.

Develop and verify against that real device. Use an emulator only if no physical device is
attached and the user has explicitly approved it.

Keep device interaction non-destructive and scoped to Nonara: no factory resets, storage wipes,
package removal outside this app, or other destructive adb commands. If the device is
unavailable, finish build and unit-test verification and report that device testing could not be
completed.

## Scratch space

Use the repository's `./tmp/` directory for scratch files, screenshots, exports, generated
intermediate assets and anything temporary. Prefer it over the system `/tmp`, which may be
ephemeral or restricted. `./tmp` is gitignored: copy anything that must be versioned into a
tracked path.

## Conventions

- Kotlin + Jetpack Compose (Material 3), single `:app` module. Keep new code in
  `app.nonara.notes`.
- Local-first: notes and speech recognition stay on the device. Network access and the
  `INTERNET` permission are allowed, including for STT model downloads. No accounts,
  analytics, ads or cloud transcription unless the user explicitly asks for them.
- Use the API 33 minimum for STT; guard APIs introduced after the configured minSdk.
- The app is portrait-first. Landscape does not need a dedicated, fully polished layout, but it
  must stay functional: no crashes, no clipped or unreachable controls, no broken navigation.
  Prefer layouts that adapt (scroll, wrap, weighted rows) over portrait-only assumptions.
- Tests and docs change with the code. Remove scaffolding and throwaway scripts once verified.
