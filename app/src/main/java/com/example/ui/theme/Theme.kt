package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.AppThemeMode

private val VaniVoxLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealPrimaryContainerLight,
    onPrimaryContainer = TealOnPrimaryContainerLight,
    secondary = CoralSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CoralSecondaryContainerLight,
    onSecondaryContainer = CoralOnSecondaryContainerLight,
    tertiary = IndigoTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = IndigoTertiaryContainerLight,
    onTertiaryContainer = IndigoOnTertiaryContainerLight,
    background = StudioLightBackground,
    onBackground = StudioLightTextPrimary,
    surface = StudioLightSurface,
    onSurface = StudioLightTextPrimary,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = StudioLightTextSecondary,
    surfaceContainerLow = StudioLightSurfaceContainer,
    surfaceContainer = StudioLightSurfaceVariant,
    outline = StudioLightOutline,
    outlineVariant = StudioLightOutlineVariant,
    error = ErrorLight,
    onError = Color.White
)

private val VaniVoxDarkScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondary = CoralSecondaryDark,
    onSecondary = CoralOnSecondaryDark,
    secondaryContainer = CoralSecondaryContainerDark,
    onSecondaryContainer = CoralOnSecondaryContainerDark,
    tertiary = IndigoTertiaryDark,
    onTertiary = IndigoOnTertiaryDark,
    tertiaryContainer = IndigoTertiaryContainerDark,
    onTertiaryContainer = IndigoOnTertiaryContainerDark,
    background = StudioDarkBackground,
    onBackground = StudioDarkTextPrimary,
    surface = StudioDarkSurface,
    onSurface = StudioDarkTextPrimary,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = StudioDarkTextSecondary,
    surfaceContainerLow = StudioDarkSurfaceContainer,
    surfaceContainer = StudioDarkSurfaceVariant,
    outline = StudioDarkOutline,
    outlineVariant = StudioDarkOutlineVariant,
    error = ErrorDark,
    onError = Color(0xFF450A0A)
)

private val VaniVoxShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VaniVoxTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) VaniVoxDarkScheme else VaniVoxLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaniVoxTypography,
        shapes = VaniVoxShapes,
        content = content
    )
}
