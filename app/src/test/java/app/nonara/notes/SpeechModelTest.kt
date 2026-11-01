package app.nonara.notes

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SpeechModelTest {
    private fun model(family:SpeechModelFamily,languages:List<String> = listOf("de-DE","en-US")) = SpeechModel(
        "test","Test model","",family,languages,mapOf("encoder" to "encoder.onnx","decoder" to "decoder.onnx",
            "joiner" to "joiner.onnx","tokens" to "tokens.txt","preprocessor" to "preprocess.onnx",
            "uncached_decoder" to "uncached.onnx","cached_decoder" to "cached.onnx"))

    @Test(expected=IllegalArgumentException::class)
    fun incompatibleSelectionMustNotSilentlySwitchGermanToEnglish() {
        model(SpeechModelFamily.WHISPER,listOf("en-US")).languageForSelection("de-DE")
    }

    @Test fun germanSelectionStaysGerman() {
        assertEquals("de-DE",model(SpeechModelFamily.PARAKEET).languageForSelection("de-DE"))
    }

    @Test(expected=IllegalArgumentException::class)
    fun englishOnlyModelRejectsGermanBeforeLoadingNativeRuntime() {
        model(SpeechModelFamily.WHISPER,listOf("en-US")).recognizerConfig(File("models"),"de-DE")
    }

    @Test(expected=IllegalStateException::class)
    fun incompleteParakeetDoesNotFallBackToWhisper() {
        val model=model(SpeechModelFamily.PARAKEET)
        model.copy(files=model.files-"joiner").recognizerConfig(File("models"),"de-DE")
    }
}
