# NONARA — Product & Technical Specification

**Version:** 1.1 (consolidated working specification)  
**Date:** 2026-11-01\
**Platform:** Android 13 / API 33 minimum; compile and target SDK 37  
**Product:** Minimal, text-first note-taking and pocket dictation  
**Status:** Product and STT specification; performance and visual defaults require validation

This is the single source of truth for product and implementation requirements, consolidating
the former product and STT documents. Implementation progress and remaining gaps are tracked in [IMPLEMENTATION.md](IMPLEMENTATION.md).
The app uses minSdk 33; this specification includes acceptance requirements that are not yet
fully validated. Package/application ID: `app.nonara.notes`.

---

## 1. Product vision

**NONARA is a quiet place for passing thoughts.** Open the app, type or speak, and move on. It should feel more like putting a thought down on paper than managing a productivity system.

**Guiding experience:** **Open. Capture. Forget about it.** NONARA remembers, so the user does not have to.

Five principles govern the experience:

1. **Thoughts, not documents.** A note may be a word, a sentence, or many paragraphs.
2. **Text is primary.** Voice dictation produces the same editable text note as typing.
3. **No ceremony.** No mandatory title, folder, tag, confirmation, or Save button.
4. **Local-first ownership.** Completed notes are human-readable `.md` files; no account is required.
5. **Zen over feature density.** Calm layouts, accessible controls, restrained motion, and reliable recovery.

### Decision status

- **Agreed:** Name NONARA; calm/Zen product direction; three principal screens (Home, Editor, Settings); recording as a focused overlay/state; text-first notes; Markdown body with YAML front matter; one file per note; plain-text editing; automatic saving; local-first approach; no folders, tags, account requirement, or cloud sync in v1.
- **Agreed brand direction:** Black Gothic/quill monogram for Android launcher; handcrafted burgundy monogram/lettering inside the app; parchment and warm neutral colors; restrained ink motif.
- **Implementation defaults:** UUIDv7 IDs, private app-specific file storage, in-memory search index, 400 ms edit debounce, 30-day trash, WorkManager for retryable Sherpa transcription, Jetpack Compose/Kotlin/DataStore. Timing and performance values are provisional tuning targets.
- **Resolved platform/privacy choices:** API 33 minimum; German/English on-device recognition; Sherpa as the only dictation backend with recoverable recordings; network access for explicit model downloads; no audio/transcript uploads; platform backup disabled; successful-capture audio deleted after durable commit.
- **Open decisions:** Exact typography/accessibility-tested colors, explicit-title editing UX, and performance tuning. Direct folder access remains outside v1.

---

## 2. Brand and visual language

### Name and personality

**NONARA** is a coined name inspired by playful Japanese-language word associations, particularly *onara* (fart). It is **not** a standard Japanese expression for “brain fart,” and the brand should not claim that it is. The joke is optional and understated; the actual product should feel thoughtful, warm, and respectful.

### Visual identity

| Surface | Intended treatment |
|---|---|
| Android launcher | User-supplied Gothic/quill monogram on parchment, installed from `tmp/NONARA_gothic_android_icons/res/`; preserve its supplied artwork |
| In-app branding | User-created burgundy hand-drawn **N**, without the discarded circle or unwanted extra strokes above it; complementary lowercase `nonara` wordmark |
| Primary background | Warm parchment/cream, with very subtle texture only where it does not impair legibility |
| Text | Charcoal/deep ink color; clear and modern body font |
| Accent | Burgundy/dark ink-red, used sparingly for primary actions and brand moments |
| Decorative language | An occasional ink drop, brush texture, or understated flourish—not ornamental UI chrome |

The supplied launcher package includes legacy densities, adaptive foreground/background layers and
API 33 themed monochrome icons. Its production resources are versioned under `app/src/main/res`;
previews and storefront images remain scratch assets. The former generated placeholder icon and
its generator are retired. Do not regenerate or redraw the supplied launcher artwork. In-app
burgundy artwork is a separate asset; use supplied artwork if available, otherwise a locally
generated placeholder without claiming it is the user’s original mark.

**Suggested exploration colors (not yet final accessibility-approved tokens):** parchment `#F5F0E7`, paper `#FFFCF7`, burgundy `#76283A`, charcoal `#302824`, muted secondary `#82766E`.

The brand can be expressive while the interface stays quiet. Use the Gothic motif for the mark, **not** for body text. Avoid noisy paper patterns behind editable content, excessive splatters, faux-antique controls, and busy gradients. Support light, dark, and follow-system appearance; test text and control contrast before shipping.

Optional brand line: **“Let it out.”** Another useful product sentence: **“A place for passing thoughts.”** Neither is required to appear in the app.

---

## 3. Information architecture and navigation

There are **three principal screens** and **one recording interaction**:

```text
Home (note collection)
 ├─ Search notes
 ├─ Tap note ───────────────> Editor (read / type / edit)
 ├─ Tap pencil ─────────────> Editor (new text note)
 ├─ Tap microphone ────────> Recording overlay/state
 └─ Tap settings icon ──────> Settings

Editor
 ├─ Back ──────────────────> Home, previous scroll/search position
 ├─ Tap microphone ────────> Recording overlay (append to note)
 └─ More ──────────────────> Copy / Share / Delete

Recording overlay/state
 ├─ Pause / Resume
 ├─ Cancel (confirm discard)
 └─ Finish ─────────────────> Pending transcript; user free to navigate
```

**No bottom navigation** is needed for v1. Home is the hub. Settings is a small header action. The main microphone action should be reachable with one thumb.

### 3.1 Home — “Your thoughts”

**Purpose:** Browse, find, and quickly capture thoughts.

**Structure:**

- Supplied NONARA vector wordmark at top left, tinted for the active theme, and an icon Settings action.
- Search field for note content and titles.
- Simple list of note cards/rows, with derived or explicit title, a brief text preview, and human-readable date.
- Group notes quietly into **Today**, **Yesterday**, and **Earlier** (calendar groups follow device-local date).
- Sort by `updated_at` descending, stable tie-break by `id`.
- Primary microphone button, secondary pencil/new-text action.
- Minimal empty state: **“No notes yet”**. No decorative headings, taglines or model switcher on Home.
- Pending dictated thoughts appear with statuses such as **“Transcribing…”** or **“Couldn't transcribe — Retry”**. Failed Sherpa recordings retain their audio for retry. A pending append appears on its target note rather than as a duplicate note.

Search is initially case-insensitive, over explicit title, derived title, and full body. No mandatory filters, tags, folders, or AI classification. For modest note collections, indexing content in memory is sufficient.

### 3.2 Editor — read and edit

**Purpose:** A single distraction-free surface for notes created by typing *or* voice.

- Back navigation returns to the Home list without an explicit Save step.
- Note text is readable immediately; **do not** automatically summon the keyboard when opening an existing note.
- Tap the text to edit. Use a minimal plain-text editor with intuitive selection, copy/paste, and multiline entry.
- Markdown syntax is **stored as plain text**. Rendering or toolbars for Markdown formatting are not required in v1.
- `title` is optional. When `null`, Home derives a title from the first meaningful body line.
- Display a very discreet status: **Saving…**, **Saved**, or **Couldn't save**. Only show **Saved** after durable persistence.
- Microphone action allows dictating additional text to the current note.
- More menu: Copy, Share, Delete. Delete supports immediate Undo and later restoration from Trash.
- Preserve cursor/selection and editor draft through ordinary lifecycle changes where possible.

**Dictation insertion:** Capture an explicit caret when present; collapse selections to their end rather than replacing selected text. Otherwise append with paragraph separation. If later edits invalidate the recorded body fingerprint, append with a notice. Never replace existing text with delayed transcription; section 7.5 defines the serialized commit.

### 3.3 Recording — focused overlay/state, not a fourth screen

- Start on a single tap once permission is granted; request microphone permission only when first needed.
- Show a modest waveform or levels indicator and a readable recording state; avoid excessive animation.
- Controls: **Pause/Resume**, **Cancel**, **Finish**.
- Cancel asks whether to discard an active recording.
- Finish releases the microphone, checkpoints available audio, schedules transcription/commit and returns control promptly. Sherpa retains audio for retry. See section 7 for the recovery guarantees.
- No forced transcription-review dialog and no manual Save button.
- The user can start another thought without waiting for the previous one to transcribe.

A tiny ink-drop settling into a note could be a subtle transition after Finish, with reduced-motion support. This is polish, not a blocker for v1.

### 3.4 Settings

Keep settings short and grouped:

1. **Appearance:** System / Light / Dark.
2. **Dictation:** Recording or Streaming, Deutsch or English, Sherpa model selection and installed model management. Streaming requires a streaming-capable model. Language choices remain explicit; models with automatic detection retain that native limitation.
3. **Privacy & data:** Export, Import, Trash/recovery, delete data and explanations of local transcription, temporary audio and disabled platform backup.
4. **About:** Version, licenses, feedback.

Action buttons use locally drawn icons, accessible names, tooltips and at least 48 dp touch targets. Selection chips retain concise labels. Model cards show DE/EN language chips and a Streaming chip where supported. Keep factual status/errors, sizes and licenses; remove promotional/decorative copy.

Defaults: theme follows system; Recording with Sherpa/base; German by default, with English only when explicitly
selected; temporary audio removed after a successfully persisted transcript; trash retention
30 days; no cloud sync, analytics or accounts. Do not advertise a keep-audio option in v1.

---

## 4. Permanent note file format — v1

**Source of truth:** One Markdown file per completed note, with YAML front matter followed by a plain-text/Markdown-compatible body.

**Canonical example** — filename `019a39a7-7e30-75b5-8200-870f54c58c4b.md`:

````markdown
---
schema_version: 1
id: "019a39a7-7e30-75b5-8200-870f54c58c4b"
title: null
created_at: "2026-10-31T18:42:00.000Z"
updated_at: "2026-10-31T18:42:00.000Z"
created_via: "voice"
---

Maybe I should build a little herb garden on the balcony.

Some basil, mint, and maybe tomatoes.

Could be a fun weekend project.
````

### 4.1 Metadata schema

| Key | Type | Required | Validation / meaning |
|---|---|---|---|
| `schema_version` | integer | Yes | Exactly `1` for this format |
| `id` | string | Yes | Canonical lowercase hyphenated UUID; UUIDv7 recommended for new notes; must match filename stem |
| `title` | string or `null` | Yes | `null` = no custom title; empty strings normalized to `null` on user edits |
| `created_at` | string | Yes | ISO 8601 UTC, canonical `YYYY-MM-DDTHH:mm:ss.SSSZ`; original creation instant |
| `updated_at` | string | Yes | Same timestamp format; last content/title modification, never earlier than `created_at` |
| `created_via` | string enum | Yes | `"text"` or `"voice"`; original creation method, not a live recording-status flag |

`created_via` stays unchanged when users edit a dictated note by typing or append dictation to a text-created note. A future schema may include edit provenance if genuinely useful.

The reserved optional scalar `last_applied_capture_id` is a durable idempotency receipt, not a
live recording status. Preserve it through edits/exports; see section 7.5 for its commit protocol.
Do not trust imported receipts as authority over this installation’s pending jobs.

**Not stored:** Derived title, preview, word count, search tokens, recording state, transcription progress, UI scroll position, or any cached index. These belong in derived or operational state.

### 4.2 Derived title

When `title: null`, derive from the first non-empty body line after trimming that line for display. Optionally strip a leading Markdown heading marker for the *display title only*. If the body is empty (e.g., a legacy/imported file), display **“Untitled thought”**. Truncate visually, not in the persisted body.

### 4.3 File naming and stability

- Filename: `<lowercase-uuid>.md`.
- Never name files after their titles; title changes must not rename files.
- `id` is immutable. Detect an imported ID collision and never overwrite silently. Generate UUIDv7 locally with a tested implementation using secure random bits; do not assume the Android platform exposes a UUIDv7 factory. Timestamp order is not a replacement for explicit creation/update fields.
- Treat directory paths as internal: do not allow `..`, path separators, arbitrary filenames, or external paths derived from untrusted note metadata.

### 4.4 Parsing and serialization rules

Implement a `NoteCodec` independent of Android UI code:

1. Decode as UTF-8. Accept an optional UTF-8 BOM on import, but never emit one.
2. Accept `\n` or `\r\n` line endings on read. Serialize front matter with LF (`\n`).
3. The first logical line (after optional BOM) **must** be exactly `---`.
4. The next standalone `---` line closes YAML front matter. A `---` in the body is just body text.
5. Front matter (measured as UTF-8 bytes, including delimiters) must be at most **16 KiB** and contain a restricted YAML map of **scalar keys/values only**: strings, integers, and null. Reject nested collections, aliases, anchors, custom tags, duplicate keys, and malformed YAML in v1.
6. Require and validate all v1 keys and values according to the schema. Parse dates as instants and reject invalid dates.
7. Reject invalid UTF-8 rather than silently replacing bytes. Canonical writer places **one blank separator line** after the closing `---`, then writes the exact body string. The canonical parser consumes **exactly one** separator blank line if present; any additional leading blank lines belong to the body. For noncanonical imported files without a separator blank line, treat content immediately after the closing delimiter as body. Preserve imported body bytes/characters through ordinary metadata edits wherever feasible.
8. Preserve body text, including meaningful whitespace, blank lines, punctuation, Markdown markers, Unicode, and trailing newline. Do not auto-format notes on open, autosave, or import.
9. Serialize metadata in the defined field order, quote strings and timestamps, and write `title: null` literally when unset. Escape YAML strings using a proper serializer rather than manual interpolation.
10. Preserve unknown **simple scalar metadata** round-trip in a separate extension map. Do not interpret it. Unknown complex YAML structures are unsupported in v1 and must not be silently discarded.
11. If `schema_version` is newer than supported, expose the raw note read-only where safely possible. Do not downgrade or overwrite it.
12. If parsing fails, do **not** delete, repair automatically, or rewrite the source file. Report that the file needs attention and preserve its original bytes for recovery/export.

**Round-trip invariant:** Parsing and serializing a note without user changes must not change the meaning or content of its body. The app may canonicalize known metadata on an intentional write, but must not rewrite an untouched file merely because it was viewed.

### 4.5 Data model

```kotlin
data class Note(
    val id: UUID,
    val title: String?,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdVia: NoteSource
)

enum class NoteSource { TEXT, VOICE }
```

Serialization should accommodate preserved simple extension metadata, even if that preservation is implemented in a separate parsed-file wrapper rather than the UI-facing `Note` object.

---

## 5. Filesystem layout and ownership

**v1 default:** Android private app-specific persistent files (`filesDir`), not public storage. This avoids broad storage permissions, but makes explicit export/backup essential. Uninstall can remove app-specific data.

```text
filesDir/                     # App-private persistent root
├── notes/
│   ├── <note-id>.md
│   └── <note-id>.md
├── pending/
│   └── <capture-id>/
│       ├── capture.json
│       ├── audio/             # Sherpa only: recoverable PCM chunks
│       └── transcript.json    # Durable segment results / commit recovery
├── trash/
│   └── <note-id>.md
├── recovery/                  # Recoverable editor drafts / operation journal
└── models/                    # Verified Sherpa/VAD files, excluded from exports
```

- **`notes/`:** Completed human-readable Markdown notes; permanent source of truth.
- **`pending/`:** Durable capture metadata, recognized segments and audio while pending, processing, failed or awaiting commit.
- **`trash/`:** Soft-deleted `.md` files, restorable by ID; track deletion time in a small operational record or sidecar so cleanup is deterministic without modifying the note's original metadata.
- **`recovery/`:** Optional compact journal for user edits that have not yet been committed to their canonical note file. Must be recoverable and bounded.

Do not mix pending audio or live job state into completed note files. The scalar commit receipt
defined in sections 4.1/7.5 is the sole operational exception, required to atomically couple a
transcript insertion with its identity. Temporary file locations can change without a
note-format migration.

### 5.1 Source-of-truth and cache boundary

| Data | Storage | Durable authority? |
|---|---|---|
| Completed note body + metadata | `notes/*.md` | **Yes** |
| Deleted notes during retention | `trash/*.md` | **Yes, until purged** |
| Capture records, recognized text and audio | `pending/` | **Yes, while incomplete** |
| Editor draft before commit | Recovery journal | **Temporary durable recovery** |
| Search tokens, list preview, derived title | In-memory index | No; rebuildable |
| Theme / dictation preferences | Jetpack DataStore | Yes, for preferences |

The in-memory index is always rebuildable from `notes/`; deleting it must never delete notes. Consider SQLite/FTS later only after measurement shows a need.

---

## 6. File writing, autosave, and consistency

### 6.1 Editor saving policy

- Update the visible editor state immediately on typing.
- Debounce normal persistence by approximately **400 ms**; implementation can tune this after testing.
- Serialize a complete note into an atomic replacement, not incremental writes into the middle of an existing `.md`.
- Use Android `AtomicFile` or equivalent reliable same-filesystem write/commit strategy in private storage. Always correctly handle abort/fail paths.
- Serialize writes to the **same note** with a per-note mutex/actor and a monotonically increasing edit revision. Old writes must never overwrite newer revisions.
- On navigation away, lifecycle stop, and other appropriate boundaries, request an immediate flush of the latest draft rather than waiting for debounce alone.
- Do not report **Saved** before the latest revision is durably committed.
- Journal edit revisions for recovery before the debounced canonical write; keep the journal bounded and recover on startup. Report persistence failure explicitly. Only edits durably journaled or saved are guaranteed after process death; UI-visible keystrokes still awaiting storage can be lost. Do not promise recovery of every keystroke after sudden termination.
- Avoid writing empty new note files until the user has entered meaningful content. Clearing an existing note does not silently delete it.

### 6.2 Concurrent edits / external changes

v1 owns its app-private note directory, so concurrent writers should be rare. Still, repositories must serialize all app-originating changes. If future public-folder access or sync is added, implement file change detection and conflict copies rather than last-writer-wins overwrites.

### 6.3 Durability invariants

1. A failed replacement never destroys the last valid committed note.
2. A newer edit cannot be overwritten by an older asynchronous save.
3. A UI status of **Saved** means the latest displayed revision is persisted.
4. Durable Sherpa audio and recognized segment journals remain recoverable until commit.
5. A retry must not create duplicate notes or duplicate appended text.

---

## 7. Dictation and speech recognition

### 7.1 Single backend and capability boundary

Text is the permanent result; audio is a temporary recovery artifact. All recognition stays on
this device. Sherpa is the only dictation backend:

| Backend | Microphone owner | Input to recognizer | Retry after process death |
|---|---|---|---|
| Sherpa | App AudioRecorder | Durable 16 kHz mono PCM16 chunks | Retained audio + segment journal |

Use Sherpa for the record → Finish → retryable transcription flow. Never fall back to a
generic/cloud recognizer or another on-device speech service. A new recording requires an
installed model that supports the selected language; when unavailable, direct the user to
Settings. Never download a model automatically when dictation starts.

Settings changes to language/model apply to new captures. Existing jobs retain their pinned
configuration. Only one microphone session is active; a new capture may begin after Finish has
released it while earlier Sherpa jobs wait/process. Serialize inference jobs to limit memory.

### 7.2 Recording overlay and durable capture

- Home creates a capture with a predetermined target note ID; Editor binds the current note
  and caret/body revision. Capture a collapsed insertion anchor, never a replacement selection.
- Controls are Pause/Resume, Cancel (confirm discard), Finish. No manual Save or mandatory review.
- Sherpa writes numbered PCM chunks to private persistent storage during recording, not only at
  Finish. Flush/sync chunks and atomically checkpoint committed sample counts at least once per
  second and on pause/finish. Keep the currently written chunk identifiable; recover complete
  samples only. Never imply the uncheckpointed tail is guaranteed after abrupt termination.
- Pause closes/checkpoints capture input and releases the microphone. Resume starts a new chunk;
  silence during the pause is not part of the recording. Resume is disabled while the previous
  capture is still stopping.
- Finish first releases capture, finalizes its durable record, then returns control promptly.
  Sherpa enqueues transcription; never wait for inference or invent a final.
- Cancel marks the capture discarded before cancelling jobs/deleting its artifacts. No note
  insertion occurs before Finish. Workers must recheck discard state under the commit lock.
- Initial recording limit: 30 minutes of captured audio per thought, with a visible warning
  before automatic Finish. Paused time is excluded. Limit all pending capture artifacts to
  256 MiB initially; check free space plus headroom before/during capture and stop safely before
  exhaustion. Never delete a failed recording to make room without an explicit discard.
- On interruption/backgrounding, stop capture and checkpoint recoverable content; expose an
  interrupted card with Resume/Finish/Discard as appropriate. Do not automatically resume or
  automatically submit a recording the user has not finished. On process restart, a previously
  active capture becomes interrupted rather than pretending its microphone is still active.

Private `capture.json` includes a schema version, capture ID, predetermined target note ID,
creation time, mode, language (`de-DE` / `en-US`), model/runtime version, state, attempt count,
committed audio chunks/sample counts, recognized segment IDs, insertion policy/body fingerprint,
last error, operation receipt and commit status. Store no `auto` language in v1. Migrate this
operational format independently of permanent notes. Unknown future records are preserved and
shown as unsupported, not overwritten.

State path: recording ↔ paused; Finish from either → pending → transcribing → committing → completed. Interrupted,
failed and discarded are explicit alternatives; retry returns failed work to pending. A state
change and its referenced artifacts must be ordered so recovery never references missing data.

### 7.3 Contracts, events and cancellation

Do not force saved-audio transcription and microphone capture behind one `start()` interface.
Use these separate responsibilities:

- `AudioRecorder`: permission-aware capture, pause/resume, bounded chunk persistence and finish.
- `TranscriptionEngine`: availability and decode of a durable capture; never opens a microphone.
- `DictationCoordinator`: durable state, deduplication, retries, scheduling and note commit.

Availability is READY / NEEDS_MODEL / UNSUPPORTED / UNKNOWN, with actionable reasons. A missing
model installation is NEEDS_MODEL; an incompatible model/language pair is UNSUPPORTED. No
availability check starts a microphone or a download.

Each processing attempt has a unique attempt ID. Events carry that ID and comprise Final
(immutable text and stable segment ID), Error (typed category), and Ended (one terminal
reason). Reject callbacks from older attempts. Empty finals are omitted. Sherpa segments have
persisted boundaries and stable identities across retries; replace a retry's uncommitted
result for the same segment instead of appending duplicates. Never use transcript text equality
for deduplication.

A coordinator-owned single consumer journals finals in order, then projects UI state. Compose
collectors must not own commits or replay them after rotation. Use bounded buffers. Never
silently drop finals or block the audio/main thread on storage. Sherpa reads one segment at a
time from disk, avoiding an unbounded RAM audio queue; a storage failure stops processing.

Start returns when the microphone is ready or fails through one typed path; it cannot wait for
the full session. Stop is idempotent, releases the microphone and cancels scheduled work. Stop
during startup must cancel acquisition so a microphone cannot open later. End an attempt
exactly once; no transcript event may follow Ended. Release is idempotent and terminal. Do not
hold lifecycle locks across starting/stopping that would prevent cancellation.

Expose capture state (idle/loading/listening/paused/stopping/interrupted) separately from job
state (pending/transcribing/committing/failed/completed). A processing indicator must not imply
that a microphone is active. Errors persist in UI until retried/dismissed.

### 7.4 Retryable Sherpa execution

Use uniquely named WorkManager work per capture, backed by the durable capture record. Scan
pending/interrupted records on startup and reconcile missing jobs; WorkManager state alone is
not the content authority. Process a bounded segment at a time and checkpoint outputs. Stop
cooperatively, resuming unfinished segments on later execution. Cancellation/time limits must
not mark a capture completed or delete its audio. Avoid releasing native objects while decode
still uses them. Do not assume a worker runs instantly or indefinitely; checkpoint/yield within
its execution budget rather than requiring unrestricted background compute.

Record → Finish must not wait for inference. Worker transient retries are bounded (initially
three automatic attempts with backoff); then expose manual Retry. Model missing/corrupt,
permission, storage, discarded target and unsupported input errors need user action rather than
endless retries. Reuse durable segment results after partial failure. No cloud fallback.

### 7.5 Exactly-once insertion and recovery

1. Persist the complete assembled transcript and a commit intent with immutable capture ID,
   target note ID and intended insertion before changing a note. Silence/empty output shows a
   no-speech outcome and offers Retry/Discard; it must not create an empty voice note.
2. Under the per-note write actor, flush/reconcile newer editor revisions, re-read the latest
   note and decide insertion. If its body fingerprint still matches the captured body, insert
   at the captured caret. With no explicit caret, append with paragraph separation. If the
   body changed, append at the end with a non-blocking notice rather than guessing an offset.
   Never replace a selected range or overwrite a stale whole-note snapshot. If the note is
   deleted, read-only or invalid, fail safely and offer recovery into a new note; do not
   resurrect it or silently recreate its old identity.
3. Atomically write the resulting Markdown file with `last_applied_capture_id` set to this
   capture's ID in the same file replacement. The receipt and body must commit together. For
   new voice notes use the predetermined ID, capture creation time and `created_via: voice`;
   insertion into existing notes preserves their original creation time/source.
4. Durably mark the capture completed before permitting another capture insertion into the same
   note. Recovery resolves in-flight commit intents before subsequent writes: a matching local
   receipt means already applied, so complete the record without inserting again. Do not infer
   success merely from matching text. Before a receipt is resolved, preserve it through edits,
   undo and deletion; serialize these operations behind commit recovery.
5. Delete audio only after completion is durable. Failed deletion retries cleanup without
   re-transcribing/re-inserting. Keep completed/discarded tombstones until workers are cancelled
   and stale work cannot reapply. An imported receipt cannot complete an unrelated local job.

Persist one undo operation per completed capture, with before/after revision checks; undo
serializes through the same actor and must not erase later edits. After restart, if the undo
history is unavailable, do not offer a destructive approximate undo. A save error preserves the
transcript/audio, exposes Retry/Copy, and never reports Saved. A Cancel after completion is an
ordinary note edit/delete operation, not a way to race an already committed worker.

### 7.6 Sherpa models and audio processing

- Pin sherpa-onnx Kotlin/JNI and ONNX Runtime versions/ABIs. Validate API 33 and native-library
  packaging on supported devices before adopting a prebuilt distribution.
- Downloadable int8 models: multilingual Whisper Tiny/Base/Small/Medium/Turbo/Large v3, NVIDIA
  Parakeet TDT 0.6B v3, Canary 180M Flash, Meta Omnilingual ASR CTC 300M and multilingual
  Nemotron 3.5 ASR Streaming 0.6B (160/560 ms), German FastConformer transducer/CTC int8 and
  German Kroko streaming Zipformer. German is the primary language and default; every model must support German.
  English remains optional. Whisper Base remains the default. Keep the
  existing Base/Small IDs and versions compatible with installed models and pending captures.
- Configure each model family explicitly: Whisper encoder/decoder with `transcribe` and
  `de`/`en` (128 mel bins for Turbo/Large v3); Parakeet encoder/decoder/joiner with
  `nemo_transducer`; Canary with matching source/target language to transcribe without translation;
  Omnilingual with its CTC decoder; Nemotron with its streaming transducer and an explicit language
  option set before each stream's audio; German FastConformer uses its NeMo transducer or CTC
  export, and Kroko uses its native streaming transducer with 80 mel bins. Omnilingual CTC has
  no forced-language option.
  Nemotron consumes the same saved VAD segments through bounded PCM chunks, signals end of input
  and drains the final frames. This saved-segment adapter does not own a microphone; the separate
  live preview follows the app recorder's saved PCM. Kroko saved segments receive trailing silence
  to flush their final frames.
  Keep one model loaded.
  Model selection preserves the chosen language; reject incompatible selections.
- Maintain the full German-capable reference list, including available versions/precisions and
  source links, in [MODEL_CATALOGUE.md](MODEL_CATALOGUE.md). These entries are not all app downloads.
- Read durable 16 kHz mono PCM16 audio, convert to the pinned runtime's expected samples, run
  Silero VAD and serial model inference on a dedicated dispatcher, never on the main thread.
- Initial VAD tuning: 250 ms minimum speech, 500 ms minimum silence, 200 ms padding and maximum
  10-second recognition segments including padding. Force splits during continuous speech,
  flush the final incomplete frame/segment, and avoid duplicated samples at boundaries. Persist
  the segmentation/configuration so retry results retain stable segment identities.
- Keep one decode segment plus bounded read-ahead in RAM; the full recording remains on disk.
  Start at four inference threads and benchmark fewer before exposing tuning controls.
- Whisper is non-streaming: show progress/status, never a replaceable live transcript. No periodic
  re-decoding in v1. Preserve punctuation; filter empty output. Any hallucination/repetition filter needs tests
  proving it preserves deliberate repetitions and short answers such as “ja” and “no”.

### Live streaming preview

Streaming is an optional Sherpa capture mode, selected in Settings. It requires an installed,
German-capable streaming model compatible with the chosen language. Selecting an incompatible
model or language is disabled/rejected without silently changing language. Settings apply to new
captures; a running capture retains its model and preview mode.

The app recorder still owns the microphone and saves PCM through the same recovery path.
Live inference tails those saved files in bounded chunks on a background dispatcher and shows
replaceable preview text in the recording panel. Endpoint text accumulates once; partials replace
only the current utterance. Preview text is transient and never appended directly to a note.
Pause, Finish, discard and ViewModel cleanup cancel preview work and release native resources.
Finish still queues final VAD transcription from saved audio and the existing receipt-based commit.
No note/capture storage schema or import/export format changes are needed.

Inference is serialized with saved-audio jobs to limit memory. If preview cannot acquire the
inference slot or fails, show a concise status and keep audio recording; Finish can still queue
transcription. Loading/decoding must never block microphone writes or run on the main thread.
Whisper remains a recording-only model; do not emulate streaming by repeatedly decoding it.

### 7.7 Model installation

Production Sherpa models are downloaded after an explicit action, never bundled in the APK or
silently fetched by opening dictation. A fresh install may require connectivity; installed
models support offline recognition. Show model availability and download/installed size first.

Use a pinned catalog with ID/version, runtime compatibility, languages, license/provenance and
every required file (family-specific model components, tokens, Silero VAD and metadata), sizes and SHA-256.
Download over HTTPS into app-private staging, verify all files, then atomically publish a
complete versioned directory under `filesDir/models/`. A failed install cannot replace a working
model. Prefer individual files over archive extraction; provide applicable license notices.

Transfers belong to unique foreground WorkManager work, independent of the Activity/ViewModel,
with a data-sync progress notification and Cancel action on API 33+. Persist model ID/version,
catalogue fingerprint, work ID, phase and aggregate bytes. Reopening Settings reconnects to
queued/downloading, interrupted, verifying, installed or failed work. Request notification
permission on download; denial hides the drawer notification but never implies completion or
prevents tracking/cancelling in Settings. Declare foreground-service/data-sync permissions and type.

Keep partial data after cancellation, network failure or process death. Resume/Retry is explicit;
Cancel never schedules a restart, and Discard partial download explicitly removes staged data.
Only append an identity-encoded HTTP 206 whose Content-Range starts at the actual local file
length and matches the pinned total size; use a persisted strong ETag or Last-Modified with
If-Range when available. Resolve redirects anew on each attempt. A 200 replaces that file;
416, changed identity or invalid ranges trigger at most one safe restart per file per attempt.
Bind staged bytes to all pinned URLs/sizes/hashes and verify full size/SHA-256, including the
resumed prefix, before writing the complete marker and atomically renaming the directory.
Retain verified files across retries. Budget only remaining bytes plus a 32 MiB safety margin;
serialize transfers, retry, discard and installation/deletion, and throttle progress updates.
Transient network/HTTP 408/429/5xx failures get at most three automatic attempts, with exponential
30-second backoff; checksum, storage and other permanent failures require explicit Retry.

Screen lock, navigation and Activity recreation do not cancel transfers. OS suspension,
force-stop, Android foreground-service time limits and (on Android 16+) WorkManager job quotas
can still interrupt/delay work; show recoverable progress without promising uninterrupted
execution under every device power policy. See [Android long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
and [notification permission behavior](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Show installed versions/storage; refuse deletion while loaded or pinned by pending jobs unless
the user explicitly discards those jobs. Shared VAD deletion cannot break another installed
model. Failed model loading revalidates installation and offers repair, not a download loop.

### 7.8 Permissions and lifecycle

Declare RECORD_AUDIO and INTERNET. Request microphone permission only on dictation action;
handle denial/permanent denial without blocking typed notes. Network access is allowed for
explicit model provisioning; never upload notes, audio or transcripts. No cloud STT in v1.

Capture is foreground-only: no background listening, microphone foreground service or automatic
resume in v1. Backgrounding/screen lock/calls/capture silencing stop capture, checkpoint content
and expose interruption recovery. Do not require phone-state permission simply to detect audio
interruptions; use available recording callbacks/read errors and lifecycle signals. Distinguish
foreground rotation from actual backgrounding, with one retained capture owner and event
consumer. A device-specific inability to detect an interruption must be reported during QA.

Saved-audio inference may continue as eligible WorkManager work after the UI leaves; this is
separate from microphone use. Lifecycle cleanup must release capture even if inference/journal
work is slow. ViewModel clearing/process death cannot guarantee the uncheckpointed tail; never
promise recovery beyond successfully persisted audio.

References: [Sherpa Whisper](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/index.html),
[Sherpa Kotlin VAD](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/Vad.kt).

---

## 8. Android application architecture

**Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, ViewModel, Kotlin Coroutines/Flow, Preferences DataStore, and WorkManager for durable Sherpa jobs. Keep the architecture lean; avoid needless layers.

```text
UI (Compose)
  HomeScreen | EditorScreen | SettingsScreen | RecordingOverlay
           ↓ user actions / observable state
ViewModels
  HomeViewModel | EditorViewModel | SettingsViewModel | RecordingViewModel
           ↓
Repositories / coordinators
  NoteRepository | CaptureRepository | PreferencesRepository
           ↓
Storage and platform adapters
  NoteCodec + NoteFileStore | AudioRecorder + TranscriptionEngine | DataStore
           ↓
  Markdown files           Pending audio/jobs            Preferences
```

### 8.1 Responsibilities

| Component | Responsibility |
|---|---|
| `Note` | In-memory note domain model |
| `NoteCodec` | Strict YAML/Markdown parsing and serialization; Android-independent |
| `NoteFileStore` | Atomic reads/writes, directory scanning, move-to-trash, recovery |
| `NoteRepository` | Creation, updates, observing, search, cache invalidation |
| `CaptureRepository` | Pending audio records, state transitions, retries, idempotent commit |
| `TranscriptionEngine` | Saved-audio decode adapter; microphone-free |
| `AudioRecorder` | Foreground microphone ownership, durable PCM capture and pause/resume/finish |
| `PreferencesRepository` | DataStore-backed user preferences |
| `HomeViewModel` | List/search state, pending items, navigation actions |
| `EditorViewModel` | Read/edit state, autosave coordination, append entry point |
| `RecordingViewModel` | Recording controls/status, permission and cancel UI |

### 8.2 Suggested repository API

```kotlin
interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun getNote(id: UUID): Note?
    suspend fun createNote(body: String, source: NoteSource): Note
    suspend fun updateNote(note: Note)
    suspend fun moveToTrash(id: UUID)
    suspend fun restoreNote(id: UUID)
    suspend fun search(query: String): List<Note>
}
```

Implementation-specific APIs should additionally expose edit revision / persistence state and provide a safe operation for idempotent transcript insertion. `updateNote(note)` alone is insufficient for conflict-safe asynchronous append.

### 8.3 Example module/package layout

```text
app/src/main/java/app/nonara/notes/
├── app/                 # Application, DI wiring, app-level navigation
├── core/model/          # Note, NoteSource, capture models
├── core/codec/          # YAML/Markdown codec, validators
├── data/notes/          # NoteFileStore, NoteRepository implementation
├── data/capture/        # Pending capture store, transcription coordinator
├── data/preferences/    # DataStore preferences
├── platform/audio/      # Recorder and permissions
├── feature/home/        # Home screen + ViewModel
├── feature/editor/      # Editor screen + ViewModel
├── feature/recording/   # Overlay + ViewModel
├── feature/settings/    # Settings screen + ViewModel
└── ui/theme/            # Colors, typography, adaptive appearance
```

A single Android app module is sufficient initially. `NoteCodec` should remain independently unit-testable without Compose or Android file APIs.

---

## 9. Search and list projection

- Initial implementation: scan `notes/*.md` on startup, parse, index the note bodies and titles **in memory**, and emit list updates through `Flow`.
- Sort by `updated_at` descending, then `id` for deterministic ties.
- Search explicit/derived title and body, case-insensitively; optimize only after measuring typical datasets.
- Previews, grouping by local calendar day, search matches, and derived titles are **computed**, not stored in Markdown metadata.
- Pending new-note cards are projected from `pending/`; pending appends decorate their target note instead of duplicating it. Retry actions use the retained Sherpa audio.
- A malformed note should not crash the whole collection scan; quarantine it logically in the UI while preserving its file bytes.
- Rebuildability is a strict requirement: clearing the index must leave all user content intact.

---

## 10. Preferences, privacy, and backup

### 10.1 Preferences model

```kotlin
data class AppSettings(
    val theme: ThemeMode,             // SYSTEM
    val languageTag: String,          // de-DE by default; en-US only when explicitly selected
    val sherpaModelSize: ModelSize,   // BASE by default
    val trashRetentionDays: Int       // 30
)
```

Use Preferences DataStore. Explain model provisioning and the single Sherpa backend. No automatic
language detection, automatic capability-downgrading fallback, alternate speech service, cloud
recognition or long-term audio-retention option in v1. These are resolved scope choices, not
undecided settings.

### 10.2 Privacy, backup and retention

- No accounts, analytics, ads or cloud sync. Network access is allowed, with v1 transfers limited
  to explicit model provisioning. Models remain local; never upload audio, text or notes.
- Sherpa recognition runs on device. Initial setup can need connectivity; installed models must
  be tested offline.
- Keep raw Sherpa audio private and delete after durable note commit. Retain failed/interrupted
  recordings until explicit Retry/Discard or completion. Show pending storage usage; no silent
  age-based purge of failed captures in v1.
- Platform cloud backup and automatic device transfer are disabled by default. Keep
  `android:allowBackup="false"` and configure supported backup/data-extraction rules to exclude
  notes, drafts, pending captures, models and preferences. Verify actual device behavior; do not
  claim this defeats vendor-specific copying or user-driven exports. Tell users that uninstall
  or device loss can remove private data and explicit export is required for their backup.
- Do not claim application-level encryption or secure erasure beyond the actual implementation.
- Delete-all-data is confirmed explicitly: stop capture, cancel/join jobs, fence new commits,
  then remove notes/trash/pending/recovery state and clear derived caches/preferences. Let the
  user choose whether installed models are removed too. Stale workers must not recreate data.

---

### 10.3 Export and import

**Export all notes** should be a v1 feature. Via Android's system document picker, write a ZIP containing at least:

```text
nonara-export.zip
└── notes/
    ├── <note-id>.md
    └── <note-id>.md
```

Optionally include `trash/` with an explicit user choice; exclude pending captures/audio, recovery journals, models and preferences. Explain that unfinished captures are not part of the note export. Take a consistent snapshot under repository coordination; report partial export failure and do not present a truncated ZIP as successful. Keep the exported note files portable and human-readable without requiring NONARA to open them.

**Import:** Validate YAML and UUID filenames; reject absolute/traversing paths, symlinks and duplicate archive paths. Set bounds on entry count, per-note size, total expanded size and compression ratio before allocation/extraction. Treat limits as explicit errors, not truncation. Import each valid note atomically; report imported/skipped/conflicting/unsupported files. Keep conflicting IDs untouched and offer an explicit separate-copy action with a new ID. Preserve bodies/timestamps and permitted metadata. Future schemas remain read-only/unsupported. Never overwrite an existing file or trust imported capture receipts to finish local jobs. Rebuild the index after import.

**Retention:** Soft-delete to `trash/`, offer immediate Undo, restore within 30 days and purge on startup/eligible maintenance after expiry. Use a recoverable move intent so crashes between file move and deletion-sidecar update cannot lose the note or its deletion time. Preserve ID/body/timestamps on restore; conflicts must not overwrite a live note. Deleting a target with pending dictation requires resolving/discarding that append first; never resurrect a trashed note from a late job. Discarding unfinished audio is separate from deleting a note.

**Direct folder access:** A user-selected folder via Android's Storage Access Framework is a possible future enhancement, not a v1 requirement. It introduces permissions, provider-specific write semantics, and conflict-resolution questions.

---

## 11. Accessibility, performance, and UX quality

- Large, thumb-friendly recording controls; correctly labeled accessibility actions.
- Dynamic text scaling, system font size support, contrast checks, and TalkBack-friendly screen descriptions.
- Honor reduced-motion preferences where available. Recording must remain understandable without animated waveform motion.
- Never communicate recording/persistence state through color alone.
- Handle permission denial without blocking the text editor.
- Keep the app responsive while indexing files, recording, transcribing, or saving; perform disk and CPU work off the main thread.
- Preserve Home scroll position and search query when opening and closing notes.
- Avoid a modal transcription “waiting room.” Pending work should be visible but unobtrusive.
- Test with empty collections, thousands of small notes, long notes, non-Latin scripts, and low-storage conditions.

---

## 12. Scope for v1

### Included

- Home: browse notes, derived titles, timestamps, search, pending capture status.
- Create/edit plain-text notes; optional custom titles; reliable autosave.
- Microphone recording with Pause/Resume, Cancel/Discard, Finish.
- On-device Sherpa transcription with durable retryable audio; creates/appends editable note text.
- Recovery from failed/interrupted transcription and interrupted writes.
- Soft delete, Undo, Trash, restore, automatic retention purge.
- Settings: theme, supported language choice, privacy explanations, data export/import.
- Private local file storage; Markdown + YAML interoperability.
- Android adaptive launcher branding and quiet parchment/burgundy in-app aesthetic.

### Deliberately out of scope

- Accounts, mandatory sign-in, cloud synchronization, real-time collaboration.
- Folders, user tags, AI categorization, summaries, semantic search.
- Complicated rich-text or WYSIWYG editor.
- Mandatory custom titles or explicit Save buttons.
- Home-screen widgets and shortcuts beyond essential launcher functionality (possible later).
- Long-term original-audio archive, cloud STT, automatic language detection, background microphone capture, and automatic switching to another recognizer.

---

## 13. Acceptance criteria and test plan

### Note format and files

- [ ] Creating a typed note produces exactly one valid `.md` file with matching UUID filename/front matter.
- [ ] Reopening a note preserves text, metadata, Unicode, emoji, and meaningful whitespace.
- [ ] `---` within a note body does not split front matter or corrupt parsing.
- [ ] CRLF/BOM imports work and subsequent controlled writes use canonical serialization.
- [ ] Duplicate YAML keys, unsupported types, bad dates, and mismatched IDs are rejected without altering source bytes.
- [ ] Unknown permitted scalar metadata survives supported rewrites; future schemas are not silently downgraded.
- [ ] Editing a note's explicit title does not rename its file.
- [ ] A failed atomic write leaves an earlier valid note and/or recoverable newer draft.
- [ ] Rapid typing and concurrent write attempts never regress to older revisions.
- [ ] A clean index rebuild reproduces the same visible note collection.

### Recording and transcription

- [ ] First microphone use handles grant/deny/retry appropriately.
- [ ] Pause, Resume, Finish, and Cancel have predictable UI and recording behavior.
- [ ] Interruption stops the microphone and recovers checkpointed audio/text, with possible uncheckpointed-tail loss disclosed.
- [ ] Pending captures remain visible; Sherpa retries retained audio and reports failures.
- [ ] A new successful transcription creates one note, even after crash/retry.
- [ ] Dictating into an existing note does not replace newer user edits or insert text twice.
- [ ] Sherpa audio is deleted only after note/receipt/completed-record persistence; cleanup retries cannot insert twice.
- [ ] Crash injection before/after each journal/note/completed-record boundary proves no duplicate insertion and no deletion of required recovery audio. Discard/delete/undo racing a worker cannot resurrect content.

### User experience and privacy

- [ ] No user action is needed to assign a title, category, or Save command.
- [ ] Existing note opens for reading without unexpected keyboard focus.
- [ ] Back navigation preserves prior Home position and search state.
- [ ] Screen-reader labels, touch targets, contrast, and large fonts are verified.
- [ ] Export/import round-trips permanent note files without silent loss or overwrite.
- [ ] Deleted note can be restored with the same identity and body.
- [ ] UI accurately describes offline provisioning, on-device recognition, retryable Sherpa recovery and disabled platform backup.

---

### Speech engines, models and platform acceptance

- Follow repository TDD: write failing behavioral tests first; every milestone includes tests.
  Mock echoes/wiring-only checks do not establish correctness. Run relevant JVM and instrumented
  suites on implementation handoff; report unavailable device cases.
- Primary physical device: OnePlus 5T (`ONEPLUS A5010`), Android 13/API 33. A Pixel 6 can be an
  additional benchmark, not a substitute. Use no emulator without explicit user approval.
- JVM tests cover readiness combinations, start/stop during loading, repeated stop/release,
  pause/resume, journal backpressure, retry limits, segmentation/retry identity,
  no-match/silence, and cancellation during pending/commit.
- Device tests cover permission denial/revocation, missing installed model, incompatible
  model/language, airplane mode after installation, calls/capture contention,
  rotation/backgrounding, screen lock and rapid start/stop. Verify stop releases the mic.
- Model tests cover missing components, checksum failure, interrupted/resumed downloads with
  changed remote identity, disk full, atomic publication, cancellation, shared VAD references
  and attempts to delete models pinned by a pending job.
- Use locally recorded or user-supplied German/English test clips and versioned reference text:
  short answers, intentional repetition, numbers, punctuation, silence, noise and continuous
  speech; 30-second and 5-minute samples. No third-party audio downloads. Inject saved PCM into
  Sherpa rather than assuming live file injection.
- Provisional OnePlus 5T base-model gates: zero nonempty finals from silence; clean-speech WER
  ≤ 20% for each language (lowercase, punctuation ignored); median RTF < 0.5 over repeated
  5-minute clips; p95 inference latency ≤ 5 seconds per closed 10-second segment; peak process
  PSS ≤ 1 GB; no unbounded memory growth/crashes. RTF = decode wall time / decoded audio duration.
  Measure Finish-to-complete job latency separately: batch transcription of a long recording
  cannot promise a 5-second total result. Record corpus/runtime/model versions, thermal state,
  cold/warm loading and thread count; report noisy-input accuracy separately.
- Compare base/small on the actual device. Treat gates as hypotheses to validate in the spike,
  not achieved results. Small must fail gracefully on resource exhaustion. Freeze the corpus
  and full normalization procedure before declaring accuracy gates passed.
- Verify the app never constructs a general/cloud recognizer or uploads note/audio data. App
  network inspection alone does not audit the separate device-supplied recognition service.
- Validate supplied legacy/adaptive/themed icons under launcher masks; preview artwork alone
  does not establish actual device launcher behavior.

---

## 14. Implementation order

1. **Domain and codec:** `Note`, strict YAML front matter parser/serializer, validators, and round-trip tests.
2. **File store and reliability:** UUID filenames, safe directory handling, atomic commits, revision serialization, recovery, trash.
3. **Text experience:** Home, search, plain-text editor, autosave states, preferences, basic styling.
4. **Import/export:** Portable ZIP export, safe import, index rebuild, privacy/backup configuration.
5. **Platform/capture:** Use minSdk 33; add microphone/network declarations; foreground PCM capture, pause/resume, interruption checkpoints and overlay.
6. **Sherpa spike and provisioning:** Pin runtime/models; temporarily provision a verified base model in private storage for development (not a bundled production model); benchmark on the OnePlus 5; build explicit downloads with verification/atomic installation.
7. **Transcription/recovery:** Segment checkpoints, WorkManager, atomic receipt-based insertion, recovery and cleanup.
8. **Polish and QA:** Accessibility, dark mode, low-storage/crash testing, performance, quiet animations, release validation.

---

## 15. Remaining product validation

| Topic | Validation needed |
|---|---|
| Recognition tuning | Base accuracy and latency on the OnePlus 5; VAD and thread tuning before changing defaults |
| Recording/storage limits | Validate provisional 30-minute / 256 MiB pending limits with realistic use |
| Notes editing | Exact optional-title editing UX; no mandatory title or Markdown highlighting |
| Visual tokens | Final body typography, dark palette, contrast and spacing; supplied launcher art remains authoritative |

The Sherpa-only engine choice, offline recognition, API 33 minimum, explicit model downloads,
audio cleanup and backup defaults are resolved above. Changes to these choices require updating this specification;
do not retain contradictory alternative contracts in separate files.

---

## 16. Definition of success

A user should be able to open NONARA, type or speak one thought, leave, and later retrieve it without worrying about its name, location, or whether it was saved. The implementation is successful when **completed thoughts remain portable Markdown files, Sherpa recordings recover within their documented durability limits, and the interface stays calm when work fails**.

*End of specification.*
