package app.nonara.notes

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LiveStreamingTest {
    @Test fun streamingPreviewReplacesPartialsAndKeepsEndpointTextOnlyOnce() {
        val session = FakeLiveSession()
        val preview = StreamingPreview(session)
        session.text = "Guten"
        assertEquals("Guten", preview.accept(floatArrayOf(0.2f)))
        session.text = "Guten Morgen"; session.endpoint = true
        assertEquals("Guten Morgen", preview.accept(floatArrayOf(0.3f)))
        session.text = "Noch"
        assertEquals("Guten Morgen Noch", preview.accept(floatArrayOf(0.4f)))
        session.text = "Noch ein Gedanke"
        assertEquals("Guten Morgen Noch ein Gedanke", preview.accept(floatArrayOf(0.5f)))
        assertEquals(1, session.resets)
        assertTrue(session.samples.take(3).toFloatArray().contentEquals(floatArrayOf(0.2f,0.3f,0.4f)))
    }

    @Test fun pcmTailReadsGrowingFilesInOrderWithoutRepeatingOrDroppingHalfSamples() {
        val dir = Files.createTempDirectory("pcm-tail").toFile()
        try {
            val first = File(dir,"1.pcm"); first.writeBytes(byteArrayOf(0,64,0))
            val tail = PcmTail(dir)
            assertArrayEquals(floatArrayOf(0.5f), tail.read()!!, 0f)
            assertNull(tail.read())
            first.appendBytes(byteArrayOf(-64))
            assertArrayEquals(floatArrayOf(-0.5f), tail.read()!!, 0f)
            File(dir,"2.pcm").writeBytes(byteArrayOf(-1,127))
            assertArrayEquals(floatArrayOf(32767f/32768), tail.read()!!, 0f)
            assertNull(tail.read())
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellingLivePreviewReleasesSessionAndPreservesRecordedAudio() = runBlocking {
        val dir = Files.createTempDirectory("live-preview").toFile()
        val pcm=File(dir,"1.pcm").apply { writeBytes(byteArrayOf(0,64,0,-64)) }
        val session=FakeLiveSession().apply { text="Ein Gedanke" }
        val preview=CompletableDeferred<String>()
        val job=launch { followPcm(dir,session) { preview.complete(it) } }
        try {
            assertEquals("Ein Gedanke",withTimeout(1000) { preview.await() })
            job.cancelAndJoin()
            assertTrue(session.closed)
            assertArrayEquals(byteArrayOf(0,64,0,-64),pcm.readBytes())
            assertArrayEquals(floatArrayOf(0.5f,-0.5f),session.samples.toFloatArray(),0f)
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    private class FakeLiveSession : LiveSession {
        var closed=false
        var text=""; var endpoint=false; var resets=0; var finished=false
        val samples=mutableListOf<Float>()
        private var pending=false
        override fun accept(samples:FloatArray) { this.samples.addAll(samples.toList()); pending=true }
        override fun ready()=pending
        override fun decode() { pending=false }
        override fun result()=text
        override fun endpoint()=endpoint
        override fun reset() { resets++; endpoint=false; text="" }
        override fun finish() { finished=true }
        override fun close() { closed=true }
    }
}
