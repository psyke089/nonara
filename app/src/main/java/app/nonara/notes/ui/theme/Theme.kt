package app.nonara.notes.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun NonaraTheme(mode: String = "System", content: @Composable () -> Unit) {
    val dark=mode=="Dark" || (mode=="System" && androidx.compose.foundation.isSystemInDarkTheme())
    MaterialTheme(
        colorScheme=if(dark) darkColorScheme(
            primary=Color(0xFFE6AFBD), onPrimary=Color(0xFF431D29),
            primaryContainer=Color(0xFF4C2D38), onPrimaryContainer=Color(0xFFFFD9E1),
            background=Color(0xFF191719), surface=Color(0xFF211E20),
            onBackground=Color(0xFFF1E6E5), onSurface=Color(0xFFF1E6E5),
            onSurfaceVariant=Color(0xFFCBBDBF), surfaceContainerLow=Color(0xFF272325),
            surfaceContainer=Color(0xFF30292C), outline=Color(0xFF9C8B90)
        ) else lightColorScheme(
            primary=Color(0xFF76283A), onPrimary=Color.White,
            primaryContainer=Color(0xFFF2DDE1), onPrimaryContainer=Color(0xFF4B2430),
            secondary=Moss, background=Paper, onBackground=Ink,
            surface=Paper, onSurface=Ink, onSurfaceVariant=FadedInk,
            surfaceContainerLow=Color(0xFFEDE7DD), surfaceContainer=Color(0xFFE6DED3),
            outline=Color(0xFF8B7E77)
        ), typography=Typography, content=content
    )
}
