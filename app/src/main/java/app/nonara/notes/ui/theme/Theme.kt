package app.nonara.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun NonaraTheme(mode: String = "System", content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if(mode == "Dark" || (mode == "System" && androidx.compose.foundation.isSystemInDarkTheme())) androidx.compose.material3.darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFE7AABB)) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF76283A),
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
