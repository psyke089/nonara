package app.nonara.notes

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StreamingSegmentTest {
    /** A deterministic framed decoder: each positive sample is a character, zero is silence.
     * A final short frame is readable only after end-of-input, like a streaming ASR tail. */
    private class FramedSession: StreamingSession {
        val pending = ArrayDeque<Float>()
        val text = StringBuilder()
        var finished = false
        var closed = false
        var largestQueue = 0
        var totalAccepted = 0
        override fun accept(samples: FloatArray) { totalAccepted += samples.size; samples.forEach { pending.add(it) }; largestQueue = maxOf(largestQueue, pending.size) }
        override fun finish() { finished = true }
        override fun ready() = pending.size >= 1000 || finished && pending.isNotEmpty()
        override fun decode() { repeat(minOf(1000, pending.size)) { val sample = pending.removeFirst(); if(sample > 0) text.append(sample.toInt().toChar()) } }
        override fun result() = text.toString()
        override fun close() { closed = true }
    }
    @Test fun segmentTailIsDecodedWithoutLosingOrDuplicatingSamplesAndStreamIsReleased() {
        val expected = "Ein deutscher Gedanke. ".repeat(200)
        val session = FramedSession()
        val result = decodeStreamingSegment(expected.map { it.code.toFloat() }.toFloatArray(), session)
        assertEquals(expected, result)
        assertTrue(session.finished)
        assertTrue(session.closed)
        assertTrue(session.pending.isEmpty())
        assertTrue("Feed bounded chunks instead of accumulating the whole recording", session.largestQueue <= 20000)
    }
    @Test fun zipformerTailSilenceFlushesTheFinalFrameWithoutChangingSpeechText() {
        val session = FramedSession()
        val text = "Ein letzter Gedanke"
        val result = decodeStreamingSegment(text.map { it.code.toFloat() }.toFloatArray(), session, tailSilenceSamples=12800)
        assertEquals(text,result)
        assertEquals(text.length+12800,session.totalAccepted)
        assertTrue(session.closed)
        assertTrue(session.pending.isEmpty())
    }
    @Test fun cancellationReleasesTheStreamWithoutReturningAPartialTranscript() {
        val session = FramedSession()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            decodeStreamingSegment(FloatArray(32000) { 97f }, session) { if(++checks == 3) throw CancellationException() }
        }
        assertTrue(session.closed)
    }
}
