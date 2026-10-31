package app.nonara.notes.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nonara.notes.AppModel
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(model:AppModel=viewModel()) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var text by remember(model.editor?.id) { mutableStateOf(TextFieldValue(model.editor?.body.orEmpty())) }
    LaunchedEffect(model.editor?.body) { if(model.status=="Saved" && text.text!=model.editor?.body) text=TextFieldValue(model.editor?.body.orEmpty()) }
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    var confirmDownload by remember { mutableStateOf(false) }
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) model.start(if(model.page=="editor") text.selection.end else null)
        else model.error="Microphone permission was denied. You can continue typing, or allow microphone access in Android settings."
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::import) }
    fun record() {
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.start(if(model.page=="editor") text.selection.end else null)
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler(model.page!="home" || model.active!=null) {
        if(model.active!=null) model.error="Finish or cancel the recording first."
        else if(model.page=="editor") model.leave() else model.page="home"
    }
    Scaffold(topBar={ TopAppBar(title={ Text(if(model.page=="home") "nonara" else model.page.replaceFirstChar { it.uppercase() }) },
        navigationIcon={ if(model.page!="home") TextButton(onClick={ if(model.page=="editor") model.leave() else model.page="home" }) { Text("Back") } },
        actions={ if(model.page=="home") TextButton(onClick={model.page="settings"}) { Text("Settings") } }) },
        bottomBar={ if(model.page=="home" || model.page=="editor") Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={record()},modifier=Modifier.weight(1f)) { Text("Speak") }
            if(model.page=="home") OutlinedButton(onClick={model.open()},modifier=Modifier.weight(1f)) { Text("Write") }
        } }
    ) { padding ->
        when(model.page) {
            "home" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(model.query,{model.query=it},label={Text("Search thoughts")},modifier=Modifier.fillMaxWidth().testTag("search"),singleLine=true) }
                if(model.notes.isEmpty() && model.pending.isEmpty()) item { Column(Modifier.padding(vertical=48.dp)) { Text("No notes yet",style=MaterialTheme.typography.headlineSmall); Text("Notes you write will appear here."); Text("What's on your mind?",Modifier.padding(top=20.dp)) } }
                items(model.pending,key={it.getString("id")}) { capture ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        Text("Dictation · ${capture.getString("state")}")
                        if(capture.optString("error").isNotBlank()) Text(capture.optString("error"))
                        if(capture.optString("text").isNotBlank()) Text(capture.optString("text"),maxLines=3)
                        if(capture.getString("state") in listOf("paused","interrupted")) TextButton(onClick={model.resume(capture.getString("id"))}) { Text("Resume recording") }
                        Row { TextButton(onClick={model.retry(capture.getString("id"))},enabled=capture.getString("state") in listOf("failed","interrupted","paused")) { Text("Finish / Retry") }
                            TextButton(onClick={confirmDiscard=capture.getString("id")}) { Text("Discard") } }
                    } }
                }
                val filtered=model.notes.filter { model.query.isBlank() || (it.displayTitle+"\n"+it.body).contains(model.query,ignoreCase=true) }
                val groups=filtered.groupBy { val date=it.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate(); when(date) { LocalDate.now()->"Today"; LocalDate.now().minusDays(1)->"Yesterday"; else->"Earlier" } }
                groups.forEach { (group,notes) ->
                    item { Text(group,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=12.dp)) }
                    items(notes,key={it.id}) { note -> Card(onClick={model.open(note)},modifier=Modifier.fillMaxWidth().testTag("note-${note.id}")) {
                        Column(Modifier.padding(16.dp)) { Text(note.displayTitle,style=MaterialTheme.typography.titleMedium,maxLines=2)
                            Text(note.body,maxLines=3); Text(note.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")),style=MaterialTheme.typography.labelSmall) }
                    } }
                }
                item { TextButton(onClick={model.undoDelete()}) { Text("Undo last deletion") }; if(model.status.isNotBlank()) Text(model.status) }
            }
            "editor" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(model.editor?.title.orEmpty(),{model.edit(text.text,it)},label={Text("Title (optional)")},modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text,{text=it;model.edit(it.text)},label={Text("Your thought")},modifier=Modifier.fillMaxWidth().testTag("body").heightIn(min=280.dp))
                Text(model.status,style=MaterialTheme.typography.labelSmall)
                Row { TextButton(onClick={clipboard.setText(AnnotatedString(text.text))}) { Text("Copy") }
                    TextButton(onClick={ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text.text) },"Share thought")) }) { Text("Share") }
                    TextButton(onClick={model.editor?.let(model::trash)}) { Text("Delete") } }
            }
            "settings" -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Appearance",style=MaterialTheme.typography.titleLarge)
                Row { listOf("System","Light","Dark").forEach { label -> FilterChip(selected=model.theme==label,onClick={model.preference("theme",label)},label={Text(label)}) } }
                Text("Dictation",style=MaterialTheme.typography.titleLarge)
                Text("Sherpa saves audio privately until transcription succeeds. Android live mode keeps recognized text only; unfinished speech cannot be retried.")
                Row { FilterChip(model.mode=="sherpa",{model.preference("mode","sherpa")},label={Text("Sherpa")}); FilterChip(model.mode=="android",{model.preference("mode","android")},label={Text("Android live")}) }
                Row { FilterChip(model.language=="de-DE",{model.preference("language","de-DE")},label={Text("Deutsch")}); FilterChip(model.language=="en-US",{model.preference("language","en-US")},label={Text("English")}) }
                Row { listOf("base","small").forEach { size -> FilterChip(model.modelSize==size,{model.preference("model",size)},label={Text(size)}) } }
                Text(if(model.modelSize in model.installed) "Model installed · ready offline" else "Model required · ${model.models.bytes(model.modelSize)/1024/1024} MiB download")
                if(model.downloading) { Text(model.downloadStatus); TextButton(onClick={model.cancelDownload()}) { Text("Cancel download") } }
                else Row { Button(onClick={confirmDownload=true},enabled=model.modelSize !in model.installed) { Text("Download model") }; TextButton(onClick={model.deleteModel()}) { Text("Delete model") } }
                TextButton(onClick={model.androidDownload()}) { Text("Request Android language pack") }
                HorizontalDivider()
                Text("Privacy & data",style=MaterialTheme.typography.titleLarge)
                Text("Notes stay on this device. Audio is never uploaded. Models need an initial download. Platform backup is disabled; export your notes before uninstalling or replacing your phone. Pending recordings are excluded from exports.")
                Button(onClick={export.launch("nonara-export.zip")}) { Text("Export notes") }
                OutlinedButton(onClick={import.launch(arrayOf("application/zip","application/octet-stream"))}) { Text("Import notes") }
                TextButton(onClick={model.page="trash"}) { Text("Trash · retained for 30 days") }
                Text(model.status)
                HorizontalDivider(); Text("NONARA 0.1.0\nA place for passing thoughts.")
                Text("Speech libraries: sherpa-onnx / Apache-2.0, ONNX Runtime / MIT, Whisper / MIT, Silero VAD / MIT. YAML: SnakeYAML / Apache-2.0.",style=MaterialTheme.typography.bodySmall)
            }
            "trash" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if(model.deleted.isEmpty()) item { Text("Trash is empty") }
                items(model.deleted,key={it.id}) { note -> ListItem(headlineContent={Text(note.displayTitle)},trailingContent={TextButton(onClick={model.restore(note)}) {Text("Restore")} }) }
            }
        }
    }
    if(model.active!=null) AlertDialog(onDismissRequest={},title={Text(if(model.paused) "Paused" else "Listening")},text={Column { Text("Your recording stays on this device."); Text(model.preview); if(model.busyCapture) Text("Finishing current speech…") }},
        confirmButton={TextButton(onClick={model.finish()},enabled=!model.busyCapture) {Text("Finish")}},
        dismissButton={Row { TextButton(onClick={model.togglePause()},enabled=!model.busyCapture) {Text(if(model.paused) "Resume" else "Pause")}; TextButton(onClick={confirmDiscard=model.active},enabled=!model.busyCapture) {Text("Cancel")} } })
    confirmDiscard?.let { id -> AlertDialog(onDismissRequest={confirmDiscard=null},title={Text("Discard this capture?")},text={Text("Its temporary audio and recognized text will be deleted.")},confirmButton={TextButton(onClick={model.discard(id);confirmDiscard=null}) {Text("Discard")}},dismissButton={TextButton(onClick={confirmDiscard=null}) {Text("Keep")} }) }
    if(confirmDownload) AlertDialog(onDismissRequest={confirmDownload=false},title={Text("Download ${model.modelSize}?")},text={Text("${model.models.bytes(model.modelSize)/1024/1024} MiB over your current connection. Recognition works offline after installation.")},confirmButton={TextButton(onClick={confirmDownload=false;model.download()}) {Text("Download")}},dismissButton={TextButton(onClick={confirmDownload=false}) {Text("Cancel")} })
    model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("Needs attention")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}) {Text("OK")} }) }
}
