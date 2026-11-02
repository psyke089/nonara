package app.nonara.notes

import com.k2fsa.sherpa.onnx.OnlineRecognizer
import java.io.File

internal fun createLiveSession(model:SpeechModel, folder:File, language:String):LiveSession {
    val recognizer=OnlineRecognizer(config=model.onlineRecognizerConfig(folder,language).copy(enableEndpoint=true))
    val stream=try { recognizer.createStream() } catch(e:Exception) { recognizer.release(); throw e }
    try {
        if(model.family==SpeechModelFamily.NEMOTRON) stream.setOption("language",language.take(2))
    } catch(e:Exception) { stream.release(); recognizer.release(); throw e }
    return object:LiveSession {
        override fun accept(samples:FloatArray)=stream.acceptWaveform(samples,16000)
        override fun ready()=recognizer.isReady(stream)
        override fun decode()=recognizer.decode(stream)
        override fun result()=recognizer.getResult(stream).text
        override fun endpoint()=recognizer.isEndpoint(stream)
        override fun reset() { recognizer.reset(stream) }
        override fun finish()=stream.inputFinished()
        override fun close() { stream.release(); recognizer.release() }
    }
}
