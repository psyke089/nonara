# AGENTS.md

Guidance for coding agents working in this repository.

## Project

Nonara is a private, local-first note-taking Android app built with Kotlin and Jetpack Compose.
Package and application ID: `app.nonara.notes`. Minimum Android 13 (API 33), as specified in [docs/SPEC.md](docs/SPEC.md).
Compile and target SDK 37.

- [README.md](README.md) — human-facing overview and commands.
- Remote: `ssh://git@codeberg.org/k4r/nonara.git` (private), default branch `main`.

## Language and speech models

- **German (`de-DE`) is the primary dictation language and the default.** English is optional
  secondary support; do not infer an English default from the device or UI language.
- Every selectable speech model must support German. Multilingual models are welcome;
  do not add English-only models unless the user explicitly requests them.
- Selecting a model must preserve the chosen dictation language. Reject or disable an
  incompatible choice instead of silently changing the language.
- Prioritize German recordings when validating transcription. Keep models as explicit,
  separate downloads; never bundle them in the APK.

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
./gradlew assembleRelease
tools/release.sh <version>                # bump, test, build, commit, tag, push
tools/upload-apk.sh gdrive:nonara
```

`./gradlew installDebug` is equivalent to the first install step.

Release builds:

- `versionCode` and `versionName` are declared at the top of `app/build.gradle.kts`. Bump both,
  and the hardcoded `NONARA <version>` line in the Settings footer of
  `app/src/main/java/app/nonara/notes/ui/NotesScreen.kt`.
- `./gradlew assembleRelease` writes `app/build/outputs/apk/release/nonara-<version>.apk`
  (arm64-v8a only). It is signed with the dedicated release key when one is configured, and
  falls back to debug signing otherwise so a contributor checkout still produces an installable
  APK.
  - Signing values come from Gradle properties `nonara.storeFile`, `nonara.storePassword`,
    `nonara.keyAlias`, `nonara.keyPassword`, or the env vars `NONARA_STORE_FILE`,
    `NONARA_STORE_PASSWORD`, `NONARA_KEY_ALIAS`, `NONARA_KEY_PASSWORD`. Locally they live in
    `~/.gradle/gradle.properties`; CI supplies the same values from repository secrets.
  - The keystore is `~/.config/nonara/nonara-release.jks` (alias `nonara`, 4096-bit RSA,
    certificate `CN=Nonara`). **Never commit it**, its passwords or `*.jks`/`*.keystore` files.
    Back it up: losing it means existing installs can no longer be updated in place. To
    recreate it (new identity, every install must be removed first):
    `keytool -genkeypair -keystore nonara-release.jks -storetype PKCS12 -alias nonara -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Nonara, O=Nonara, C=DE"`.
  - Builds signed with the debug key cannot be updated in place by a `CN=Nonara` APK: uninstall
    the old package (`adb uninstall app.nonara.notes`) and export notes first.
  - `tools/release.sh <version>` performs the whole ritual: bumps `versionCode`/`versionName`,
    updates the About line, runs tests/lint, builds and checksums the APK, commits, tags
    `v<version>`, then pushes `main` and the tag to `origin`. It refuses a dirty tree, an
    existing tag, or a version without the `MAJOR.MINOR.PATCH` shape. `--dry-run` stops before
    committing and leaves the bump in the tree.
- `tools/upload-apk.sh [remote:path]` copies the newest release APK and its `.sha256` file to
  Google Drive with `rclone`, printing the target on success. This is an optional extra: GitHub
  releases are the primary distribution. The default target is
  `gdrive:Nonara/releases`; the folder in use is `gdrive:nonara`. The remote lives outside the
  repo in `~/.config/rclone/rclone.conf` — never commit credentials. Uploads run at upstream
  link speed (~30 KiB/s for a 35 MB APK).

## Continuous integration

The source of truth is Codeberg; a push mirror copies `main` and all tags to the public GitHub
repository (`psyke089/nonara`), where GitHub Actions builds. Point local clones at it with
`git remote add github git@github.com:psyke089/nonara.git`, but keep committing to `origin`.

- `.github/workflows/ci.yml`: a push to `main` runs `testDebugUnitTest` and `lintDebug`; a `v*`
  tag additionally builds the signed release APK, asserts the signer is `CN=Nonara`, writes
  checksums and publishes a GitHub release with the APK attached; a manual dispatch builds the
  same APK as a preview artifact without publishing.
- The release job refuses to run when the tag does not match `nonaraVersionName`, when the
  keystore secret is missing, or when the built APK is not signed with the release key. Never
  weaken those checks: a per-runner debug key would silently break installs.
- `tools/install-android-sdk.sh` installs the `android-37` platform and 36/37 build-tools the
  workflow needs.
- The mirror is authenticated over HTTPS with a fine-grained GitHub token scoped to
  `psyke089/nonara` with **Contents** and **Workflows** read-write. An SSH deploy key is not an
  option: GitHub rejects workflow-file updates from one (`refusing to allow an OAuth App to
  create or update workflow … without workflow scope`). The token exists only in Codeberg's
  mirror settings and in the local tea config — never paste it into chat, commits, scripts or
  logs; set it through `Settings → Repository → Mirror Settings` or a command that reads it
  with `read -rs`. Rotating it means delete + re-create the mirror (Forgejo 16.0.0-dev has no
  `PATCH` route for `push_mirrors`), then push once to Codeberg to trigger the first sync.
- It force-pushes (`git push --mirror`), so treat the GitHub copy as disposable: no commits,
  tags or branches are created there by hand.
- A `v*` tag only starts the release run when it reaches GitHub in its own push. If `main` and a
  tag are mirrored in one atomic push, GitHub creates the branch run only; force the release
  with `gh workflow run ci.yml --ref v<version>` — a dispatch on a tag ref still matches the
  `ref_type == 'tag'` guards, so it publishes. `tools/release.sh` pushes `main` and the tag
  separately, which is the normal path.
- The mirror runs `sync_on_commit`, so a push to Codeberg usually triggers both the mirror and the
  workflow; its periodic interval is a safety net only. It is not reliable: a sync can be skipped
  with `last_error` empty and a stale `last_update`, leaving GitHub on the old ref. When that
  happens, push the refs straight to the copy (`git push github main`, `git push github
  v<version>`) or use *Mirror Settings → Synchronize Now* — Codeberg's Forgejo (16.0.0-dev) has
  no `/sync` API route. The mirror force-pushes the same refs later, so the detour is harmless.
- Repository secrets: `NONARA_KEYSTORE_B64` (base64 of the keystore), `NONARA_STORE_PASSWORD`,
  `NONARA_KEY_ALIAS`, `NONARA_KEY_PASSWORD`. Mirror force-pushes never touch secrets.

## Device testing

A physical Android phone is connected over ADB for testing — currently a OnePlus 5T
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
