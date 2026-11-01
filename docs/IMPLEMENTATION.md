# Implementation status

The current code is a functional first implementation, not complete acceptance of SPEC.md.

## Implemented

- API 33 minimum, network/microphone declarations and backup/transfer exclusion rules.
- Markdown/YAML note codec, UUIDv7 IDs, atomic writes, draft recovery, stale-save protection,
  capture receipts, search, optional titles, copy/share, trash and restoration.
- Paper/burgundy Compose Home, Editor and Settings with serif headings and grouped note cards.
- Recording panel with elapsed active time, PCM RMS / Android RMS input meter, explicit microphone
  state and pause/resume/finish controls. Base/Small model cards show selection, sizes and per-model
  installation/download controls. ZIP import/export with path/size bounds.
- Pinned Sherpa 2.0.1 dependency; base/small multilingual int8 catalog with pinned model revisions,
  sizes and SHA-256 checksums; explicit downloads, progress, cancellation and model deletion guards.
- Foreground PCM recording, pause/resume, interruption persistence and manual recovery.
- WorkManager transcription, Silero VAD, bounded decode segments and cached segment results;
  recognized text is applied once and successful capture audio is cleaned up.
- Explicit Android on-device live recognition, preview/final callbacks, durable text journaling,
  pause/finish and bounded busy retries. No generic/cloud recognizer is constructed.

## Still required for full spec completion

- Android language readiness via checkRecognitionSupport and richer error/retry classification.
- Resumable model transfers (current retry starts incomplete files over), full bundled license
  texts, and newer-device native packaging validation.
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

- JVM regression suite: 13 tests passing (storage/codec plus recording timer pause accounting,
  signed PCM microphone levels and duration formatting).
- Debug app and instrumented-test APKs build; lint succeeds with warnings.
- The downloaded Sherpa AAR matches its upstream SHA-256 release digest. A broad dependency
  checksum-generation scan was cancelled to respect the requested verification budget.
- Device pass: capture persistence, launch, note editing, Base/Small selection, recording-panel
  pause/resume/finish, and real PCM microphone feedback pass on the OnePlus 5T. No speech accuracy
  benchmark or stress pass was run. The redesigned debug app is installed on the phone.

## Decisions for later

- Choose the human-recorded German/English acceptance corpus and validate provisional accuracy,
  latency, memory and recording-duration targets before treating these as product guarantees.
- Confirm final in-app typography/artwork and whether Android live mode merits the extra UI.
