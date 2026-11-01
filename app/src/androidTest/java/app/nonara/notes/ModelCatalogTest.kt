package app.nonara.notes

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    private val models=Models(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun catalogueContainsOnlyGermanCapableWhisperAndParakeetModels() {
        assertEquals(setOf("tiny","base","small","medium","parakeet-v3"),models.ids().toSet())
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
            val config=info.recognizerConfig(models.directory(id),"de-DE").modelConfig
            assertTrue(config.tokens.endsWith(info.files.getValue("tokens")))
            assertTrue(info.files.containsKey("vad"))
            when(info.family) {
                SpeechModelFamily.PARAKEET -> assertTrue(config.transducer.joiner.endsWith("joiner.int8.onnx"))
                SpeechModelFamily.WHISPER -> assertEquals("de",config.whisper.language)
            }
        }
    }

}
