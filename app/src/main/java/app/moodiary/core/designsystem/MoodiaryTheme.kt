package app.moodiary.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.moodiary.domain.AppPreferences

@Composable fun MoodiaryTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = preferences.theme == "DARK" || (preferences.theme == "SYSTEM" && isSystemInDarkTheme())
    val accent = when (preferences.palette) { "OCEAN" -> Color(0xFF275F8A); "PLUM" -> Color(0xFF755078); "ROSE" -> Color(0xFF974858); "SUNSET" -> Color(0xFF875316); else -> Color(0xFF326A5F) }
    val nightAccent = when (preferences.palette) { "OCEAN" -> Color(0xFF9ECCF3); "PLUM" -> Color(0xFFE3B6E3); "ROSE" -> Color(0xFFFFB1BF); "SUNSET" -> Color(0xFFF2C183); else -> Color(0xFF9FD4C2) }
    val colors = if (dark) darkColorScheme(
        primary = nightAccent, onPrimary = Color(0xFF132D27),
        primaryContainer = lerp(accent, Color.Black, 0.45f), onPrimaryContainer = nightAccent,
        secondaryContainer = Color(0xFF303B36), onSecondaryContainer = Color(0xFFDEE8E0),
        background = Color(0xFF141917), surface = Color(0xFF141917),
        surfaceContainerLowest = Color(0xFF101411), surfaceContainerLow = Color(0xFF1B211E),
        surfaceContainer = Color(0xFF202823), surfaceContainerHigh = Color(0xFF2A322D),
        surfaceContainerHighest = Color(0xFF343D36), onSurface = Color(0xFFE3E9E1), onSurfaceVariant = Color(0xFFC1CBC1)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.White, 0.85f), onPrimaryContainer = Color(0xFF193B30),
        secondaryContainer = Color(0xFFE7EEE4), onSecondaryContainer = Color(0xFF2D4032),
        background = Color(0xFFFAFBF7), surface = Color(0xFFFAFBF7),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F6EF),
        surfaceContainer = Color(0xFFEDF2E9), surfaceContainerHigh = Color(0xFFE6EDE1),
        surfaceContainerHighest = Color(0xFFDFE7DA), onSurface = Color(0xFF202B24), onSurfaceVariant = Color(0xFF4D5D51)
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
    ), content = content)
}
