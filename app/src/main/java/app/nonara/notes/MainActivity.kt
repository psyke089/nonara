package app.nonara.notes

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import app.nonara.notes.ui.NotesScreen
import app.nonara.notes.ui.theme.NonaraTheme
import app.nonara.notes.ui.theme.Paper

class MainActivity : ComponentActivity() {
    private val model by lazy { androidx.lifecycle.ViewModelProvider(this)[AppModel::class.java] }
    override fun onStop() { if(!isChangingConfigurations) model.lifecycleStop(); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Paper.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Paper.toArgb()),
        )
        setContent {
            NonaraTheme(model.theme) {
                val dark=MaterialTheme.colorScheme.background.luminance()<0.5f
                SideEffect {
                    val style=if(dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT,Paper.toArgb())
                    enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
                }
                NotesScreen(model)
            }
        }
    }
}
