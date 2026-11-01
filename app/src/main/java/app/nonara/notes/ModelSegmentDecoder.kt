package app.nonara.notes

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import java.io.Closeable
import java.io.File

/** Both decoder types consume the same saved PCM/VAD segments and use capture retry caching. */
internal interface ModelSegmentDecoder: Closeable {
    fun transcribe(samples: FloatArray, checkCancellation: () -> Unit): String
}

internal fun createModelSegmentDecoder(model: SpeechModel, folder: File, language: String): ModelSegmentDecoder {
    model.languageForSelection(language)
    return if(model.streaming) {
        val recognizer = OnlineRecognizer(config = model.onlineRecognizerConfig(folder, language))
        object: ModelSegmentDecoder {
            override fun transcribe(samples: FloatArray, checkCancellation: () -> Unit): String {
                val stream = recognizer.createStream()
                try {
                    // Set on every new stream; never fall back to English or automatic detection.
                    stream.setOption("language", language.take(2))
                } catch(e: Exception) { stream.release(); throw e }
                return decodeStreamingSegment(samples, object: StreamingSession {
                    override fun accept(samples: FloatArray) = stream.acceptWaveform(samples, 16000)
                    override fun finish() = stream.inputFinished()
                    override fun ready() = recognizer.isReady(stream)
                    override fun decode() = recognizer.decode(stream)
                    override fun result() = recognizer.getResult(stream).text.trim()
                    override fun close() = stream.release()
                }, checkCancellation)
            }
            override fun close() = recognizer.release()
        }
    } else {
        val recognizer = OfflineRecognizer(config = model.recognizerConfig(folder, language))
        object: ModelSegmentDecoder {
            override fun transcribe(samples: FloatArray, checkCancellation: () -> Unit): String {
                val stream = recognizer.createStream()
                try {
                    checkCancellation(); stream.acceptWaveform(samples, 16000); recognizer.decode(stream)
                    checkCancellation()
                    return recognizer.getResult(stream).text.trim()
                } finally { stream.release() }
            }
            override fun close() = recognizer.release()
        }
    }
}
