package app.nonara.notes

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class NewModelChoicesTest {
    private val additions = setOf("canary-180m", "omnilingual-300m", "nemotron-3.5", "whisper-turbo", "whisper-large-v3")
    private fun entries(): List<Map<String, Any>> = Yaml().load(File("src/main/assets/models.json").readText())
    @Suppress("UNCHECKED_CAST")
    private fun info(entry: Map<String, Any>): SpeechModel = SpeechModel(
        entry["id"] as String, entry["name"] as String, entry["description"] as String,
        SpeechModelFamily.valueOf((entry["family"] as String).uppercase(java.util.Locale.ROOT)),
        entry["languages"] as List<String>, (entry["files"] as List<Map<String, Any>>).associate { it["role"] as String to it["name"] as String })

    @Test fun existingChoicesAndAllFiveRecommendationsAreAvailableWithoutChangingLanguage() {
        val entries = entries()
        assertTrue(entries.map { it["id"] }.toSet().containsAll(setOf("tiny", "base", "small", "medium", "parakeet-v3") + additions))
        for(entry in entries) {
            assertEquals("de-DE", info(entry).languageForSelection("de-DE"))
            if(info(entry).supports("en-US")) assertEquals("en-US", info(entry).languageForSelection("en-US"))
            else assertThrows(IllegalArgumentException::class.java) { info(entry).languageForSelection("en-US") }
        }
    }
    @Test fun canaryTranscribesGermanWithoutTranslatingItIntoEnglish() {
        val model = info(entries().first { it["id"] == "canary-180m" })
        val config = model.recognizerConfig(File("models"), "de-DE")
        assertEquals("de", config.modelConfig.canary.srcLang)
        assertEquals("de", config.modelConfig.canary.tgtLang)
        assertTrue(config.modelConfig.canary.usePnc)
        assertEquals("en", model.recognizerConfig(File("models"), "en-US").modelConfig.canary.tgtLang)
    }
    @Test fun largeV3AndTurboUse128MelBinsWhileEarlierWhisperModelsKeep80() {
        val files = mapOf("encoder" to "encoder.onnx", "decoder" to "decoder.onnx", "tokens" to "tokens.txt")
        for(id in listOf("base", "medium", "whisper-turbo", "whisper-large-v3")) {
            val model = SpeechModel(id, id, "", SpeechModelFamily.WHISPER, listOf("de-DE"), files)
            val config = model.recognizerConfig(File("models"), "de-DE")
            assertEquals(if(id.startsWith("whisper-")) 128 else 80, config.featConfig.featureDim)
            assertEquals("transcribe", config.modelConfig.whisper.task)
        }
    }
    @Test fun incompleteNewFamiliesAreRejectedBeforeNativeLoading() {
        for(id in additions) {
            val model = info(entries().first { it["id"] == id })
            val incomplete = model.copy(files = model.files - if(id == "omnilingual-300m") "model" else "encoder")
            assertThrows(IllegalStateException::class.java) {
                if(id == "nemotron-3.5") incomplete.onlineRecognizerConfig(File("models"), "de-DE")
                else incomplete.recognizerConfig(File("models"), "de-DE")
            }
            assertThrows(IllegalArgumentException::class.java) { model.copy(languages = listOf("en-US")).languageForSelection("de-DE") }
        }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun downloadsHavePinnedIntegrityMetadataAndEveryRequiredDecoderPart() {
        for(entry in entries().filter { it["id"] in additions }) {
            val files = entry["files"] as List<Map<String, Any>>
            val required = when(entry["id"]) {
                "omnilingual-300m" -> setOf("model", "tokens", "vad")
                "nemotron-3.5" -> setOf("encoder", "decoder", "joiner", "tokens", "vad")
                else -> setOf("encoder", "decoder", "tokens", "vad")
            }
            assertEquals(required, files.map { it["role"] }.toSet())
            assertEquals(files.size, files.map { it["name"] }.toSet().size)
            for(file in files) {
                assertTrue((file["size"] as Number).toLong() > 0)
                assertTrue((file["sha256"] as String).matches(Regex("[0-9a-f]{64}")))
                if(file["role"] != "vad") assertTrue((file["url"] as String).contains("/resolve/${entry["version"]}/"))
            }
        }
        assertEquals(5, entries().count { it["id"] in additions })
    }
}
