package app.nonara.notes.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nonara.notes.*
import app.nonara.notes.R
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(model:AppModel=viewModel()) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var text by remember(model.editor?.id) { mutableStateOf(TextFieldValue(model.editor?.body.orEmpty())) }
    LaunchedEffect(model.editor?.body) { if(model.status=="Saved" && text.text!=model.editor?.body) text=TextFieldValue(model.editor?.body.orEmpty()) }
    LaunchedEffect(model.page) { if (model.page!="editor") model.stopPlayback() }
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    var confirmDownload by remember { mutableStateOf<String?>(null) }
    var confirmDiscardDownload by remember { mutableStateOf<String?>(null) }
    var notificationModel by rememberSaveable { mutableStateOf<String?>(null) }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationModel?.let(model::download); notificationModel=null
    }
    fun download(id:String) {
        if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) model.download(id)
        else { notificationModel=id; notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) model.start(if(model.page=="editor") text.selection.end else null)
        else model.error="Microphone permission denied. Allow access in Android settings."
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::import) }
    fun record() {
        if(!model.models.ready(model.modelSize) || !model.models.supports(model.modelSize,model.language) || (model.streaming && !model.models.info(model.modelSize).streaming)) {
            model.page="settings"; model.error="Choose and download a compatible speech model in Settings."
            return
        }
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.start(if(model.page=="editor") text.selection.end else null)
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun back() { if(model.page=="editor") model.leave() else model.page="home" }
    BackHandler(model.page!="home" || model.active!=null) {
        if(model.active!=null) model.error="Finish or discard the recording first." else back()
    }
    Scaffold(
        topBar={ Column { TopAppBar(
            title={ if(model.page=="home") Image(painterResource(R.drawable.nonara_wordmark),"Nonara",modifier=Modifier.width(132.dp).height(60.dp),colorFilter=ColorFilter.tint(MaterialTheme.colorScheme.primary))
                else Text(model.page.replaceFirstChar { it.uppercase() },fontFamily=FontFamily.Serif,style=MaterialTheme.typography.headlineMedium) },
            navigationIcon={ if(model.page!="home") IconAction("Back",ActionGlyph.BACK,::back) },
            actions={ if(model.page=="home") IconAction("Settings",ActionGlyph.SETTINGS,{model.page="settings"}) }
        )
            Box(Modifier.fillMaxWidth().height(24.dp).padding(horizontal=20.dp),contentAlignment=Alignment.CenterStart) {
                Text(model.status,modifier=Modifier.fillMaxWidth().testTag("app-status"),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        } },
        bottomBar={
            if (model.page=="home" || model.page=="editor") {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=8.dp),
                    contentAlignment=Alignment.CenterEnd) {
                    Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surfaceContainer,
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                            if (model.page=="home") IconAction("Write",ActionGlyph.WRITE,{model.open()})
                            IconAction("Record",ActionGlyph.MIC,::record,prominent=true)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when(model.page) {
            "home" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value=model.query, onValueChange={model.query=it}, singleLine=true,
                        placeholder={Text("Search")}, leadingIcon={Icon(glyphs.getValue(ActionGlyph.SEARCH),null)},
                        shape=RoundedCornerShape(16.dp),
                        colors=OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor=Color.Transparent,
                            unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainer,
                            focusedContainerColor=MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("search")
                            .semantics { contentDescription="Search" }
                    )
                }
                if(model.notes.isEmpty() && model.pending.isEmpty()) item { Text("No notes yet",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=24.dp)) }
                items(model.pending,key={it.getString("id")}) { capture ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        Text("Dictation · ${capture.getString("state")}")
                        if(capture.optString("error").isNotBlank()) Text(capture.optString("error"))
                        if(capture.optString("text").isNotBlank()) Text(capture.optString("text"),maxLines=3)
                        FlowRow {
                            if(capture.getString("state") in listOf("paused","interrupted")) IconAction("Resume recording",ActionGlyph.PLAY,{model.resume(capture.getString("id"))})
                            val state=capture.getString("state")
                            val canRetry=state in listOf("failed","interrupted","paused") || (state=="committing" && capture.optString("error").isNotBlank())
                            IconAction("Finish / Retry",ActionGlyph.RESTORE,{model.retry(capture.getString("id"))},enabled=canRetry)
                            IconAction("Discard",ActionGlyph.DELETE,{confirmDiscard=capture.getString("id")})
                        }
                    } }
                }
                val filtered=model.notes.filter { model.query.isBlank() || it.title.orEmpty().contains(model.query,ignoreCase=true) || it.body.contains(model.query,ignoreCase=true) }
                items(filtered,key={it.id}) { note ->
                    Surface(onClick={model.open(note)},color=MaterialTheme.colorScheme.background,
                        modifier=Modifier.fillMaxWidth().testTag("note-${note.id}")) {
                        Column {
                            Column(Modifier.padding(horizontal=4.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(note.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")),
                                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
                                note.title?.takeIf { it.isNotBlank() }?.let {
                                    Text(it,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                                }
                                Text(note.body,style=MaterialTheme.typography.bodyLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                if(model.canUndo) item { IconAction("Undo last deletion",ActionGlyph.UNDO,{model.undoDelete()}) }
            }
            "editor" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                val fieldColors=TextFieldDefaults.colors(
                    focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent
                )
                Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        TextField(model.editor?.title.orEmpty(),{model.edit(text.text,it)},placeholder={Text("Title")},
                            textStyle=MaterialTheme.typography.headlineMedium,colors=fieldColors,modifier=Modifier.fillMaxWidth())
                        HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
                        TextField(text,{text=it;model.edit(it.text)},placeholder={Text("Note")},colors=fieldColors,
                            modifier=Modifier.fillMaxWidth().testTag("body").heightIn(min=240.dp))
                    }
                }
                if(model.editorConflict) {
                    Text("This note changed while editing. Save your draft as a copy to keep both versions.",style=MaterialTheme.typography.bodySmall)
                    IconAction("Save as copy",ActionGlyph.CHECK,{model.saveConflictCopy()})
                }
                FlowRow {
                    IconAction("Copy",ActionGlyph.COPY,{clipboard.setText(AnnotatedString(text.text))})
                    IconAction("Share",ActionGlyph.SHARE,{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text.text) },"Share note"))})
                    IconAction("Delete",ActionGlyph.DELETE,{model.editor?.let(model::trash)})
                }
                if (model.recordings.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Recordings",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
                    model.recordings.forEach { recording ->
                        val date=Instant.ofEpochMilli(recording.recordedAtMillis).atZone(ZoneId.systemDefault())
                        val playing=model.playingRecording==recording.id
                        ListItem(
                            headlineContent={ Text(date.format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))) },
                            supportingContent={ Text(recordingTime(recording.durationMillis)) },
                            trailingContent={ IconAction(if(playing) "Pause recording" else "Play recording",
                                if(playing) ActionGlyph.PAUSE else ActionGlyph.PLAY,{model.toggleRecordingPlayback(recording)}) },
                            modifier=Modifier.testTag("recording-${recording.id}")
                        )
                    }
                }
            }
            "settings" -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Appearance",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("System","Light","Dark").forEach { label -> FilterChip(model.theme==label,{model.preference("theme",label)},label={Text(label)}) } }
                HorizontalDivider()
                Text("Dictation",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!model.streaming,{model.preference("streaming","false")},label={Text("Recording")},modifier=Modifier.testTag("dictation-recording"))
                    FilterChip(model.streaming,{model.preference("streaming","true")},label={Text("Streaming")},modifier=Modifier.testTag("dictation-streaming"))
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for((language,label) in listOf("de-DE" to "Deutsch","en-US" to "English")) FilterChip(model.language==language,{model.preference("language",language)},enabled=model.models.supports(model.modelSize,language),label={Text(label)},modifier=Modifier.testTag("language-$language"))
                }
                Text("Speech models",style=MaterialTheme.typography.titleMedium)
                if(model.streaming && !model.models.info(model.modelSize).streaming) Text("Select a streaming model.",style=MaterialTheme.typography.bodySmall)
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    model.models.ids().filter { !model.streaming || model.models.info(it).streaming }.forEach { id ->
                        SpeechModelCard(model,id,
                            onDownload={ if(model.modelTransfers[id]==null) confirmDownload=id else download(id) },
                            onDiscard={confirmDiscardDownload=id})
                    }
                }
                HorizontalDivider()
                Text("Data",style=MaterialTheme.typography.titleMedium)
                FlowRow {
                    IconAction("Import notes",ActionGlyph.IMPORT,{import.launch(arrayOf("application/zip","application/octet-stream"))})
                    IconAction("Export notes",ActionGlyph.EXPORT,{export.launch("nonara-notes.zip")})
                    IconAction("Trash",ActionGlyph.TRASH,{model.page="trash"})
                }
                Text("NONARA 0.4.0",style=MaterialTheme.typography.labelSmall)
                Text("Speech libraries: sherpa-onnx / Apache-2.0, ONNX Runtime / MIT, Whisper / MIT, NVIDIA Parakeet, Canary, FastConformer / CC-BY-4.0, Omnilingual / Apache-2.0, Nemotron / OpenMDW-1.1, Kroko / CC-BY-SA, Silero VAD / MIT. YAML: SnakeYAML / Apache-2.0.",style=MaterialTheme.typography.bodySmall)
            }
            "trash" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if(model.deleted.isEmpty()) item { Text("Trash is empty") }
                items(model.deleted,key={it.id}) { note ->
                    ListItem(headlineContent={Text(note.title?.takeIf { it.isNotBlank() } ?: note.body,maxLines=2,overflow=TextOverflow.Ellipsis)},
                        trailingContent={IconAction("Restore",ActionGlyph.RESTORE,{model.restore(note)})})
                }
            }
        }
    }
    if(model.active!=null) Dialog(onDismissRequest={},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.widthIn(max=520.dp)) { RecordingPanel(model.paused,model.busyCapture,model.elapsedMillis,model.microphoneLevel,model.captureLabel,
                onPause={model.togglePause()},onFinish={model.finish()},onDiscard={confirmDiscard=model.active},preview=model.livePreview,previewStatus=model.previewStatus) }
        }
    }
    confirmDiscard?.let { id -> AlertDialog(onDismissRequest={confirmDiscard=null},title={Text("Discard recording?")},text={Text("Temporary audio and text will be deleted.")},
        confirmButton={IconAction("Discard",ActionGlyph.DELETE,{model.discard(id);confirmDiscard=null})},dismissButton={IconAction("Keep",ActionGlyph.CANCEL,{confirmDiscard=null})}) }
    confirmDownload?.let { id -> AlertDialog(onDismissRequest={confirmDownload=null},title={Text("Download ${model.models.info(id).name}?")},text={Text("${model.models.bytes(id)/1024/1024} MiB")},
        confirmButton={IconAction("Download",ActionGlyph.DOWNLOAD,{confirmDownload=null;download(id)})},dismissButton={IconAction("Cancel",ActionGlyph.CANCEL,{confirmDownload=null})}) }
    confirmDiscardDownload?.let { id -> AlertDialog(onDismissRequest={confirmDiscardDownload=null},title={Text("Discard partial download?")},text={Text("Saved progress will be deleted. Installed model files are kept.")},
        confirmButton={IconAction("Discard",ActionGlyph.DELETE,{model.discardDownload(id);confirmDiscardDownload=null})},dismissButton={IconAction("Keep",ActionGlyph.CANCEL,{confirmDiscardDownload=null})}) }
    model.error?.let { message ->
        AlertDialog(onDismissRequest={model.error=null},title={Text("Unable to continue")},text={Text(message)},
            confirmButton={ if(model.editorConflict) IconAction("Save as copy",ActionGlyph.CHECK,{model.saveConflictCopy()}) else IconAction("OK",ActionGlyph.CHECK,{model.error=null}) },
            dismissButton={ if(model.editorConflict) IconAction("Keep editing",ActionGlyph.CANCEL,{model.error=null}) })
    }
}
