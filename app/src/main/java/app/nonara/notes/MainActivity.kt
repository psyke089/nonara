package app.nonara.notes

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import app.nonara.notes.ui.NotesScreen
import app.nonara.notes.ui.theme.NonaraTheme
import app.nonara.notes.ui.theme.Paper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Paper.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Paper.toArgb()),
        )
        setContent {
            NonaraTheme {
                NotesScreen()
            }
        }
    }
}
