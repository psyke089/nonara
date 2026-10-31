package app.nonara.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.runtime.*
import java.time.Instant
import java.util.zip.*
import java.io.ByteArrayOutputStream
import org.json.JSONObject

private val android.content.Context.preferences by preferencesDataStore("settings")
class AppModel(application:Application):AndroidViewModel(application) {
    val context get()=getApplication<Application>()
    val store=Stores.notes(context)
    val captures=Captures(context)
    val models=Models(context)
    private val recorder=PcmRecorder(context,viewModelScope)
    private val live=AndroidLive(context,viewModelScope)
    var notes by mutableStateOf(emptyList<Note>()); private set
    var deleted by mutableStateOf(emptyList<Note>()); private set
    var pending by mutableStateOf(emptyList<JSONObject>()); private set
    var editor by mutableStateOf<Note?>(null); private set
    var page by mutableStateOf("home")
    var query by mutableStateOf("")
    var status by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    var theme by mutableStateOf("System"); private set
    var mode by mutableStateOf("sherpa"); private set
    var language by mutableStateOf("de-DE"); private set
    var modelSize by mutableStateOf("base"); private set
    var active by mutableStateOf<String?>(null); private set
    var paused by mutableStateOf(false); private set
    var busyCapture by mutableStateOf(false); private set
    var preview by mutableStateOf(""); private set
    var downloading by mutableStateOf(false); private set
    var downloadStatus by mutableStateOf(""); private set
    var installed by mutableStateOf(setOf<String>()); private set
    private var downloadJob: Job?=null
    private var saveJob:Job?=null
        private var revision=System.nanoTime()
    private var editorBase=""
    private var undo:Note?=null
    init {
        viewModelScope.launch { context.preferences.data.collect {
            theme=it[stringPreferencesKey("theme")] ?: "System"
            mode=it[stringPreferencesKey("mode")] ?: "sherpa"
            language=it[stringPreferencesKey("language")] ?: if(java.util.Locale.getDefault().language=="en") "en-US" else "de-DE"
            modelSize=it[stringPreferencesKey("model")] ?: "base"
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
    suspend fun refresh() {
        val data=withContext(Dispatchers.IO) { Triple(store.list(),store.list(true),captures.all()) }
        notes=data.first; deleted=data.second; pending=data.third
        if(status=="Saved") notes.firstOrNull { it.id==editor?.id && it.body!=editorBase }?.let { editor=it; editorBase=it.body }
        installed=withContext(Dispatchers.IO) { setOf("base","small").filter { models.ready(it) }.toSet() }
    }
    fun open(note:Note?=null) { editorBase=note?.body.orEmpty(); revision=System.nanoTime(); editor=note ?: Note.create(); page="editor"; status=if(note==null) "" else "Saved" }
    fun edit(body:String,title:String?=editor?.title) {
        val old=editor ?: return
        val new=old.copy(body=body,title=title?.takeIf { it.isNotBlank() },updatedAt=maxOf(Instant.now(),old.updatedAt))
        editor=new; revision=System.nanoTime(); status="Saving…"; saveJob?.cancel()
        val rev=revision
        saveJob=viewModelScope.launch {
            try { if(new.body.isNotBlank() || editorBase.isNotEmpty()) withContext(Dispatchers.IO) { store.journal(new,rev,editorBase) }; delay(400); persist(new,rev) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { status="Couldn't save"; error=e.message }
        }
    }
    private suspend fun persist(note:Note,rev:Long) {
        try {
            if(note.body.isNotBlank() || note.title!=null || withContext(Dispatchers.IO) { store.get(note.id)!=null })
                withContext(Dispatchers.IO) { store.saveEdited(note,rev,editorBase) }
            editorBase=note.body
            if(editor?.id==note.id && revision==rev) status="Saved"
        } catch(e:Exception) { status="Couldn't save"; error=e.message }
    }
    fun leave() { viewModelScope.launch { flush(); if(status!="Couldn't save") { editor=null; page="home"; refresh() } } }
    suspend fun flush() { saveJob?.cancelAndJoin(); editor?.let { persist(it,revision) } }
    fun lifecycleStop() { viewModelScope.launch { flush(); if(active!=null && !paused) pause(true) } }
    fun trash(note:Note) = task {
        require(pending.none { it.getString("target")==note.id }) { "Finish or discard pending dictation first" }
        withContext(Dispatchers.IO) { store.trash(note.id) }; undo=note; editor=null; page="home"; refresh()
    }
    fun undoDelete()=task { undo?.let { withContext(Dispatchers.IO) { store.restore(it.id) }; undo=null; refresh() } }
    fun restore(note:Note)=task { withContext(Dispatchers.IO) { store.restore(note.id) }; refresh() }
    fun start(caret:Int?)=task {
        require(active==null) { "A capture is already open" }
        if(mode=="sherpa") require(models.ready(modelSize)) { "Download a speech model in Settings before recording" }
        flush(); require(status!="Couldn't save") { "Save the note before recording" }
        val note=editor?.takeIf { it.body.isNotBlank() || it.title!=null }
        val r=withContext(Dispatchers.IO) { captures.create(note,caret,mode,language,modelSize) }
        active=r.getString("id"); paused=false; preview=""
        begin(r)
    }
    private fun begin(r:JSONObject) {
        val id=r.getString("id")
        if(r.getString("mode")=="sherpa") recorder.start(id) { message -> error=message; viewModelScope.launch { pause(true) } }
        else live.start(r.getString("language"),{ preview=it },{ text ->
            withContext(Dispatchers.IO) { synchronized(Stores.captureLock) {
                val fresh=captures.read(id); fresh.put("text",(fresh.optString("text")+" "+text).trim()); captures.write(fresh)
            } }; preview=captures.read(id).optString("text")
        },{ message -> error=message; viewModelScope.launch { pause(true) } })
    }
    suspend fun pause(interrupted:Boolean=false) {
        if(busyCapture || active==null) return
        busyCapture=true
        try { recorder.stop(); live.stop(); active?.let { withContext(Dispatchers.IO) { captures.state(it,if(interrupted) "interrupted" else "paused") } }; paused=true }
        finally { busyCapture=false }
    }
    fun togglePause()=task { if(paused) { val r=captures.read(active!!); captures.state(active!!,"recording"); begin(r); paused=false } else pause() }
    fun finish()=task {
        val id=active ?: return@task; busyCapture=true
        try { recorder.stop(); live.stop(); withContext(Dispatchers.IO) { captures.enqueue(id) }; active=null; paused=false; preview=""; refresh() }
        finally { busyCapture=false }
    }
    fun discard(id:String)=task {
        if(active==id) { recorder.stop(); live.stop(); active=null; paused=false }
        withContext(Dispatchers.IO) { captures.discard(id) }; refresh()
    }
    fun resume(id:String)=task {
        require(active==null) { "Finish the current recording first" }
        val r=withContext(Dispatchers.IO) { captures.read(id) }
        require(r.getString("state") in listOf("paused","interrupted"))
        active=id; paused=false; begin(r); captures.state(id,"recording")
    }
    fun retry(id:String)=task { withContext(Dispatchers.IO) { captures.enqueue(id) }; refresh() }
    fun download() {
        if(downloading) return
        val size=modelSize; downloading=true
        downloadJob=viewModelScope.launch {
            try { models.install(size) { progress -> viewModelScope.launch { downloadStatus=progress } }; refresh() }
            catch(e:CancellationException) { downloadStatus="Download cancelled"; throw e }
            catch(e:Exception) { error=e.message }
            finally { downloading=false }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun deleteModel()=task {
        require(active==null && pending.none { it.optString("model")==modelSize }) { "A capture still needs this model" }
        withContext(Dispatchers.IO) { models.delete(modelSize) }; refresh()
    }
    fun androidDownload()=task { live.download(language) }
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
    override fun onCleared() { recorder.release(); live.release(); super.onCleared() }
}
