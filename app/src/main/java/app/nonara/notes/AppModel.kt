package app.nonara.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.*
import java.time.Instant
import java.util.zip.*
import java.io.ByteArrayOutputStream
import org.json.JSONObject

private val android.content.Context.preferences by preferencesDataStore("settings")
class AppModel @JvmOverloads constructor(application:Application, private val editorIo:CoroutineDispatcher=Dispatchers.IO):AndroidViewModel(application) {
    val context get()=getApplication<Application>()
    val store=Stores.notes(context)
    val captures=Captures(context)
    val models=Models(context)
    private val recorder=PcmRecorder(context,viewModelScope)
    var notes by mutableStateOf(emptyList<Note>()); private set
    var deleted by mutableStateOf(emptyList<Note>()); private set
    var pending by mutableStateOf(emptyList<JSONObject>()); private set
    var editor by mutableStateOf<Note?>(null); private set
    var page by mutableStateOf("home")
    var query by mutableStateOf("")
    var status by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    var theme by mutableStateOf("System"); private set
    var language by mutableStateOf(DictationDefaults.LANGUAGE); private set
    var modelSize by mutableStateOf(DictationDefaults.MODEL); private set
    var active by mutableStateOf<String?>(null); private set
    var paused by mutableStateOf(false); private set
    var busyCapture by mutableStateOf(false); private set
    var microphoneLevel by mutableStateOf(0f); private set
    var elapsedMillis by mutableStateOf(0L); private set
    var captureLabel by mutableStateOf(""); private set
    var streaming by mutableStateOf(false); private set
    var livePreview by mutableStateOf(""); private set
    var previewStatus by mutableStateOf(""); private set
    private var previewJob: Job? = null
    private var previewGeneration = 0
    private var captureStreaming = false
    private val recordingClock = RecordingClock()
    private var clockJob: Job? = null
    var downloadingModel by mutableStateOf<String?>(null); private set
    var downloading by mutableStateOf(false); private set
    var downloadStatus by mutableStateOf(""); private set
    var installed by mutableStateOf(setOf<String>()); private set
    private val downloads=ModelDownloads(context)
    var modelTransfers by mutableStateOf(emptyMap<String,TransferRecord>()); private set
    private var saveJob:Job?=null
    private val saveMutex=Mutex()
    private var revision=System.nanoTime()
    private var editorBase=""
    private var editorSession=Any()
    var editorConflict by mutableStateOf(false); private set
    private var conflictCopyId:String?=null
    private var undo by mutableStateOf<Note?>(null)
    val canUndo get()=undo!=null
    init {
        viewModelScope.launch { context.preferences.data.collect {
            theme=it[stringPreferencesKey("theme")] ?: "System"
            streaming=it[stringPreferencesKey("streaming")]=="true"
            language=DictationDefaults.language(it[stringPreferencesKey("language")])
            modelSize=DictationDefaults.model(it[stringPreferencesKey("model")])
        } }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.purge(); captures.all().forEach { r ->
                when(r.getString("state")) {
                    "recording","paused" -> captures.state(r.getString("id"),"interrupted")
                    "pending","transcribing","committing" -> captures.enqueue(r.getString("id"))
                }
            } }
            while(isActive) { refresh(); delay(1500) }
        }
    }
    fun preference(key:String,value:String) { viewModelScope.launch { context.preferences.edit { it[stringPreferencesKey(key)]=value } } }
    fun selectModel(id: String) = task {
        val info=models.info(id)
        require(!streaming || info.streaming) { "Choose a streaming model" }
        context.preferences.edit {
            it[stringPreferencesKey("model")]=id
            it[stringPreferencesKey("language")]=info.languageForSelection(language)
        }
    }
    suspend fun refresh() {
        val session=editorSession
        val rev=revision
        val wasSaved=status=="Saved"
        val data=withContext(Dispatchers.IO) { Triple(store.list(),store.list(true),captures.all()) }
        notes=data.first; deleted=data.second; pending=data.third
        if(wasSaved && status=="Saved" && editorSession===session && revision==rev)
            notes.firstOrNull { it.id==editor?.id && it.body!=editorBase }?.let { editor=it; editorBase=it.body }
        installed=withContext(Dispatchers.IO) { models.ids().filter { models.ready(it) }.toSet() }
        refreshDownloads()
    }
    fun open(note:Note?=null) {
        saveJob?.cancel(); editorSession=Any(); editorConflict=false; conflictCopyId=null
        editorBase=note?.body.orEmpty(); revision=System.nanoTime(); editor=note ?: Note.create(); page="editor"; status=if(note==null) "" else "Saved"
    }
    fun edit(body:String,title:String?=editor?.title) {
        val old=editor ?: return
        val new=old.copy(body=body,title=title?.takeIf { it.isNotBlank() },updatedAt=maxOf(Instant.now(),old.updatedAt))
        editor=new; revision=System.nanoTime(); saveJob?.cancel()
        if(editorConflict) return
        status="Saving…"
        val rev=revision
        val session=editorSession
        saveJob=viewModelScope.launch {
            try {
                saveMutex.withLock {
                    if(editorSession!==session || revision!=rev || editorConflict) return@launch
                    val base=editorBase
                    if(new.body.isNotBlank() || base.isNotEmpty()) withContext(editorIo) { store.journal(new,rev,base) }
                }
                delay(400); persist(new,rev,session)
            }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { saveFailed(e,session) }
        }
    }
    private fun saveFailed(e:Exception,session:Any) {
        if(editorSession!==session) return
        status="Couldn't save"
        if(e is NoteEditConflict) editorConflict=true
        error=e.message
    }
    private suspend fun persist(note:Note,rev:Long,session:Any) = withContext(NonCancellable) {
        // Once a write starts, its baseline acknowledgement must finish even if typing cancels
        // the debounce job. Otherwise our next edit mistakes this write for an external change.
        saveMutex.withLock {
            if(editorSession!==session || revision!=rev || editorConflict) return@withLock
            try {
                val base=editorBase
                val saved=withContext(editorIo) {
                    if(note.body.isNotBlank() || note.title!=null || store.get(note.id)!=null) {
                        store.saveEdited(note,rev,base); true
                    } else false
                }
                if(editorSession===session) {
                    if(saved) editorBase=note.body
                    if(revision==rev) status="Saved"
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { saveFailed(e,session) }
        }
    }
    fun leave() { viewModelScope.launch {
        if(editorConflict) { error=NoteEditConflict().message; return@launch }
        flush(); if(status!="Couldn't save") { editor=null; page="home"; refresh() }
    } }
    suspend fun flush() {
        val session=editorSession
        saveJob?.cancelAndJoin()
        if(editorSession===session && !editorConflict) editor?.let { persist(it,revision,session) }
    }
    fun saveConflictCopy()=task {
        saveJob?.cancelAndJoin()
        withContext(NonCancellable) {
            saveMutex.withLock {
                if(!editorConflict) return@withLock
                val draft=editor ?: return@withLock
                val rev=revision
                val session=editorSession
                val id=conflictCopyId ?: newId().also { conflictCopyId=it }
                val copy=draft.copy(id=id,extra=draft.extra - "last_applied_capture_id")
                withContext(editorIo) { store.save(copy,rev) }
                if(editorSession===session) {
                    editorBase=copy.body; editorConflict=false; conflictCopyId=null; error=null
                    if(revision==rev) { editor=null; page="home"; status="Saved" }
                    else { editor=editor?.copy(id=id); edit(editor!!.body) }
                }
            }
        }
        refresh()
    }
    fun lifecycleStop() { viewModelScope.launch { flush(); if(active!=null && !paused) pause(true) } }
    fun trash(note:Note) = task {
        require(pending.none { it.getString("target")==note.id }) { "Finish or discard pending dictation first" }
        saveJob?.cancelAndJoin()
        withContext(Dispatchers.IO) { store.trash(note.id) }; undo=note; editor=null; page="home"; refresh()
    }
    fun undoDelete()=task { undo?.let { withContext(Dispatchers.IO) { store.restore(it.id) }; undo=null; refresh() } }
    fun restore(note:Note)=task { withContext(Dispatchers.IO) { store.restore(note.id) }; refresh() }
    fun start(caret:Int?)=task {
        require(active==null) { "A capture is already open" }
        require(models.supports(modelSize,language)) { "This model does not support the selected language. Choose another model in Settings." }
        require(models.ready(modelSize)) { "Download a speech model in Settings before recording" }
        require(!streaming || models.info(modelSize).streaming) { "Choose a streaming model in Settings" }
        flush(); require(status!="Couldn't save") { "Save the note before recording" }
        val note=editor?.takeIf { it.body.isNotBlank() || it.title!=null }
        val r=withContext(Dispatchers.IO) { captures.create(note,caret,language,modelSize) }
        captureStreaming=streaming
        active=r.getString("id"); paused=false; recordingClock.start(android.os.SystemClock.elapsedRealtime())
        begin(r)
    }
    private fun begin(r:JSONObject) {
        val id=r.getString("id")
        microphoneLevel=0f
        captureLabel=models.info(r.getString("model")).name + " · " + if(r.getString("language")=="de-DE") "Deutsch" else "English"
        recordingClock.resume(android.os.SystemClock.elapsedRealtime())
        clockJob?.cancel()
        clockJob=viewModelScope.launch { while(isActive) { elapsedMillis=recordingClock.elapsed(android.os.SystemClock.elapsedRealtime()); delay(100) } }
        try {
        recorder.start(id, { microphoneLevel=it }) { message -> error=message; viewModelScope.launch { pause(true) } }
        startPreview(r)
        } catch(e:Exception) {
            recorder.release(); stopPreview()
            recordingClock.pause(android.os.SystemClock.elapsedRealtime())
            clockJob?.cancel(); microphoneLevel=0f; paused=true
            captures.state(id,"interrupted",e.message ?: "Microphone unavailable")
            throw e
        }
    }
    suspend fun pause(interrupted:Boolean=false) {
        if(busyCapture || active==null) return
        busyCapture=true
        try { recorder.stop(); stopPreview(); active?.let { withContext(Dispatchers.IO) { captures.state(it,if(interrupted) "interrupted" else "paused") } }; paused=true; recordingClock.pause(android.os.SystemClock.elapsedRealtime()); clockJob?.cancel(); elapsedMillis=recordingClock.elapsed(android.os.SystemClock.elapsedRealtime()); microphoneLevel=0f }
        finally { busyCapture=false }
    }
    fun togglePause()=task { if(paused) { val r=captures.read(active!!); captures.state(active!!,"recording"); begin(r); paused=false } else pause() }
    fun finish()=task {
        val id=active ?: return@task; busyCapture=true
        try { recorder.stop(); stopPreview(); withContext(Dispatchers.IO) { captures.enqueue(id) }; active=null; paused=false; clockJob?.cancel(); microphoneLevel=0f; refresh() }
        finally { busyCapture=false }
    }
    fun discard(id:String)=task {
        if(active==id) { recorder.stop(); stopPreview(); active=null; paused=false; clockJob?.cancel(); microphoneLevel=0f }
        withContext(Dispatchers.IO) { captures.discard(id) }; refresh()
    }
    fun resume(id:String)=task {
        require(active==null) { "Finish the current recording first" }
        val r=withContext(Dispatchers.IO) { captures.read(id) }
        require(r.getString("state") in listOf("paused","interrupted"))
        captureStreaming=streaming && models.info(r.getString("model")).streaming
        active=id; paused=false; recordingClock.start(android.os.SystemClock.elapsedRealtime()); begin(r); captures.state(id,"recording")
    }
    private fun stopPreview() {
        previewGeneration++; previewJob?.cancel(); previewJob=null
        previewStatus=""
    }
    private fun startPreview(record:JSONObject) {
        stopPreview(); livePreview=""
        if(!captureStreaming) return
        val generation=previewGeneration
        val id=record.getString("id")
        previewStatus="Starting preview…"
        previewJob=viewModelScope.launch(Dispatchers.Default) {
            val owner=Any()
            if(!Stores.inference.tryLock(owner)) {
                withContext(Dispatchers.Main) { if(generation==previewGeneration) previewStatus="Preview unavailable · transcription in progress" }
                return@launch
            }
            try {
                val info=models.info(record.getString("model"))
                followPcm(captures.dir(id),createLiveSession(info,models.directory(info.id),record.getString("language"))) { text ->
                    withContext(Dispatchers.Main) {
                        if(generation==previewGeneration) { livePreview=text; previewStatus="Live preview" }
                    }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                withContext(Dispatchers.Main) { if(generation==previewGeneration) previewStatus="Preview unavailable · audio recording continues" }
            } finally { Stores.inference.unlock(owner) }
        }
    }
    fun retry(id:String)=task { withContext(Dispatchers.IO) { captures.enqueue(id) }; refresh() }
    private suspend fun refreshDownloads() {
        val (records,live)=withContext(Dispatchers.IO) { downloads.snapshot() }
        modelTransfers=records.associateBy { it.model }
        val current=records.firstOrNull { it.workId in live }
        downloading=current!=null
        downloadingModel=current?.model
        downloadStatus=current?.let { TransferProgress(it.phase,it.bytes,it.total).label()+if(it.message.isBlank()) "" else "\n"+it.message }.orEmpty()
    }
    fun download(size:String=modelSize)=task {
        withContext(Dispatchers.IO) { downloads.start(size) }
        refreshDownloads()
    }
    fun cancelDownload()=task { withContext(Dispatchers.IO) { downloads.cancel() }; refreshDownloads() }
    fun discardDownload(size:String)=task {
        withContext(Dispatchers.IO) { downloads.discard(size) }; refreshDownloads()
    }
    fun deleteModel(size:String=modelSize)=task {
        require(active==null && pending.none { it.optString("model")==size }) { "A capture still needs this model" }
        withContext(Dispatchers.IO) { downloads.delete(size) }; refresh()
    }
    fun export(uri:Uri)=task {
        flush()
        withContext(Dispatchers.IO) {
            val snapshot=store.list().map { it.id to NoteCodec.encode(it).toByteArray() }
            context.contentResolver.openOutputStream(uri,"wt")!!.use { raw -> ZipOutputStream(raw).use { zip ->
                snapshot.forEach { (id,bytes) -> zip.putNextEntry(ZipEntry("notes/$id.md")); zip.write(bytes); zip.closeEntry() }
            } }
        }; status="Export complete"
    }
    fun import(uri:Uri)=task {
        val summary=withContext(Dispatchers.IO) {
            var imported=0; var skipped=0; var total=0L; val names=mutableSetOf<String>()
            context.contentResolver.openInputStream(uri)!!.use { raw -> ZipInputStream(raw).use { zip ->
                var count=0
                while(true) { val entry=zip.nextEntry ?: break; require(++count<=10000) { "Too many ZIP entries" }
                    require(entry.name.matches(Regex("notes/[0-9a-f-]{36}\\.md"))) { "Invalid archive path: ${entry.name}" }
                    require(names.add(entry.name)) { "Duplicate archive path" }
                    val out=ByteArrayOutputStream(); val buffer=ByteArray(8192)
                    while(true) { val n=zip.read(buffer); if(n<0) break; total+=n; require(out.size()+n<=4*1024*1024 && total<=256L*1024*1024) { "Import exceeds size limit" }; out.write(buffer,0,n) }
                    runCatching { store.importNote(out.toByteArray(),entry.name.substringAfter('/')) }.onSuccess { imported++ }.onFailure { skipped++ }
                }
            } }; "$imported imported, $skipped conflicting or unsupported notes skipped"
        }; status=summary; refresh()
    }
    fun task(block:suspend ()->Unit) { viewModelScope.launch { try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { error=e.message ?: "Operation failed" } } }
    override fun onCleared() { clockJob?.cancel(); stopPreview(); recorder.release(); super.onCleared() }
}
