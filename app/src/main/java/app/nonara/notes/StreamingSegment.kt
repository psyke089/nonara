package app.nonara.notes

import java.io.Closeable

internal interface StreamingSession: Closeable {
    fun accept(samples: FloatArray)
    fun finish()
    fun ready(): Boolean
    fun decode()
    fun result(): String
}

/** Drain while feeding bounded PCM chunks; finalization makes the last partial frame readable. */
internal fun decodeStreamingSegment(samples: FloatArray, session: StreamingSession, tailSilenceSamples: Int = 0, checkCancellation: () -> Unit = {}): String = session.use {
    fun drain() {
        while(session.ready()) { checkCancellation(); session.decode() }
    }
    var offset = 0
    while(offset < samples.size) {
        checkCancellation()
        val end = minOf(offset + 1600, samples.size)
        session.accept(samples.copyOfRange(offset, end))
        drain(); offset = end
    }
    var tail = tailSilenceSamples
    while(tail > 0) {
        checkCancellation()
        val count = minOf(1600,tail)
        session.accept(FloatArray(count)); drain(); tail -= count
    }
    checkCancellation()
    session.finish()
    drain()
    checkCancellation()
    session.result()
}
