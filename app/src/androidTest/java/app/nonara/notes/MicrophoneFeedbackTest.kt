package app.nonara.notes

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MicrophoneFeedbackTest {
    @Test fun recorderPublishesInputLevelAndSavesAudioSamples() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val base=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(base.packageName,Manifest.permission.RECORD_AUDIO)
        val dir=File(base.cacheDir,"meter-test-${newId()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getFilesDir()=dir }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val recorder=PcmRecorder(context,scope)
        try {
            val capture=Captures(context).create(null,null,"sherpa","en-US","base")
            val feedback=CompletableDeferred<Float>()
            withContext(Dispatchers.Main) {
                recorder.start(capture.getString("id"),{ feedback.complete(it) },{ feedback.completeExceptionally(IllegalStateException(it)) })
            }
            val level=withTimeout(5000) { feedback.await() }
            withContext(Dispatchers.Main) { recorder.stop() }
            assertTrue(level in 0f..1f)
            val audio=Captures(context).dir(capture.getString("id")).listFiles()!!.filter { it.extension=="pcm" }
            assertEquals(1,audio.size)
            assertTrue("Microphone feedback must accompany recorded PCM samples",audio.single().length()>0)
        } finally {
            withContext(Dispatchers.Main) { recorder.stop() }
            scope.cancel(); dir.deleteRecursively()
        }
    }
}
