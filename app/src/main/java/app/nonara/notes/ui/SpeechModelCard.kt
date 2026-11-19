package app.nonara.notes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nonara.notes.AppModel
import app.nonara.notes.TransferPhase
import app.nonara.notes.TransferProgress
import app.nonara.notes.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeechModelCard(model: AppModel, id: String, onDownload: () -> Unit, onDiscard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val info = model.models.info(id)
    val selected = model.modelSize == id
    val installed = id in model.installed
    val compatible = info.supports(model.language)
    val transfer = model.modelTransfers[id]
    val downloading = model.downloadingModel == id
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("model-card-$id"),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(end=4.dp), verticalAlignment=Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min=56.dp).testTag("model-$id")
                        .selectable(selected, enabled=compatible, role=Role.RadioButton, onClick={model.selectModel(id)})
                        .padding(start=12.dp, end=4.dp, top=7.dp, bottom=7.dp),
                    verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(selected, onClick=null, enabled=compatible, modifier=Modifier.size(18.dp))
                    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(info.name, style=MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            info.languages.forEach { language ->
                                ModelBadge(if (language=="de-DE") "DE" else "EN","model-$id-language-$language")
                            }
                            if (info.streaming) ModelBadge("Streaming","model-$id-streaming")
                            Text("${model.models.bytes(id)/1024/1024} MiB",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                            if (installed) Text("Installed",style=MaterialTheme.typography.labelSmall,color=colors.secondary)
                        }
                    }
                }
                when {
                    downloading -> IconAction("Cancel download",ActionGlyph.CANCEL,{model.cancelDownload()})
                    installed -> IconAction("Remove download",ActionGlyph.DELETE,{model.deleteModel(id)})
                    else -> {
                        val label = when (transfer?.phase) {
                            TransferPhase.INTERRUPTED -> "Resume download"
                            TransferPhase.FAILED -> "Retry download"
                            else -> "Download"
                        }
                        IconAction(label,ActionGlyph.DOWNLOAD,onDownload,enabled=!model.downloading)
                    }
                }
            }
            if (!compatible || downloading || (!installed && transfer!=null)) {
                Column(Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if (!compatible) Text("Unavailable for the selected language",style=MaterialTheme.typography.bodySmall)
                    if (downloading) {
                        if (transfer==null || transfer.phase==TransferPhase.VERIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress={transfer.bytes.toFloat()/transfer.total.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
                        Text(model.downloadStatus,style=MaterialTheme.typography.bodySmall)
                    } else if (!installed && transfer!=null) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(TransferProgress(transfer.phase,transfer.bytes,transfer.total).label(),style=MaterialTheme.typography.labelMedium)
                                if (transfer.message.isNotBlank()) Text(transfer.message,style=MaterialTheme.typography.bodySmall)
                            }
                            IconAction("Discard partial download",ActionGlyph.DELETE,onDiscard,enabled=!model.downloading)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelBadge(label: String, tag: String) {
    Surface(modifier=Modifier.testTag(tag),shape=RoundedCornerShape(4.dp),
        color=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(label,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=4.dp))
    }
}
