package com.salizzai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.salizzai.app.SaliZZaiApp

private val DarkColorScheme = darkColorScheme(
    primary = SaliPurpleLight,
    onPrimary = Color(0xFF20172F),
    primaryContainer = SaliPurpleDark,
    onPrimaryContainer = Color(0xFFF3F0EA),
    secondary = SaliPurpleAccent,
    onSecondary = Color(0xFF08252B),
    secondaryContainer = Color(0xFF16424B),
    onSecondaryContainer = Color(0xFFD5F9FF),
    tertiary = SaliGold,
    onTertiary = Color(0xFF2B210D),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.15f),
    onErrorContainer = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = SaliPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF27154C),
    secondary = SaliPurpleAccent,
    onSecondary = Color(0xFF08252B),
    secondaryContainer = Color(0xFFD7F6FA),
    onSecondaryContainer = Color(0xFF0A3B46),
    tertiary = SaliGold,
    onTertiary = Color(0xFF2B210D),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.10f),
    onErrorContainer = ErrorRed,
)

val SaliShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SaliZZaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val app = SaliZZaiApp.get(context)
    val prefTheme by remember { app.userPrefs.theme }.collectAsState(initial = "dark")

    val effectiveDark = when (prefTheme) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val colorScheme = if (effectiveDark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaliTypography,
        shapes = SaliShapes,
        content = content
    )
}
