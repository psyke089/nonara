# Implementation status

The current code is a functional first implementation, not complete acceptance of SPEC.md.

## Implemented

- API 33 minimum, network/microphone declarations and backup/transfer exclusion rules.
- Markdown/YAML note codec, UUIDv7 IDs, atomic writes, draft recovery, stale-save protection,
  capture receipts, search, optional titles, copy/share, trash and restoration.
- Minimal Home, Editor and Settings with the supplied theme-tinted vector wordmark, locally drawn
  icon actions with accessible names/tooltips, concise labels and grouped note cards. Home has
  no model switcher or promotional copy. Model cards expose language and Streaming chips.
- Recording panel with elapsed active time, PCM RMS input meter, explicit microphone
  state and pause/resume/finish controls. Family-specific model cards show selection, sizes and per-model
  installation/download controls. ZIP import/export with path/size bounds.
- Sherpa is the only dictation backend. Every new capture records recoverable PCM, writes
  `mode=sherpa` into the existing capture field, and requires an installed model that supports
  the selected language; missing models send the user to Settings without downloading or
  switching recognizers.
- Pinned Sherpa 2.0.1 dependency; Whisper Tiny/Base/Small/Medium/Turbo/Large v3, Parakeet v3,
  Canary 180M Flash, Omnilingual ASR 300M, Nemotron 3.5 (160/560 ms), German FastConformer
  transducer/CTC int8 and Kroko German catalogue with pinned model revisions,
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
- Family-specific Whisper/Parakeet/Canary/Omnilingual recognizers and a Nemotron streaming
  transducer adapter for saved VAD segments. Bounded input/draining preserves tail samples and
  releases streams on cancellation. Canary source/target language match; Turbo/Large v3 use 128 mel bins.
  Language compatibility guards and
  model selection preserving the chosen language. Models remain separate explicit downloads.
- [Complete German model catalogue](MODEL_CATALOGUE.md), including additional reference-only multilingual FastConformer,
  Omnilingual v2, Cohere and Qwen entries and all published precision/chunk variants.
- Foreground PCM recording, pause/resume, interruption persistence and manual recovery.
- Optional live Sherpa preview for streaming models: bounded reads from saved PCM, background
  native inference, endpoint/partial handling and cancellation with resource release. Preview is
  transient; Finish reuses durable final transcription and receipt-based insertion. A busy inference
  slot or preview error leaves PCM recording active with a concise status. Capture/note schemas
  and import/export formats are unchanged.
- WorkManager transcription, Silero VAD, bounded decode segments and cached segment results;
  recognized text is applied once and successful capture audio is cleaned up.

## Still required for full spec completion

- Full bundled license texts and newer-device native packaging validation.
- Per-capture undo, confirmed delete-all-data, recovery into a new note after target deletion,
  explicit conflict-copy import UI and export of unsupported/raw files.
- Durable audio checkpoint sample counts, aggregate duration across pause/resume, deadline-aware
  background job continuation, bounded automatic retries and complete race/crash fault injection.
- Stronger reconciliation of simultaneous editor changes/transcript commits; current conflicts
  stop a stale save and retain the draft in the editor for copying rather than merging it.
- Product polish: broader accessibility/landscape review, translated interface copy,
  and preservation of Home scroll position.
- Full speech recognition testing and base/small performance/accuracy measurements on the OnePlus
  5T. The models have not been downloaded to the phone or benchmarked. German/English corpus,
  silence/noise acceptance, interruption/device-restart cases remain unverified.

## Validation

- Minimal UI/streaming/catalogue update (2026-11-02): 56 JVM tests pass; offline lint,
  debug APK and instrumented-test APK builds pass. The full 20-test instrumented suite passes
  on the OnePlus 5T, covering the minimal Home, icon actions, language/Streaming chips,
  incompatible language controls, streaming preference recreation, landscape navigation,
  existing capture persistence and real microphone PCM recording. The supplied wordmark and
  compact Settings were also inspected in phone screenshots.
- No model weights or test audio were downloaded for this update. Catalogue metadata/vocabulary
  lookups transferred under 200 KB. No models are installed on the phone, so real native
  recognition/preview quality, latency, memory use and decoder compatibility remain unverified.
  Deterministic tests cover growing PCM reads, partial replacement, endpoint accumulation,
  preview cancellation/resource release without deleting audio, and Zipformer tail flushing.


- Resumable-download verification is local only: deterministic byte fixtures/local HTTP server
  cover interruption/resume, 206/200/416 handling, invalid/changed ranges and identity, corrupt
  completed files, full-prefix checksums, fresh redirects, cancellation, reusable component/remaining
  space accounting and preservation of an existing installation. Durable-journal tests exercise
  duplicate starts, cancellation and recovery independently of any Activity.
- Device verification: debug app and instrumented-test APKs installed over ADB on the OnePlus 5T;
  the full 17-test instrumented suite passed. It covers capture persistence, real PCM microphone
  feedback, model catalogue metadata, launch, editor/search, model/language selection and
  recreation, landscape navigation, the Settings single-backend UI, the missing-model recording
  gate and recording-panel controls. No model weights were downloaded.
- Screen-lock, notification denial, process/device restart, OEM power policies and newer Android
  quotas/timeouts remain unverified on devices. WorkManager scheduling,
  foreground-service/notification integration and Android stop/restart behavior have not been
  exercised. WorkManager cannot promise uninterrupted transfers under all policies.
- JVM regression suite: 51 tests passing, covering transfer/journal/retry, additional-model and
  streaming-segment behavior, storage/codec, recording timer, microphone level, formatting,
  German/English compatibility and the single-backend German/Sherpa defaults. The new defaults
  tests started failing before implementation.
- Debug app and instrumented-test APKs build; lint succeeds with warnings.
- No model weights or new test audio were downloaded. Pinned weight hashes/sizes come from LFS
  metadata; small token text was read to derive SHA-256. Actual new-family inference and model
  accuracy, latency and memory use remain unverified. No additional JVM Android test framework
  was added.
- The downloaded Sherpa AAR matches its upstream SHA-256 release digest. A broad dependency
  checksum-generation scan was cancelled to respect the requested verification budget.
- Actual new-model decoding remains unverified: model downloads for native inference verification
  were stopped at the user's request. No accuracy benchmark or stress pass was run.

## Decisions for later

- Choose the human-recorded German/English acceptance corpus and validate provisional accuracy,
  latency, memory and recording-duration targets before treating these as product guarantees.
- Confirm final in-app typography/artwork.
