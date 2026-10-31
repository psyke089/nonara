package app.nonara.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.*
import kotlinx.coroutines.*

class AndroidLive(private val context: Context, private val scope: CoroutineScope) {
    private var recognizer: SpeechRecognizer?=null
    private var running=false
    private var retry: Job?=null
    private var ended=CompletableDeferred<Unit>()
    private var generation=0
    fun intent(language:String)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
    }
    fun available()=SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    fun download(language:String) {
        require(available()) { "No Android on-device recognition service is installed" }
        val r=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        r.triggerModelDownload(intent(language)); r.destroy()
    }
    fun start(language:String,onPartial:(String)->Unit,onFinal:suspend (String)->Unit,onError:(String)->Unit) {
        require(available()) { "Android on-device recognition is unavailable. Use Sherpa recording." }
        running=true; val token=++generation; var failures=0
        val r=SpeechRecognizer.createOnDeviceSpeechRecognizer(context); recognizer=r
        val request=intent(language)
        fun listen() { if(running && token==generation) { ended=CompletableDeferred(); r.startListening(request) } }
        r.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB:Float) {}
            override fun onBufferReceived(buffer:ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType:Int,params:Bundle?) {}
            override fun onPartialResults(results:Bundle?) { if(token==generation) onPartial(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()) }
            override fun onResults(results:Bundle?) {
                if(token!=generation) return
                val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                scope.launch {
                    try { if(text.isNotBlank()) onFinal(text); failures=0; onPartial("") }
                    catch(e:Exception) { running=false; onError(e.message ?: "Unable to save recognized text") }
                    finally { ended.complete(Unit) }
                    if(running) listen()
                }
            }
            override fun onError(error:Int) {
                if(token!=generation) return
                ended.complete(Unit)
                if(!running) return
                val delayMs=when(error) {
                    SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if(failures++<3) 500L shl (failures-1) else -1
                    else -> -1
                }
                if(delayMs>=0) retry=scope.launch { delay(delayMs); listen() }
                else { running=false; onError("Android recognition stopped (error $error). Recognized text is retained; unfinished speech has no saved audio.") }
            }
        }); listen()
    }
    suspend fun stop() {
        running=false; retry?.cancel(); retry=null
        val r=recognizer ?: return
        r.stopListening()
        withTimeoutOrNull(15000) { ended.await() }
        generation++; r.cancel(); r.destroy(); recognizer=null
    }
    fun release() { running=false; retry?.cancel(); generation++; recognizer?.destroy(); recognizer=null }
}
