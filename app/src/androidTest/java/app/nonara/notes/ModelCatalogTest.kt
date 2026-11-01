package app.nonara.notes

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    private val models=Models(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun catalogueContainsExistingChoicesAndFiveGermanCapableAdditions() {
        assertEquals(setOf("tiny","base","small","medium","parakeet-v3", "canary-180m", "omnilingual-300m",
            "nemotron-3.5", "whisper-turbo", "whisper-large-v3"),models.ids().toSet())
        assertTrue(models.ids().all { models.supports(it,"de-DE") })
    }

    @Test fun everyModelHasVerifiedVersionedFilesAndItsRequiredDecoderParts() {
        for(id in models.ids()) {
            val entry=models.definition(id)
            val info=models.info(id)
            val files=entry.getJSONArray("files")
            val names=mutableSetOf<String>()
            for(i in 0 until files.length()) {
                val file=files.getJSONObject(i)
                assertTrue(names.add(file.getString("name")))
                assertTrue(file.getLong("size")>0)
                assertTrue(file.getString("sha256").matches(Regex("[0-9a-f]{64}")))
                if(file.getString("role")!="vad") assertTrue(file.getString("url").contains("/resolve/${entry.getString("version")}/"))
            }
            val config=if(info.streaming) null else info.recognizerConfig(models.directory(id),"de-DE").modelConfig
            if(info.streaming) {
                val online=info.onlineRecognizerConfig(models.directory(id),"de-DE")
                assertEquals(128,online.featConfig.featureDim)
                assertTrue(online.modelConfig.transducer.encoder.endsWith(info.files.getValue("encoder")))
                assertTrue(online.modelConfig.tokens.endsWith(info.files.getValue("tokens")))
                assertTrue(info.files.containsKey("vad"))
                continue
            }
            requireNotNull(config)
            assertTrue(config.tokens.endsWith(info.files.getValue("tokens")))
            assertTrue(info.files.containsKey("vad"))
            when(info.family) {
                SpeechModelFamily.PARAKEET -> assertTrue(config.transducer.joiner.endsWith("joiner.int8.onnx"))
                SpeechModelFamily.WHISPER -> assertEquals("de",config.whisper.language)
                SpeechModelFamily.CANARY -> { assertEquals("de",config.canary.srcLang); assertEquals("de",config.canary.tgtLang) }
                SpeechModelFamily.OMNILINGUAL -> assertTrue(config.omnilingual.model.endsWith(info.files.getValue("model")))
                SpeechModelFamily.NEMOTRON -> fail("Nemotron must use the streaming decoder")
            }
        }
    }

}
