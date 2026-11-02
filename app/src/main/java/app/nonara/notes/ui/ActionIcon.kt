package app.nonara.notes.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Locally drawn, consistent 24-unit outline icons; no downloaded icon assets. */
enum class ActionGlyph { BACK, SETTINGS, MIC, WRITE, COPY, SHARE, DELETE, UNDO, RESTORE, DOWNLOAD, CANCEL, PLAY, PAUSE, CHECK, IMPORT, EXPORT, TRASH }

private val glyphs=ActionGlyph.entries.associateWith { glyph ->
    ImageVector.Builder(glyph.name,24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathFillType=PathFillType.NonZero) {
            when(glyph) {
                ActionGlyph.BACK -> { moveTo(19f,12f); lineTo(5f,12f); moveTo(11f,6f); lineTo(5f,12f); lineTo(11f,18f) }
                ActionGlyph.SETTINGS -> {
                    moveTo(4f,6f); lineTo(20f,6f); moveTo(4f,12f); lineTo(20f,12f); moveTo(4f,18f); lineTo(20f,18f)
                    moveTo(8f,3f); lineTo(8f,9f); moveTo(16f,9f); lineTo(16f,15f); moveTo(10f,15f); lineTo(10f,21f)
                }
                ActionGlyph.MIC -> {
                    moveTo(9f,5f); curveTo(9f,1f,15f,1f,15f,5f); lineTo(15f,11f); curveTo(15f,15f,9f,15f,9f,11f); close()
                    moveTo(5f,10f); lineTo(5f,11f); curveTo(5f,20f,19f,20f,19f,11f); lineTo(19f,10f)
                    moveTo(12f,18f); lineTo(12f,22f); moveTo(8f,22f); lineTo(16f,22f)
                }
                ActionGlyph.WRITE -> { moveTo(4f,20f); lineTo(5f,15f); lineTo(17f,3f); lineTo(21f,7f); lineTo(9f,19f); close(); moveTo(14f,6f); lineTo(18f,10f) }
                ActionGlyph.COPY -> { moveTo(8f,8f); lineTo(20f,8f); lineTo(20f,21f); lineTo(8f,21f); close(); moveTo(15f,4f); lineTo(4f,4f); lineTo(4f,17f) }
                ActionGlyph.SHARE -> {
                    moveTo(7f,11f); lineTo(17f,5f); moveTo(7f,13f); lineTo(17f,19f)
                    moveTo(7f,12f); arcTo(2f,2f,0f,true,true,3f,12f); arcTo(2f,2f,0f,true,true,7f,12f)
                    moveTo(21f,4f); arcTo(2f,2f,0f,true,true,17f,4f); arcTo(2f,2f,0f,true,true,21f,4f)
                    moveTo(21f,20f); arcTo(2f,2f,0f,true,true,17f,20f); arcTo(2f,2f,0f,true,true,21f,20f)
                }
                ActionGlyph.DELETE, ActionGlyph.TRASH -> { moveTo(4f,6f); lineTo(20f,6f); moveTo(9f,6f); lineTo(9f,3f); lineTo(15f,3f); lineTo(15f,6f); moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f); moveTo(10f,10f); lineTo(10f,17f); moveTo(14f,10f); lineTo(14f,17f) }
                ActionGlyph.UNDO, ActionGlyph.RESTORE -> { moveTo(4f,10f); lineTo(11f,10f); moveTo(4f,10f); lineTo(4f,3f); moveTo(4f,10f); curveTo(9f,0f,22f,6f,20f,14f); curveTo(19f,19f,15f,21f,10f,20f) }
                ActionGlyph.DOWNLOAD, ActionGlyph.IMPORT -> { moveTo(12f,3f); lineTo(12f,15f); moveTo(7f,10f); lineTo(12f,15f); lineTo(17f,10f); moveTo(4f,16f); lineTo(4f,21f); lineTo(20f,21f); lineTo(20f,16f) }
                ActionGlyph.EXPORT -> { moveTo(12f,16f); lineTo(12f,3f); moveTo(7f,8f); lineTo(12f,3f); lineTo(17f,8f); moveTo(4f,16f); lineTo(4f,21f); lineTo(20f,21f); lineTo(20f,16f) }
                ActionGlyph.CANCEL -> { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
                ActionGlyph.PLAY -> { moveTo(8f,4f); lineTo(20f,12f); lineTo(8f,20f); close() }
                ActionGlyph.PAUSE -> { moveTo(8f,4f); lineTo(8f,20f); moveTo(16f,4f); lineTo(16f,20f) }
                ActionGlyph.CHECK -> { moveTo(4f,12f); lineTo(9f,17f); lineTo(20f,6f) }
            }
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAction(label:String, glyph:ActionGlyph, onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true, prominent:Boolean=false) {
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={ PlainTooltip { Text(label) } },state=rememberTooltipState()) {
        val buttonModifier=modifier.size(if(prominent) 56.dp else 48.dp).semantics { contentDescription=label }
        if(prominent) FilledIconButton(onClick=onClick,enabled=enabled,modifier=buttonModifier) { Icon(glyphs.getValue(glyph),null) }
        else IconButton(onClick=onClick,enabled=enabled,modifier=buttonModifier) { Icon(glyphs.getValue(glyph),null) }
    }
}
