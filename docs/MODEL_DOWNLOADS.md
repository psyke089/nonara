# Resumable model downloads — handoff spec

## Goal and scope

Model downloads should continue when the phone locks or Nonara leaves the foreground, and
resume partial files after interruptions. The user observed a download stopping after automatic
screen lock; the exact cause has not been diagnosed.

This is a specification only. **Do not run verification on the phone or an emulator.** The user
will test on the phone later. Do not download real models for development verification.

Baseline: commit `fc2c5d2`, version `0.3.0`. German is the primary dictation language; preserve
the existing German-capable catalogue and language selection behavior. Multi-model transcription
and comparison are out of scope.

## Current implementation

- `Models.kt` downloads into a versioned `.partial` directory, reuses complete files after
  size/SHA-256 checks, but deletes and restarts an incomplete file.
- Installation verifies every file, writes a `complete` marker, then renames the staging
  directory into place. Preserve this atomic installation behavior.
- `AppModel.kt` owns the download coroutine in `viewModelScope`; download status is transient.
  `lifecycleStop()` explicitly pauses recordings, not downloads.
- Settings download controls are in `ui/NotesScreen.kt`; pinned URLs, sizes and hashes are in
  `assets/models.json`. The app supports API 33 and targets API 37.

## Required behavior

1. Move download ownership to durable Android background work with a progress notification and
   a cancel action. Use a mechanism compatible with API 33 (for example, a foreground
   WorkManager worker); consult current Android background-transfer rules and add the required
   manifest declarations. Notification permission handling must not falsely report completion.
   Screen lock, navigation and activity recreation must not cancel the transfer.
2. Persist model ID/version and transfer state. Reopening Settings must reconnect to existing
   work, showing aggregate bytes/progress and a clear downloading, interrupted, verifying,
   installed or failed state. Serialize writers and coordinate download, retry and deletion;
   repeated taps must not start duplicate transfers. Keep the current one-download-at-a-time UI.
3. Keep partial files after network failure, cancellation or process death. A subsequent explicit
   Resume/Retry continues them. Cancel stops promptly without deleting progress; provide an
   explicit discard action to remove partial data. Preserve installed models and existing
   capture-related deletion guards.
4. Resume from the actual local file length using HTTP `Range`. Persist suitable remote identity
   information and use `If-Range` where available. Bind partial data to the pinned catalogue
   revision, expected size and hash. Resolve redirects afresh; do not reuse expired signed URLs.
5. Append only after a valid `206` response whose `Content-Range` begins at the requested offset
   and matches the expected total size. Use identity encoding. If the server returns `200`,
   restart that file safely rather than append. For `416`, verify an apparently complete local
   file or restart it. Changed identity, invalid ranges, oversized files and checksum failure
   must never produce an installed model. Bound recovery attempts to avoid restart loops.
6. Verify final size and full SHA-256, including previously downloaded bytes. Retain verified
   completed files across retries. Check storage against bytes still needed plus a safety
   margin, accounting for reusable staged data. Keep progress updates throttled and ensure
   cancellation closes the active network connection and file handles.
7. Use bounded retry/backoff for transient failures. Explicit cancellation must not auto-restart.
   OS suspension/force-stop may still interrupt work; offer recovery without promising
   uninterrupted execution under every device power policy.

## Verification and documentation

Follow repository TDD using small deterministic fixtures and a local HTTP test server. Cover
mid-file interruption/resume, validated `206`, ignored ranges (`200`), `416`, changed identity,
corrupt completed files, redirects, cancellation, duplicate starts, remaining-space accounting,
and failed installation preserving an existing model. Assert resulting bytes and installed
state, not just request wiring. Exercise persisted state independently of the Activity.

Run relevant local tests, lint and APK builds. Do not use ADB, install an APK or run device tests
for this task. Document unverified device behavior explicitly, and update `SPEC.md` and
`IMPLEMENTATION.md` after implementation. No release upload is requested by this handoff.
