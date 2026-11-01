package app.nonara.notes

import com.k2fsa.sherpa.onnx.*
import java.io.File

enum class SpeechModelFamily { WHISPER, PARAKEET }

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

    fun recognizerConfig(folder: File, language: String): OfflineRecognizerConfig {
        require(supports(language)) { "$name does not support $language. Choose a compatible model." }
        fun path(role: String): String {
            val name=files[role] ?: error("$id is missing its $role file")
            require(File(name).name==name) { "Invalid model filename" }
            return File(folder,name).path
        }
        val config=OfflineModelConfig(tokens=path("tokens"),numThreads=4)
        when(family) {
            SpeechModelFamily.WHISPER -> config.whisper=OfflineWhisperModelConfig(
                encoder=path("encoder"),decoder=path("decoder"),language=language.take(2),task="transcribe")
            SpeechModelFamily.PARAKEET -> {
                config.transducer=OfflineTransducerModelConfig(encoder=path("encoder"),decoder=path("decoder"),joiner=path("joiner"))
                config.modelType="nemo_transducer"
            }

        }
        return OfflineRecognizerConfig(modelConfig=config)
    }
}
