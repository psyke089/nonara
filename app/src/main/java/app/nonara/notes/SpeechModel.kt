package app.nonara.notes

import com.k2fsa.sherpa.onnx.*
import java.io.File

enum class SpeechModelFamily { WHISPER, PARAKEET, CANARY, OMNILINGUAL, NEMOTRON }

data class SpeechModel(
    val id: String,
    val name: String,
    val description: String,
    val family: SpeechModelFamily,
    val languages: List<String>,
    val files: Map<String, String>,
) {
    fun supports(language: String) = language in languages
    fun languageForSelection(current: String): String {
        require(supports(current)) { "$name does not support the selected language ($current)." }
        return current
    }
    val streaming: Boolean get() = family == SpeechModelFamily.NEMOTRON
    private val featureDim: Int get() = when {
        family in setOf(SpeechModelFamily.CANARY, SpeechModelFamily.NEMOTRON) -> 128
        id in setOf("whisper-large-v3", "whisper-turbo") -> 128
        else -> 80
    }
    private fun path(folder: File, role: String): String {
        val name = files[role] ?: error("$id is missing its $role file")
        require(File(name).name == name) { "Invalid model filename" }
        return File(folder, name).path
    }
    fun onlineRecognizerConfig(folder: File, language: String): OnlineRecognizerConfig {
        languageForSelection(language)
        check(streaming) { "$name needs an offline decoder" }
        return OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = featureDim),
            modelConfig = OnlineModelConfig(tokens = path(folder, "tokens"), numThreads = 4,
                transducer = OnlineTransducerModelConfig(encoder = path(folder, "encoder"),
                    decoder = path(folder, "decoder"), joiner = path(folder, "joiner"))),
            enableEndpoint = false,
        )
    }
    fun recognizerConfig(folder: File, language: String): OfflineRecognizerConfig {
        languageForSelection(language)
        check(!streaming) { "$name needs the streaming decoder" }
        val config = OfflineModelConfig(tokens = path(folder, "tokens"), numThreads = 4)
        when(family) {
            SpeechModelFamily.WHISPER -> config.whisper = OfflineWhisperModelConfig(
                encoder = path(folder, "encoder"), decoder = path(folder, "decoder"), language = language.take(2), task = "transcribe")
            SpeechModelFamily.PARAKEET -> {
                config.transducer = OfflineTransducerModelConfig(encoder = path(folder, "encoder"),
                    decoder = path(folder, "decoder"), joiner = path(folder, "joiner"))
                config.modelType = "nemo_transducer"
            }
            SpeechModelFamily.CANARY -> config.canary = OfflineCanaryModelConfig(
                encoder = path(folder, "encoder"), decoder = path(folder, "decoder"),
                srcLang = language.take(2), tgtLang = language.take(2), usePnc = true)
            SpeechModelFamily.OMNILINGUAL -> config.omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = path(folder, "model"))
            SpeechModelFamily.NEMOTRON -> error("$name needs the streaming decoder")
        }
        return OfflineRecognizerConfig(featConfig = FeatureConfig(sampleRate = 16000, featureDim = featureDim), modelConfig = config)
    }
}
