package app.nonara.notes.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nonara.notes.AppModel
import app.nonara.notes.TransferPhase
import app.nonara.notes.TransferProgress
import app.nonara.notes.label
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
    var confirmDownload by remember { mutableStateOf<String?>(null) }
    var confirmDiscardDownload by remember { mutableStateOf<String?>(null) }
    var notificationModel by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted=granted
        notificationModel?.let { model.download(it) }
        notificationModel=null
    }
    fun download(size: String) {
        if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) model.download(size)
        else { notificationModel=size; notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) model.start(if(model.page=="editor") text.selection.end else null)
        else model.error="Microphone permission was denied. You can continue typing, or allow microphone access in Android settings."
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::import) }
    fun record() {
        if(model.mode=="sherpa" && model.modelSize !in model.installed) {
            model.page="settings"
            model.error="Choose and download a speech model before your first recording. Choose a model below."
            return
        }
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.start(if(model.page=="editor") text.selection.end else null)
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler(model.page!="home" || model.active!=null) {
        if(model.active!=null) model.error="Finish or cancel the recording first."
        else if(model.page=="editor") model.leave() else model.page="home"
    }
    Scaffold(topBar={ TopAppBar(title={ Text(if(model.page=="home") "nonara" else model.page.replaceFirstChar { it.uppercase() },fontFamily=FontFamily.Serif,style=MaterialTheme.typography.headlineMedium) },
        navigationIcon={ if(model.page!="home") TextButton(onClick={ if(model.page=="editor") model.leave() else model.page="home" }) { Text("Back") } },
        actions={ if(model.page=="home") TextButton(onClick={model.page="settings"}) { Text("Settings") } }) },
        bottomBar={ if(model.page=="home" || model.page=="editor") Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={record()},modifier=Modifier.weight(1f).heightIn(min=56.dp)) { Text("Record a thought") }
            if(model.page=="home") OutlinedButton(onClick={model.open()},modifier=Modifier.heightIn(min=56.dp)) { Text("Write") }
        } }
    ) { padding ->
        when(model.page) {
            "home" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item { Column(Modifier.padding(top=22.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("A little room\nfor your thoughts.",style=MaterialTheme.typography.displaySmall,fontFamily=FontFamily.Serif)
                    Text("Write it down. Say it out loud. Keep it here.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SuggestionChip(onClick={model.page="settings"},label={ Text((if(model.mode=="sherpa") model.models.info(model.modelSize).name else "Android live")+" · "+(if(model.language=="de-DE") "Deutsch" else "English")+"  ›") })
                } }
                item { OutlinedTextField(model.query,{model.query=it},label={Text("Search thoughts")},modifier=Modifier.fillMaxWidth().testTag("search"),singleLine=true,shape=RoundedCornerShape(20.dp)) }
                if(model.notes.isEmpty() && model.pending.isEmpty()) item { Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().padding(top=24.dp)) {
                    Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("✦",style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary)
                        Text("No notes yet",style=MaterialTheme.typography.headlineSmall,fontFamily=FontFamily.Serif)
                        Text("Notes you write will appear here.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("What’s on your mind?",style=MaterialTheme.typography.titleMedium)
                    }
                } }
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
                    items(notes,key={it.id}) { note -> Card(onClick={model.open(note)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth().testTag("note-${note.id}")) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(note.displayTitle,style=MaterialTheme.typography.titleMedium,maxLines=2)
                            Text(note.body,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(note.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")),style=MaterialTheme.typography.labelSmall) }
                    } }
                }
                item { TextButton(onClick={model.undoDelete()}) { Text("Undo last deletion") }; if(model.status.isNotBlank()) Text(model.status) }
            }
            "editor" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                TextField(model.editor?.title.orEmpty(),{model.edit(text.text,it)},placeholder={Text("Untitled thought")},textStyle=MaterialTheme.typography.headlineMedium.copy(fontFamily=FontFamily.Serif),
                    colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.background,unfocusedContainerColor=MaterialTheme.colorScheme.background),modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text,{text=it;model.edit(it.text)},placeholder={Text("Let your thoughts wander…")},shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().testTag("body").heightIn(min=280.dp))
                Text(model.status,style=MaterialTheme.typography.labelSmall)
                Row { TextButton(onClick={clipboard.setText(AnnotatedString(text.text))}) { Text("Copy") }
                    TextButton(onClick={ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text.text) },"Share thought")) }) { Text("Share") }
                    TextButton(onClick={model.editor?.let(model::trash)}) { Text("Delete") } }
            }
            "settings" -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Make it yours.",style=MaterialTheme.typography.displaySmall,fontFamily=FontFamily.Serif)
                Text("Appearance",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("System","Light","Dark").forEach { label -> FilterChip(selected=model.theme==label,onClick={model.preference("theme",label)},label={Text(label)}) } }
                HorizontalDivider()
                Text("Your voice, your choice.",style=MaterialTheme.typography.headlineSmall,fontFamily=FontFamily.Serif)
                Text("Sherpa saves audio privately until transcription succeeds. Android live mode keeps recognized text only; unfinished speech cannot be retried.")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(model.mode=="sherpa",{model.preference("mode","sherpa")},label={Text("Sherpa")}); FilterChip(model.mode=="android",{model.preference("mode","android")},label={Text("Android live")}) }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(model.language=="de-DE",{model.preference("language","de-DE")},enabled=model.mode=="android" || model.models.supports(model.modelSize,"de-DE"),label={Text("Deutsch")}); FilterChip(model.language=="en-US",{model.preference("language","en-US")},label={Text("English")}) }
                Text("Speech models",style=MaterialTheme.typography.headlineSmall,fontFamily=FontFamily.Serif)
                Text("Choose a model for your next recording. Every model below supports German. Your language stays selected. Downloads stay on this device.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Downloads can continue with the screen locked. Android power limits may interrupt them; resume saved progress here.",style=MaterialTheme.typography.bodySmall)
                if(!notificationsGranted) Text("Allow notifications to see download progress and Cancel outside Nonara. If denied, track and cancel downloads here.",style=MaterialTheme.typography.bodySmall)
                model.models.ids().forEach { size ->
                    val info=model.models.info(size)
                    val selected=model.mode=="sherpa" && model.modelSize==size
                    val installed=size in model.installed
                    val transfer=model.modelTransfers[size]
                    Surface(shape=RoundedCornerShape(24.dp),color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected=selected,onClick={model.selectModel(size)},modifier=Modifier.testTag("model-$size"))
                                Column(Modifier.weight(1f)) {
                                    Text(info.name,style=MaterialTheme.typography.titleLarge)
                                    Text(if(selected) "Selected for next recording" else "Tap to select",style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(info.description)
                            Text("${model.models.bytes(size)/1024/1024} MiB · "+"Deutsch & English"+" · "+(if(installed) "Installed" else "Download required"),style=MaterialTheme.typography.labelMedium)
                            Text(model.models.definition(size).getString("license"),style=MaterialTheme.typography.bodySmall)
                            if(model.downloadingModel==size) {
                                if(transfer?.phase==TransferPhase.VERIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress={ transfer?.let { it.bytes.toFloat()/it.total.coerceAtLeast(1) } ?: 0f },modifier=Modifier.fillMaxWidth())
                                Text(model.downloadStatus,style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={model.cancelDownload()}) { Text("Cancel download") }
                            } else if(!installed) {
                                transfer?.let {
                                    Text(TransferProgress(it.phase,it.bytes,it.total).label(),style=MaterialTheme.typography.bodySmall)
                                    if(it.message.isNotBlank()) Text(it.message,style=MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick={if(transfer==null) confirmDownload=size else download(size)},enabled=!model.downloading) {
                                    Text(when(transfer?.phase) { TransferPhase.INTERRUPTED -> "Resume"; TransferPhase.FAILED -> "Retry"; else -> "Download" })
                                }
                                if(transfer!=null) TextButton(onClick={confirmDiscardDownload=size},enabled=!model.downloading) { Text("Discard partial download") }
                            } else TextButton(onClick={model.deleteModel(size)},enabled=!model.downloading) { Text("Remove download") }
                        }
                    }
                }
                TextButton(onClick={model.androidDownload()}) { Text("Request Android language pack") }
                HorizontalDivider()
                Text("Privacy & data",style=MaterialTheme.typography.titleLarge)
                Text("Notes stay on this device. Audio is never uploaded. Models need an initial download. Platform backup is disabled; export your notes before uninstalling or replacing your phone. Pending recordings are excluded from exports.")
                Button(onClick={export.launch("nonara-export.zip")}) { Text("Export notes") }
                OutlinedButton(onClick={import.launch(arrayOf("application/zip","application/octet-stream"))}) { Text("Import notes") }
                TextButton(onClick={model.page="trash"}) { Text("Trash · retained for 30 days") }
                Text(model.status)
                HorizontalDivider(); Text("NONARA 0.3.1\nA place for passing thoughts.")
                Text("Speech libraries: sherpa-onnx / Apache-2.0, ONNX Runtime / MIT, Whisper / MIT, Parakeet (NVIDIA) / CC-BY-4.0, Silero VAD / MIT. YAML: SnakeYAML / Apache-2.0.",style=MaterialTheme.typography.bodySmall)
            }
            "trash" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if(model.deleted.isEmpty()) item { Text("Trash is empty") }
                items(model.deleted,key={it.id}) { note -> ListItem(headlineContent={Text(note.displayTitle)},trailingContent={TextButton(onClick={model.restore(note)}) {Text("Restore")} }) }
            }
        }
    }
    if(model.active!=null) Dialog(onDismissRequest={},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.widthIn(max=520.dp)) {
                RecordingPanel(model.paused,model.busyCapture,model.elapsedMillis,model.microphoneLevel,model.captureLabel,model.preview,
                    onPause={model.togglePause()},onFinish={model.finish()},onDiscard={confirmDiscard=model.active})
            }
        }
    }
    confirmDiscard?.let { id -> AlertDialog(onDismissRequest={confirmDiscard=null},title={Text("Discard this capture?")},text={Text("Its temporary audio and recognized text will be deleted.")},confirmButton={TextButton(onClick={model.discard(id);confirmDiscard=null}) {Text("Discard")}},dismissButton={TextButton(onClick={confirmDiscard=null}) {Text("Keep")} }) }
    confirmDownload?.let { size -> AlertDialog(onDismissRequest={confirmDownload=null},title={Text("Download ${model.models.info(size).name}?")},text={Text("${model.models.bytes(size)/1024/1024} MiB over your current connection. Recognition works offline after installation.")},confirmButton={TextButton(onClick={confirmDownload=null;download(size)}) {Text("Download")}},dismissButton={TextButton(onClick={confirmDownload=null}) {Text("Cancel")} }) }
    confirmDiscardDownload?.let { size -> AlertDialog(onDismissRequest={confirmDiscardDownload=null},title={Text("Discard partial download?")},text={Text("Saved download progress for ${model.models.info(size).name} will be deleted. An installed model is kept.")},confirmButton={TextButton(onClick={model.discardDownload(size);confirmDiscardDownload=null}) {Text("Discard")}},dismissButton={TextButton(onClick={confirmDiscardDownload=null}) {Text("Keep")} }) }
    model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("Needs attention")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}) {Text("OK")} }) }
}
