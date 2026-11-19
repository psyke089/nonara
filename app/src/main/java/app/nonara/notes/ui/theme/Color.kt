package app.nonara.notes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF8F6F2)
private val Ink = Color(0xFF252124)
private val Mulberry = Color(0xFF802C49)
private val Eucalyptus = Color(0xFF38665D)
private val Bronze = Color(0xFF795625)

internal val DayColors = lightColorScheme(
    primary = Mulberry, onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DEE5), onPrimaryContainer = Color(0xFF561C32),
    secondary = Eucalyptus, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCECE4), onSecondaryContainer = Color(0xFF224A41),
    tertiary = Bronze, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E6CA), onTertiaryContainer = Color(0xFF573E1E),
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFEFA),
    surfaceContainer = Color(0xFFF0EDE7), surfaceContainerHigh = Color(0xFFE9E5DF),
    surfaceContainerHighest = Color(0xFFE3DED8), surfaceVariant = Color(0xFFE9E5DF),
    onSurfaceVariant = Color(0xFF625D60), outline = Color(0xFF82797C),
    outlineVariant = Color(0xFFD9D2CF), error = Color(0xFFAA3033), onError = Color.White,
)

internal val NightColors = darkColorScheme(
    primary = Color(0xFFF1A8BF), onPrimary = Color(0xFF481127),
    primaryContainer = Color(0xFF472734), onPrimaryContainer = Color(0xFFF8CFDC),
    secondary = Color(0xFF9CCDBD), onSecondary = Color(0xFF11392F),
    secondaryContainer = Color(0xFF273F38), onSecondaryContainer = Color(0xFFC6EADA),
    tertiary = Color(0xFFE3BF8D), onTertiary = Color(0xFF402D12),
    tertiaryContainer = Color(0xFF443626), onTertiaryContainer = Color(0xFFF4DBB3),
    background = Color(0xFF171617), onBackground = Color(0xFFF1ECE8),
    surface = Color(0xFF171617), onSurface = Color(0xFFF1ECE8),
    surfaceContainerLowest = Color(0xFF131213), surfaceContainerLow = Color(0xFF211F21),
    surfaceContainer = Color(0xFF2A2729), surfaceContainerHigh = Color(0xFF332F32),
    surfaceContainerHighest = Color(0xFF3D383B), surfaceVariant = Color(0xFF383236),
    onSurfaceVariant = Color(0xFFC6BDC1), outline = Color(0xFF9A8C94),
    outlineVariant = Color(0xFF494246), error = Color(0xFFFFB4AC), onError = Color(0xFF640C12),
)
