# German-capable sherpa-onnx speech models

Checked **2026-11-01**. This is the exhaustive German-capable **ASR** list for the official
sherpa-onnx catalogue at this snapshot, including existing Nonara choices and additional models.
It is not a list of every language or of TTS, VAD, punctuation, speaker-identification or audio-tagging models.
Versions and precision variants are included rather than filtered by my recommendations.
Duplicate hosting mirrors are not separate models. `large` is an upstream Whisper alias.

Sources were cross-checked against the [official ASR release assets](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models),
[documentation](https://k2-fsa.github.io/sherpa/onnx/), and the maintainer's exported ONNX repositories
([csukuangfj](https://huggingface.co/csukuangfj), [csukuangfj2](https://huggingface.co/csukuangfj2)).
Some exports are present in release assets or maintainer repositories before the documentation index is updated.
The list covers published ONNX models, not every upstream checkpoint that could theoretically be converted.

## Complete list

### Whisper

The following are the German-capable multilingual exports. Each repository publishes **float32
and int8** encoder/decoder variants; float32 exports can require separate external weight files.
[Official export catalogue](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/export-onnx.html).

- [Whisper Tiny](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/main) — float32, int8. Existing Nonara choice: int8.
- [Whisper Base](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/tree/main) — float32, int8. Existing Nonara choice: int8.
- [Whisper Small](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/tree/main) — float32, int8. Existing Nonara choice: int8.
- [Whisper Medium](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-medium/tree/main) — float32, int8. Existing Nonara choice: int8.
- [Whisper Large (`large` alias)](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large/tree/main) — float32, int8. Listed separately by upstream; prefer an explicit version for reproducibility.
- [Whisper Large v1](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v1/tree/main) — float32, int8.
- [Whisper Large v2](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v2/tree/main) — float32, int8.
- [Whisper Large v3](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/tree/main) — float32, int8. **Recommendation 5; added to Nonara as int8.** Uses 128 mel bins.
- [Whisper Turbo / Large v3 Turbo](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/tree/main) — float32, int8. **Recommendation 4; added to Nonara as int8.** Uses 128 mel bins.

### NVIDIA NeMo: Parakeet and Canary

- [Parakeet TDT 0.6B v3](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo/parakeet-tdt-0.6b-v3.html) — German among 25 European languages.
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/tree/main) — existing Nonara choice.
  - [float16](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-fp16/tree/main).
  - [float32](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3/tree/main).
- [Canary 180M Flash, English/German/Spanish/French](https://k2-fsa.github.io/sherpa/onnx/nemo/canary.html).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/tree/main) — **recommendation 1; added to Nonara**.
  - [float32](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr/tree/main).

### NVIDIA NeMo: FastConformer, offline CTC

All three models below have **float32 and int8** exports. These are additional choices, not the
five initially recommended. They are included here without filtering for expected quality or speed.

- [German FastConformer Hybrid Large, CTC decoder (`stt_de_fastconformer_hybrid_large_pc`)](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-stt_de_fastconformer_hybrid_large_pc/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-stt_de_fastconformer_hybrid_large_pc-int8/tree/main).
  - [German upstream model card](https://huggingface.co/nvidia/stt_de_fastconformer_hybrid_large_pc).
- [FastConformer CTC, English/German/Spanish/French, 14,288 hours](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8/tree/main).
- [FastConformer CTC, ten languages including German, 20k hours](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-ctc-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8/tree/main).

### NVIDIA NeMo: FastConformer, offline transducer

These use the **transducer/RNN-T** decoder, so they are distinct from the CTC exports above.
Each has **float32 and int8** variants.

- [German FastConformer Hybrid Large, transducer decoder (`stt_de_fastconformer_hybrid_large_pc`)](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc-int8/tree/main).
- [FastConformer transducer, English/German/Spanish/French, 14,288 hours](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-transducer-en-de-es-fr-14288/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-transducer-en-de-es-fr-14288-int8/tree/main).
- [FastConformer transducer, ten languages including German, 20k hours](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k/tree/main).
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-transducer-be-de-en-es-fr-hr-it-pl-ru-uk-20k-int8/tree/main).

### Streaming Zipformer

- [Kroko German streaming Zipformer, 2025-08-06](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06/tree/main).
  - Official release: `sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06.tar.bz2`.
  - German-specific model, distinct from English, French and Spanish Kroko exports.
  - The catalogue publishes one bundle under this name; it is not advertised as a separately selectable float32/int8 pair.

### Meta Omnilingual ASR, offline CTC

All four model/version combinations below have **float32 and int8** exports. German is supported;
these CTC models recognize the input language automatically rather than accepting a forced German
language option. [Official model documentation](https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html).

- **Omnilingual ASR CTC 300M, original 2025-11-12 export**.
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/tree/main) — **recommendation 2; added to Nonara**.
  - [float32](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-2025-11-12/tree/main).
- **Omnilingual ASR CTC 1B, original 2025-11-12 export**.
  - [int8](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-int8-2025-11-12/tree/main).
  - [float32](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-2025-11-12/tree/main).
- **Omnilingual ASR CTC 300M v2, 2026-02-05 export**.
  - [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-v2-2026-02-05/tree/main).
  - [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-v2-2026-02-05/tree/main).
  - The GitHub archive instead puts `v2` before `int8`: `300M-ctc-v2-int8-2026-02-05`.
- **Omnilingual ASR CTC 1B v2, 2026-02-05 export**.
  - [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-v2-int8-2026-02-05/tree/main).
  - [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-v2-2026-02-05/tree/main).

### NVIDIA Nemotron 3.5, streaming RNN-T

[Nemotron 3.5 ASR Streaming 0.6B](https://k2-fsa.github.io/sherpa/onnx/nemo/nemotron-streaming.html)
supports German (`de-DE`) out of the box, unlike the older English-only Nemotron checkpoints.
All five published chunk sizes have **float32 and int8** maintainer exports. The release assets
include 320 ms even though the documentation's earlier summary lists only four chunk sizes.

- **80 ms**, 2026-06-11: [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-80ms-int8-2026-06-11/tree/main), [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-80ms-2026-06-11/tree/main).
- **160 ms**, 2026-06-11: [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-160ms-int8-2026-06-11/tree/main), [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-160ms-2026-06-11/tree/main).
- **320 ms**, 2026-06-11: [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11/tree/main), [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-2026-06-11/tree/main).
- **560 ms**, 2026-06-11: [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11/tree/main), [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-2026-06-11/tree/main). **Recommendation 3; added to Nonara as int8.**
- **1120 ms**, 2026-06-11: [int8](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-int8-2026-06-11/tree/main), [float32](https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-2026-06-11/tree/main).

### Other multilingual offline models

- [Cohere Transcribe, 14 languages, int8, 2026-04-01](https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/pretrained.html).
  - [Maintainer export](https://huggingface.co/csukuangfj2/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01/tree/main).
  - German is explicitly supported; input language must be selected. External encoder weight data is required.
- [Qwen3-ASR 0.6B, int8, 2026-03-25](https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html).
  - [Maintainer export](https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/tree/main).
  - German is explicitly supported among its 30 languages. Includes tokenizer and multiple model components.
  - Other upstream Qwen model sizes are not counted without a corresponding official sherpa-onnx export.

## Catalogue entries that do not meet Nonara's German requirement

These exclusions are important because names such as “multilingual” or the absence of `.en` are
not sufficient evidence of German support.

- **Distil-Whisper** `distil-large-v2`, `distil-large-v3`, `distil-large-v3.5`, `distil-small.en`,
  `distil-medium.en`: the upstream checkpoints are English ASR models, including those without
  `.en`. The sherpa export page's general “all other models are multilingual” sentence is too
  broad for these checkpoints. See the [v2](https://huggingface.co/distil-whisper/distil-large-v2),
  [v3](https://huggingface.co/distil-whisper/distil-large-v3) and [v3.5](https://huggingface.co/distil-whisper/distil-large-v3.5) model cards.
- **Whisper `.en` variants**: English-only. **Whisper Medium AISHELL** is a Chinese-specific
  fine-tune and is not counted as a supported German option.
- **Moonshine v1/v2**: the published sherpa models cover English and several other languages,
  but no German model is listed in the [official v2 catalogue](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html).
- **SenseVoice, Dolphin, FireRedASR, Paraformer and the other listed language-specific
  Zipformer/CTC/transducer models**: their advertised language sets do not include German;
  Kroko German and the multilingual NeMo models above are the relevant exceptions.
- **FunASR Nano**: the exported `Fun-ASR-Nano-2512` checkpoint explicitly advertises Chinese,
  English and Japanese. The documentation also contains an inconsistent “31 languages” demo
  label. Neither that label nor the separate upstream MLT checkpoint establishes German support
  for this export; see the [upstream checkpoint descriptions](https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512)
  and [sherpa model page](https://k2-fsa.github.io/sherpa/onnx/funasr-nano/pretrained.html).
- **Parakeet v2, Parakeet Unified English and Nemotron speech-streaming English**: English-only.
- **German Citrinet from the separate `sherpa` PyTorch catalogue** is not a published model in
  the sherpa-onnx catalogue and is outside this list's runtime scope.

## Five recommendations added to Nonara

The same five recommendations from the earlier discussion are now separate explicit downloads.
The exhaustive list above also includes other potentially useful models; these five are not a
claim that the alternatives have been benchmarked or found inferior.

1. **Canary 180M Flash int8** — `canary-180m`; 207,813,900 bytes including tokens and Silero VAD.
2. **Omnilingual ASR 300M int8 (original export)** — `omnilingual-300m`; 366,082,397 bytes including tokens and VAD. v2 remains in the reference list rather than replacing the originally recommended export.
3. **Nemotron 3.5 0.6B, 560 ms, int8** — `nemotron-3.5`; 682,859,210 bytes including tokens and VAD.
4. **Whisper Turbo int8** — `whisper-turbo`; 1,037,257,645 bytes including tokens and VAD.
5. **Whisper Large v3 int8** — `whisper-large-v3`; 1,776,397,772 bytes including tokens and VAD.

Every file has a pinned revision, expected size and SHA-256 in `app/src/main/assets/models.json`.
The existing five model IDs, pinned files and installation paths are preserved. German remains
the default; all ten choices support German and English, and selection preserves the stored language.
Canary uses matching source/target languages to transcribe rather than translate. Omnilingual's
CTC backend has no forced-language option. Nemotron uses its streaming decoder on saved VAD
segments; this does not add a new live-recording UI or upload audio.

No model weights or test audio were downloaded for development. Small token vocabulary text
was read to derive its SHA-256; weight sizes/hashes come from pinned Hugging Face LFS metadata.
Native transcription quality, latency, memory use and phone behavior remain unverified. Local
regressions cover language preservation, required components, Whisper feature dimensions,
Canary transcription language, streamed sample/tail handling, cancellation and durable transfers.
