package app.nonara.notes

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.work.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.DataInputStream

object Stores {
    private val instances = mutableMapOf<String,NoteStore>()
    @Synchronized fun notes(c: Context): NoteStore = instances.getOrPut(c.filesDir.canonicalPath) { NoteStore(c.filesDir) }
    val inference = Mutex()
    val captureLock = Any()
}
class Captures(private val context: Context) {
    val root=File(context.filesDir,"pending").apply { mkdirs() }
    fun dir(id: String): File { require(java.util.UUID.fromString(id).toString()==id); return File(root,id) }
    fun read(id: String)=JSONObject(File(dir(id),"capture.json").readText())
    fun write(record: JSONObject) = synchronized(Stores.captureLock) { atomicWrite(File(dir(record.getString("id")),"capture.json"),record.toString().toByteArray()) }
    fun all(): List<JSONObject> = synchronized(Stores.captureLock) { root.listFiles().orEmpty().mapNotNull { runCatching { read(it.name) }.getOrNull() }.filter { it.optString("state") !in listOf("completed","discarded") } }
    fun create(note: Note?, caret: Int?, mode: String, language: String, size: String): JSONObject {
        val id=newId()
        return JSONObject().put("id",id).put("target",note?.id ?: newId()).put("new",note==null)
            .put("original",note?.body ?: "").put("caret",caret ?: -1).put("mode",mode)
            .put("language",language).put("model",size).put("state","recording").put("created",System.currentTimeMillis())
            .put("text","").also { write(it) }
    }
    fun state(id: String, state: String, error: String="") = synchronized(Stores.captureLock) { val r=read(id); if(r.getString("state") !in listOf("completed","discarded")) { r.put("state",state).put("error",error); write(r) } }
    fun discard(id: String) = synchronized(Stores.captureLock) {
        state(id,"discarded"); WorkManager.getInstance(context).cancelUniqueWork(id)
        dir(id).listFiles()?.filter { it.name!="capture.json" }?.forEach { it.deleteRecursively() }
    }
    fun enqueue(id: String) {
        state(id,"pending")
        WorkManager.getInstance(context).enqueueUniqueWork(id,ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TranscribeWorker>().setInputData(workDataOf("id" to id)).build())
    }
    fun commit(id: String, text: String) = synchronized(Stores.captureLock) {
        val r=read(id); if(r.getString("state") in listOf("discarded","completed")) return@synchronized
        require(text.isNotBlank()) { "No speech recognized. Retry or discard this recording." }
        r.put("text",text).put("state","committing"); write(r)
        Stores.notes(context).appendCapture(r.getString("target"),id,text,r.optString("original"),
            r.optInt("caret").takeIf { it>=0 },r.getBoolean("new"))
        state(id,"completed")
        dir(id).listFiles()?.filter { it.name!="capture.json" }?.forEach { it.deleteRecursively() }
    }
}
class PcmRecorder(private val context: Context, private val scope: CoroutineScope) {
    private var mic: AudioRecord?=null
    private var job: Job?=null
    @Volatile private var active=false
    @android.annotation.SuppressLint("MissingPermission")
    fun start(id: String, onLevel:(Float)->Unit = {}, onError:(String)->Unit) {
        check(mic==null)
        val size=maxOf(4096,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT))
        val recorder=AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size)
        require(recorder.state==AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        mic=recorder; active=true; recorder.startRecording()
        job=scope.launch(Dispatchers.IO) {
            try {
                val captures=Captures(context); val file=File(captures.dir(id),"${System.currentTimeMillis()}.pcm")
                FileOutputStream(file).use { out ->
                    val buffer=ByteArray(size); var checkpoint=0
                    while(active) {
                        val n=recorder.read(buffer,0,buffer.size)
                        if(!active) break
                        check(n>0) { "Microphone interrupted" }
                        require(captures.root.walkTopDown().filter { it.isFile }.sumOf { it.length() } < 256L*1024*1024) { "Pending recordings reached storage limit" }
                        require(file.usableSpace>16*1024*1024) { "Storage is full" }
                        withContext(Dispatchers.Main) { onLevel(microphoneLevel(buffer,n)) }
                        out.write(buffer,0,n); checkpoint+=n
                        if(checkpoint>=32000) { out.fd.sync(); checkpoint=0 }
                        require(file.length()<30L*60*32000) { "Recording reached 30 minutes" }
                    }; out.fd.sync()
                }
            } catch(e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "Recording failed") } }
        }
    }
    fun release() { active=false; runCatching { mic?.stop() }; mic?.release(); mic=null; job?.cancel() }
    suspend fun stop() {
        active=false
        val old=mic; mic=null
        runCatching { old?.stop() }; job?.join(); old?.release(); job=null
    }
}
class TranscribeWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = Stores.inference.withLock {
        val id=inputData.getString("id") ?: return@withLock Result.failure()
        val captures=Captures(applicationContext)
        try {
            val r=captures.read(id)
            if(r.getString("state") in listOf("completed","discarded")) return@withLock Result.success()
            if(r.getString("mode")=="android" || r.getString("state")=="committing") {
                captures.commit(id,r.getString("text")); return@withLock Result.success()
            }
            val model=Models(applicationContext); val size=r.getString("model")
            require(model.ready(size)) { "Install the $size model in Settings, then retry" }
            captures.state(id,"transcribing")
            val folder=model.directory(size)
            val recognizer=OfflineRecognizer(config=OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
                whisper=OfflineWhisperModelConfig(encoder=File(folder,"$size-encoder.int8.onnx").path,
                    decoder=File(folder,"$size-decoder.int8.onnx").path,language=r.getString("language").take(2),task="transcribe"),
                tokens=File(folder,"$size-tokens.txt").path,numThreads=4)))
            val vad=Vad(config=VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(
                model=File(folder,"silero_vad.onnx").path,minSilenceDuration=0.5f,maxSpeechDuration=10f)))
            val texts=mutableListOf<String>(); var segment=0
            try {
                suspend fun drain() {
                    while(!vad.empty()) {
                        currentCoroutineContext().ensureActive()
                        val cached=File(captures.dir(id),"segment-${segment++}.txt")
                        val samples=vad.front().samples
                        val text=if(cached.exists()) cached.readText() else {
                            val stream=recognizer.createStream()
                            try { stream.acceptWaveform(samples,16000); recognizer.decode(stream)
                                recognizer.getResult(stream).text.trim().also { atomicWrite(cached,it.toByteArray()) }
                            } finally { stream.release() }
                        }
                        texts+=text; vad.pop()
                    }
                }
                for(file in captures.dir(id).listFiles().orEmpty().filter { it.extension=="pcm" }.sortedBy { it.name }) {
                    DataInputStream(file.inputStream().buffered()).use { input ->
                        var remaining=file.length()/2
                        while(remaining>0) {
                            currentCoroutineContext().ensureActive()
                            val count=minOf(512L,remaining).toInt(); val samples=FloatArray(512)
                            for(i in 0 until count) { val lo=input.readUnsignedByte(); val hi=input.readByte().toInt(); samples[i]=((hi shl 8) or lo)/32768f }
                            remaining-=count; vad.acceptWaveform(samples); drain()
                        }
                    }; vad.flush(); drain(); vad.reset()
                }
            } finally { vad.release(); recognizer.release() }
            captures.commit(id,texts.filter { it.isNotBlank() }.joinToString(" ")); Result.success()
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { if(runCatching { captures.read(id).getString("state") }.getOrNull()!="discarded") captures.state(id,"failed",e.message ?: "Transcription failed"); Result.failure() }
    }
}
