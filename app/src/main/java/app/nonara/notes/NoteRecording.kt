package app.nonara.notes

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class NoteRecording(val file: File, val durationMillis: Long = (file.length() - 44).coerceAtLeast(0) * 1000 / PCM_BYTES_PER_SECOND) {
    val id: String get() = file.nameWithoutExtension
    val recordedAtMillis: Long get() = UUID.fromString(id).mostSignificantBits ushr 16
}

private const val PCM_BYTES_PER_SECOND = 32_000

/** Write the recorder's 16 kHz mono PCM16 chunks as a single playable WAV. */
internal fun writeRecordingWav(file: File, chunks: List<File>) {
    val sizes = chunks.map { it.length() and -2L }
    val audioSize = sizes.sum()
    require(audioSize in 2..(Int.MAX_VALUE - 36L)) { "Recording has no playable audio or exceeds the WAV size limit" }
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(audioSize.toInt() + 36); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(16_000); putInt(PCM_BYTES_PER_SECOND); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(audioSize.toInt())
    }
    FileOutputStream(file).use { out ->
        out.write(header.array())
        val buffer = ByteArray(8192)
        for ((chunk, size) in chunks.zip(sizes)) chunk.inputStream().use { input ->
            var remaining = size
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(count > 0) { "Recording ended unexpectedly" }
                out.write(buffer, 0, count)
                remaining -= count
            }
        }
        out.fd.sync()
    }
}
