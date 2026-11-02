package app.nonara.notes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nonara.notes.recordingTime

@Composable
fun RecordingPanel(paused:Boolean, busy:Boolean, elapsed:Long, level:Float, label:String,
                   onPause:()->Unit, onFinish:()->Unit, onDiscard:()->Unit, preview:String="", previewStatus:String="") {
    val ink=MaterialTheme.colorScheme.primary
    val displayedLevel by animateFloatAsState(if(paused) 0f else level, label="Microphone level")
    Surface(shape=RoundedCornerShape(32.dp), color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(if(paused) MaterialTheme.colorScheme.outline else ink,CircleShape))
                Text(if(busy) "Saving recording…" else if(paused) "Recording paused" else "Recording",style=MaterialTheme.typography.labelLarge,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
            }
            Text(recordingTime(elapsed),style=MaterialTheme.typography.displayLarge,fontFamily=FontFamily.Monospace)
            Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription="Microphone activity"; stateDescription=if(paused) "Microphone paused" else if(level>0.03f) "Sound detected" else "Quiet" }) {
                val bars=35
                for(i in 0 until bars) {
                    val shape=0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(i * 1.7f))
                    val height=4.dp.toPx()+displayedLevel * size.height * shape
                    val x=size.width * (i+0.5f)/bars
                    drawLine(ink.copy(alpha=if(paused) 0.25f else 0.85f),Offset(x,(size.height-height)/2),Offset(x,(size.height+height)/2),4.dp.toPx(),StrokeCap.Round)
                }
            }
            if(previewStatus.isNotBlank()) Text(previewStatus,style=MaterialTheme.typography.labelMedium)
            if(preview.isNotBlank()) Text(preview,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.fillMaxWidth().heightIn(max=160.dp).verticalScroll(rememberScrollState()).semantics { contentDescription="Live transcript preview" })
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterHorizontally)) {
                IconAction(if(paused) "Resume" else "Pause",if(paused) ActionGlyph.PLAY else ActionGlyph.PAUSE,onPause,enabled=!busy,prominent=true)
                IconAction("Finish",ActionGlyph.CHECK,onFinish,enabled=!busy,prominent=true)
                IconAction("Discard recording",ActionGlyph.DELETE,onDiscard,enabled=!busy)
            }
        }
    }
}
