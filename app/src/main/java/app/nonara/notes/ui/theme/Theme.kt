package app.nonara.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NonaraTheme(mode: String = "System", content: @Composable () -> Unit) {
    val dark = mode == "Dark" || (mode == "System" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) NightColors else DayColors, typography = Typography, content = content)
}
