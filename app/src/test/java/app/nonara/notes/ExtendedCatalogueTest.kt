package app.nonara.notes

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class ExtendedCatalogueTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun germanSpecialistsAndStreamingModelsHaveCompletePinnedDownloads() {
        val entries:List<Map<String,Any>> = Yaml().load(File("src/main/assets/models.json").readText())
        for(id in listOf("fastconformer-de","fastconformer-de-ctc","kroko-de","nemotron-3.5-160ms")) {
            val entry = entries.singleOrNull { it["id"]==id }
            assertNotNull("Missing model $id",entry)
            entry!!
            val languages=entry["languages"] as List<String>
            assertTrue(languages.contains("de-DE"))
            val files=entry["files"] as List<Map<String,Any>>
            val roles=files.map { it["role"] }.toSet()
            assertTrue(roles.containsAll(if(id.endsWith("ctc")) setOf("model","tokens","vad") else setOf("encoder","decoder","joiner","tokens","vad")))
            for(file in files) {
                assertTrue((file["size"] as Number).toLong()>0)
                assertTrue((file["sha256"] as String).matches(Regex("[a-f0-9]{64}")))
                if(file["role"]!="vad") assertTrue((file["url"] as String).contains("/resolve/${entry["version"]}/"))
            }
            val model=SpeechModel(id,id,"",SpeechModelFamily.valueOf((entry["family"] as String).uppercase()),languages,
                files.associate { it["role"] as String to it["name"] as String })
            assertEquals(id in listOf("kroko-de","nemotron-3.5-160ms"),model.streaming)
            if(model.streaming) assertEquals(if(id=="kroko-de") 80 else 128,model.onlineRecognizerConfig(File("models"),"de-DE").featConfig.featureDim)
            else if(id.endsWith("ctc")) assertTrue(model.recognizerConfig(File("models"),"de-DE").modelConfig.nemo.model.endsWith("model.int8.onnx"))
            else assertEquals("nemo_transducer",model.recognizerConfig(File("models"),"de-DE").modelConfig.modelType)
            if(id!="nemotron-3.5-160ms") assertThrows(IllegalArgumentException::class.java) { model.languageForSelection("en-US") }
        }
    }
}
