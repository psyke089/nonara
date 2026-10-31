# Dictation Note App: Speech-to-Text Spec

## 1. Goals
- Offline dictation into notes on Android (target: Pixel 6, Android 13+).
- German primary, English secondary.
- Free/open-source stack. Native Kotlin.
- Two interchangeable STT backends selectable in settings:
  - **sherpa-onnx** (primary, Whisper models, open source)
  - **Android SpeechRecognizer** (on-device, fallback)

## 2. Non-Goals (v1)
- Cloud STT, speaker diarization, translation, NPU/GPU acceleration.
- Background/always-on listening.

## 3. Architecture

```
UI (Compose) ──► DictationViewModel ──► SttEngine (interface)
                                          ├─ SherpaOnnxEngine
                                          └─ AndroidSttEngine
Settings (DataStore) ──► EngineProvider (picks implementation)
ModelManager ──► downloads/stores sherpa models
```

The UI only consumes `SttEvent`. The engine owns the microphone.

## 4. Core Interface

```kotlin
interface SttEngine {
    val id: EngineId                       // SHERPA, ANDROID
    val events: Flow<SttEvent>
    suspend fun isAvailable(lang: String): Availability
    suspend fun start(lang: String)        // engine opens mic
    suspend fun stop()                     // flush pending audio, emit Final
    fun release()
}

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent   // uncommitted, replaceable
    data class Final(val text: String) : SttEvent     // committed segment
    data class Error(val code: SttError, val msg: String?) : SttEvent
}

enum class Availability { READY, NEEDS_MODEL, UNSUPPORTED }
```

## 5. Engine: SherpaOnnxEngine
- **Library:** sherpa-onnx Kotlin API + prebuilt JNI libs.
- **Model:** multilingual Whisper (`base` default, `small` optional), int8 ONNX.
- **Audio:** `AudioRecord`, 16 kHz mono PCM16, foreground-only.
- **Pipeline:** `AudioRecord` → Silero VAD → speech segment → Whisper → `Final`.
- **Partials:** Whisper is not streaming. Emit `Partial("…")` while a segment is open, `Final(text)` after each VAD segment closes. Optional later: periodic re-decode of the open segment.
- **Language:** pass `de` or `en` explicitly (no auto-detect in v1).
- **Threads:** 4 (configurable). Run inference on a dedicated dispatcher.
- **Guards:** VAD gating and minimum segment length to avoid hallucinations on silence; drop empty or repeated-token output.
- **Punctuation:** Whisper output as-is.

## 6. Engine: AndroidSttEngine
- `SpeechRecognizer.createOnDeviceSpeechRecognizer()` only (never the cloud recognizer).
- Check with `checkRecognitionSupport()`; offer `triggerModelDownload()` (Android 13+) if the language pack is missing.
- Set `EXTRA_PREFER_OFFLINE`, `EXTRA_PARTIAL_RESULTS = true`.
- Map `onPartialResults` → `Partial`, `onResults` → `Final`.
- Auto-restart listening after each result while dictation is active (the recognizer stops on silence).
- Map `SpeechRecognizer.ERROR_*` → `SttError`.

## 7. Settings
| Setting | Options | Default |
|---|---|---|
| Engine | Sherpa-ONNX / Android built-in | Sherpa-ONNX |
| Language | Deutsch / English | System locale if supported, else Deutsch |
| Model size (Sherpa) | base / small | base |
| Auto-fallback | on / off | on |

## 8. Fallback Rules
- If the selected engine returns `NEEDS_MODEL` or `UNSUPPORTED`, and Auto-fallback is on, switch to the other engine and show a non-blocking notice.
- On a fatal engine error (model load failure, mic init failure), fall back once per session; never loop.
- Never fall back silently to a cloud service.
- Fallback does not change the saved setting.

## 9. Model Management (Sherpa)
- Models not bundled in the APK. Downloaded on first use over HTTPS to `filesDir/models/`.
- Verify SHA-256 after download; resumable; show progress and size before download.
- Allow deleting models in settings; show storage used.
- Model catalog as a small JSON/Kotlin list (id, url, size, sha256, languages).

## 10. UI Behavior
- Dictation screen: mic button (start/stop), live transcript area, note editor.
- `Final` text is appended to the note at the cursor; `Partial` is shown inline in a lighter style and replaced on the next event.
- Show engine and status chip (Loading model / Listening / Processing).
- Handle states: permission denied, model missing, engine error, interrupted (call, app backgrounded → stop and flush).

## 11. Permissions & Lifecycle
- `RECORD_AUDIO` runtime permission with rationale.
- Stop listening on `onStop`; release engine on ViewModel clear.
- Optional later: foreground service for long dictation.

## 12. Testing
- Unit: event mapping, fallback logic, engine switching (fake engines).
- Instrumented on Pixel 6: German and English test clips, 30 s and 5 min, noisy and silent input.
- Benchmark: real-time factor and memory for `base` vs `small`; target RTF < 0.5 for `base`.
- Interruption cases: incoming call, backgrounding, permission revoked, offline mode (airplane).

## 13. Milestones
1. `SttEngine` interface, event model, settings, dictation UI.
2. `AndroidSttEngine` (end-to-end flow working).
3. `SherpaOnnxEngine` with a bundled `base` model (spike and benchmark).
4. `ModelManager` and download flow.
5. Fallback logic, polish, tests.

## 14. Open Questions
- Is `base` accurate enough for German, or should `small` be the default?
- Re-decode the open segment for faster partials, or accept chunked output?
- Add a punctuation or post-processing model for cleaner notes?
