package com.salizzai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.salizzai.app.SaliZZaiApp

/** Which visual mode the user picked in Settings. */
object ThemeMode {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
    const val AMOLED = "amoled"
}

private val DarkColorScheme = darkColorScheme(
    primary = SaliPurpleLight,
    onPrimary = Color(0xFF1B1233),
    primaryContainer = SaliPurpleDark,
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = SaliPurpleAccent,
    onSecondary = Color(0xFF04222A),
    secondaryContainer = Color(0xFF123B45),
    onSecondaryContainer = Color(0xFFC9F6FF),
    tertiary = SaliGold,
    onTertiary = Color(0xFF2B210D),
    tertiaryContainer = Color(0xFF3A2E14),
    onTertiaryContainer = Color(0xFFFFE7B8),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed,
    onError = Color(0xFF2A0509),
    errorContainer = Color(0xFF4A1016),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF0A090E),
    surfaceContainerHigh = Color(0xFF121016),
    surfaceVariant = Color(0xFF15131C),
)

private val LightColorScheme = lightColorScheme(
    primary = SaliPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF241253),
    secondary = Color(0xFF0F7C8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1F4FA),
    onSecondaryContainer = Color(0xFF06343D),
    tertiary = Color(0xFF8A6A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEBC6),
    onTertiaryContainer = Color(0xFF2E2100),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = Color(0xFFF2EEF8),
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFC01E2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = Color(0xFF5A0510),
)

val SaliShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
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
    val prefTheme by app.userPrefs.theme.collectAsState(initial = ThemeMode.DARK)

    val isAmoled = prefTheme == ThemeMode.AMOLED
    val effectiveDark = when (prefTheme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        else -> darkTheme
    }

    val colorScheme = when {
        isAmoled -> AmoledColorScheme
        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // Keep the system bars in sync with the in-app theme instead of leaving them
    // black (previously the status bar stayed dark in light mode).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaliTypography,
        shapes = SaliShapes,
        content = content
    )
}
