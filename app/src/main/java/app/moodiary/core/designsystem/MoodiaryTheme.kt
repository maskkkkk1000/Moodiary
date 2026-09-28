package app.moodiary.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.moodiary.domain.AppPreferences

@Composable fun MoodiaryTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = preferences.theme == "DARK" || (preferences.theme == "SYSTEM" && isSystemInDarkTheme())
    val accent = when (preferences.palette) { "OCEAN" -> Color(0xFF275F8A); "PLUM" -> Color(0xFF755078); "ROSE" -> Color(0xFF974858); "SUNSET" -> Color(0xFF875316); else -> Color(0xFF326A5F) }
    val colors = if (dark) darkColorScheme(primary = when (preferences.palette) { "OCEAN" -> Color(0xFF9ECCF3); "PLUM" -> Color(0xFFE3B6E3); "ROSE" -> Color(0xFFFFB1BF); "SUNSET" -> Color(0xFFF2C183); else -> Color(0xFF9FD4C2) })
    else lightColorScheme(primary = accent, background = Color(0xFFF8FAF6), surface = Color(0xFFF8FAF6))
    MaterialTheme(colorScheme = colors, content = content)
}
