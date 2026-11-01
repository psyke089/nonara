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
internal fun decodeStreamingSegment(samples: FloatArray, session: StreamingSession, checkCancellation: () -> Unit = {}): String = session.use {
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
    checkCancellation()
    session.finish()
    drain()
    checkCancellation()
    session.result()
}
