package app.nonara.notes

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile

internal interface LiveSession : StreamingSession {
    fun endpoint(): Boolean
    fun reset()
}

/** Preview only: final note text always comes from the durable audio transcription worker. */
internal class StreamingPreview(private val session: LiveSession) {
    private val completed = mutableListOf<String>()
    private fun drain() { while(session.ready()) session.decode() }
    private fun text() = (completed + session.result().trim()).filter { it.isNotBlank() }.joinToString(" ")
    fun accept(samples: FloatArray): String {
        session.accept(samples); drain()
        if(session.endpoint()) {
            session.result().trim().takeIf { it.isNotBlank() }?.let(completed::add)
            session.reset()
        }
        return text()
    }

}

/** Follow saved PCM, never the microphone: bounded reads keep inference off the recorder path. */
internal class PcmTail(private val directory: File) {
    private val offsets = mutableMapOf<String,Long>()
    fun read(): FloatArray? {
        for(file in directory.listFiles().orEmpty().filter { it.extension=="pcm" }.sortedBy { it.name }) {
            val offset=offsets[file.name] ?: 0L
            val bytes=minOf(3200L,(file.length()-offset).coerceAtLeast(0L)/2*2).toInt()
            if(bytes==0) continue
            val buffer=ByteArray(bytes)
            RandomAccessFile(file,"r").use { it.seek(offset); it.readFully(buffer) }
            offsets[file.name]=offset+bytes
            return FloatArray(bytes/2) { i ->
                ((buffer[i*2].toInt() and 255) or (buffer[i*2+1].toInt() shl 8))/32768f
            }
        }
        return null
    }
}

internal suspend fun followPcm(directory:File, session:LiveSession, onPreview:suspend (String)->Unit) = session.use {
    val preview=StreamingPreview(session)
    val tail=PcmTail(directory)
    while(true) {
        currentCoroutineContext().ensureActive()
        val samples=tail.read()
        if(samples==null) { delay(100); continue }
        onPreview(preview.accept(samples))
    }
}
