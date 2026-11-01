# Implementation status

The current code is a functional first implementation, not complete acceptance of SPEC.md.

## Implemented

- API 33 minimum, network/microphone declarations and backup/transfer exclusion rules.
- Markdown/YAML note codec, UUIDv7 IDs, atomic writes, draft recovery, stale-save protection,
  capture receipts, search, optional titles, copy/share, trash and restoration.
- Paper/burgundy Compose Home, Editor and Settings with serif headings and grouped note cards.
- Recording panel with elapsed active time, PCM RMS / Android RMS input meter, explicit microphone
  state and pause/resume/finish controls. Family-specific model cards show selection, sizes and per-model
  installation/download controls. ZIP import/export with path/size bounds.
- Pinned Sherpa 2.0.1 dependency; Whisper Tiny/Base/Small/Medium, Parakeet v3 int8 catalog with pinned model revisions,
  sizes and SHA-256 checksums; explicit downloads, progress, cancellation and model deletion guards.
- Durable, unique foreground WorkManager model downloads with data-sync service declarations,
  progress notification/Cancel, notification permission handling, and persisted transfer status
  reconnected in Settings. Cancel keeps partial data; Resume/Retry and explicit Discard are separate.
- HTTP Range/If-Range resume from actual local lengths, fresh redirect resolution, identity encoding,
  strict 206 range/size/remote-validator checks and bounded restart recovery for 200/416/changed identity.
  Per-file catalogue fingerprints prevent mixing revisions. Full SHA-256 verification includes the
  resumed prefix; verified staged components survive retries and installation remains atomic.
  Storage checks count remaining bytes plus 32 MiB. OkHttp call cancellation closes the socket
  during a stalled read; response/file handles close before the writer lock is released.
  progress is throttled and transient failures use three attempts with exponential 30-second backoff.
- Family-specific Whisper/Parakeet recognizers, language compatibility guards and
  model selection preserving the chosen language. Models remain separate explicit downloads.
- Foreground PCM recording, pause/resume, interruption persistence and manual recovery.
- WorkManager transcription, Silero VAD, bounded decode segments and cached segment results;
  recognized text is applied once and successful capture audio is cleaned up.
- Explicit Android on-device live recognition, preview/final callbacks, durable text journaling,
  pause/finish and bounded busy retries. No generic/cloud recognizer is constructed.

## Still required for full spec completion

- Android language readiness via checkRecognitionSupport and richer error/retry classification.
- Full bundled license texts and newer-device native packaging validation.
- Per-capture undo, confirmed delete-all-data, recovery into a new note after target deletion,
  explicit conflict-copy import UI and export of unsupported/raw files.
- Durable audio checkpoint sample counts, aggregate duration across pause/resume, deadline-aware
  background job continuation, bounded automatic retries and complete race/crash fault injection.
- Stronger reconciliation of simultaneous editor changes/transcript commits; current conflicts
  stop a stale save and retain the draft in the editor for copying rather than merging it.
- Product polish: broader accessibility/landscape review, translated interface copy,
  in-app supplied brand artwork, and preservation of Home scroll position.
- Full speech recognition testing and base/small performance/accuracy measurements on the OnePlus
  5T. The models have not been downloaded to the phone or benchmarked. German/English corpus,
  silence/noise acceptance, interruption/device-restart cases remain unverified.

## Validation

- Resumable-download verification is local only: deterministic byte fixtures/local HTTP server
  cover interruption/resume, 206/200/416 handling, invalid/changed ranges and identity, corrupt
  completed files, full-prefix checksums, fresh redirects, cancellation, reusable component/remaining
  space accounting and preservation of an existing installation. Durable-journal tests exercise
  duplicate starts, cancellation and recovery independently of any Activity.
- No phone/emulator verification, ADB commands, APK installation or real model downloads were
  performed for this change, as required by [MODEL_DOWNLOADS.md](MODEL_DOWNLOADS.md). Screen-lock,
  notification denial, process/device restart, OEM power policies and newer Android quotas/timeouts
  remain unverified on devices. WorkManager scheduling, foreground-service/notification integration
  and Android stop/restart behavior have not been exercised. WorkManager cannot promise
  uninterrupted transfers under all policies.
- JVM regression suite: 42 tests passing: 25 transfer/journal/retry tests plus the existing 17
  storage/codec, recording timer, microphone level, formatting and language compatibility tests.
- Debug app, instrumented-test and signed arm64 release (`0.3.1`) APKs build; lint succeeds with warnings.
- The downloaded Sherpa AAR matches its upstream SHA-256 release digest. A broad dependency
  checksum-generation scan was cancelled to respect the requested verification budget.
- Previous device suite (before this download change): 12 tests passed on the OnePlus 5T, covering capture persistence, launch, editing,
  model catalogue metadata, German-preserving Parakeet selection, Base/Small selection,
  recording-panel controls, landscape navigation and real PCM microphone feedback.
- Actual new-model decoding remains unverified: model downloads for native inference verification
  were stopped at the user's request. No accuracy benchmark or stress pass was run.

## Decisions for later

- Choose the human-recorded German/English acceptance corpus and validate provisional accuracy,
  latency, memory and recording-duration targets before treating these as product guarantees.
- Confirm final in-app typography/artwork and whether Android live mode merits the extra UI.
