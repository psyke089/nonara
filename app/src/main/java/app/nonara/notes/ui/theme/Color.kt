package app.nonara.notes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF5F7FC)
private val Ink = Color(0xFF111B32)
private val Cobalt = Color(0xFF314DE2)
private val Citron = Color(0xFF4F6316)
private val Iris = Color(0xFF6540A4)

internal val DayColors = lightColorScheme(
    primary = Cobalt, onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE6FF), onPrimaryContainer = Color(0xFF17287D),
    secondary = Citron, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F3AF), onSecondaryContainer = Color(0xFF324300),
    tertiary = Iris, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE2FF), onTertiaryContainer = Color(0xFF432476),
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCFCFF),
    surfaceContainer = Color(0xFFE9EDF7), surfaceContainerHigh = Color(0xFFE1E7F2),
    surfaceContainerHighest = Color(0xFFD9DFEB), surfaceVariant = Color(0xFFE1E7F2),
    onSurfaceVariant = Color(0xFF505B72), outline = Color(0xFF707C92),
    outlineVariant = Color(0xFFC9D1E1), error = Color(0xFFB62F42), onError = Color.White,
)

internal val NightColors = darkColorScheme(
    primary = Color(0xFF9DB7FF), onPrimary = Color(0xFF092451),
    primaryContainer = Color(0xFF243968), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFD2EB80), onSecondary = Color(0xFF293800),
    secondaryContainer = Color(0xFF364321), onSecondaryContainer = Color(0xFFE5F8AD),
    tertiary = Color(0xFFC9B1FF), onTertiary = Color(0xFF321C56),
    tertiaryContainer = Color(0xFF3F305D), onTertiaryContainer = Color(0xFFEBDFFF),
    background = Color(0xFF0D1320), onBackground = Color(0xFFE9EEFA),
    surface = Color(0xFF0D1320), onSurface = Color(0xFFE9EEFA),
    surfaceContainerLowest = Color(0xFF090E18), surfaceContainerLow = Color(0xFF161E2E),
    surfaceContainer = Color(0xFF1D2739), surfaceContainerHigh = Color(0xFF263246),
    surfaceContainerHighest = Color(0xFF303D52), surfaceVariant = Color(0xFF293447),
    onSurfaceVariant = Color(0xFFB7C2D8), outline = Color(0xFF8D9BB2),
    outlineVariant = Color(0xFF3D4B62), error = Color(0xFFFFABB3), onError = Color(0xFF670C24),
)
