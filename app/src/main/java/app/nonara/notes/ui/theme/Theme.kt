package app.nonara.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun NonaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Moss,
            onPrimary = Paper,
            secondary = FadedInk,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            onSurfaceVariant = FadedInk,
            surfaceContainer = Paper,
        ),
        typography = Typography,
        content = content,
    )
}
