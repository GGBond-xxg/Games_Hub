package com.bond.md3elauncher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bond.md3elauncher.data.ThemeColor
import com.bond.md3elauncher.data.ThemeMode

private data class ManualPalette(
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color
)

private fun manualPalette(themeColor: ThemeColor): ManualPalette = when (themeColor) {
    ThemeColor.PINK -> ManualPalette(
        lightPrimary = Color(0xFF9B405F),
        lightPrimaryContainer = Color(0xFFFFD9E2),
        lightOnPrimaryContainer = Color(0xFF3E001D),
        darkPrimary = Color(0xFFFFB1C8),
        darkOnPrimary = Color(0xFF5F1134),
        darkPrimaryContainer = Color(0xFF7D294B),
        darkOnPrimaryContainer = Color(0xFFFFD9E2)
    )
    ThemeColor.BLUE -> ManualPalette(
        lightPrimary = Color(0xFF006EAA),
        lightPrimaryContainer = Color(0xFFD6E9FF),
        lightOnPrimaryContainer = Color(0xFF001E33),
        darkPrimary = Color(0xFFAED2FF),
        darkOnPrimary = Color(0xFF003353),
        darkPrimaryContainer = Color(0xFF004A76),
        darkOnPrimaryContainer = Color(0xFFD6E9FF)
    )
    ThemeColor.PURPLE -> ManualPalette(
        lightPrimary = Color(0xFF76558F),
        lightPrimaryContainer = Color(0xFFF2DAFF),
        lightOnPrimaryContainer = Color(0xFF2D0846),
        darkPrimary = Color(0xFFDFB7F8),
        darkOnPrimary = Color(0xFF44255D),
        darkPrimaryContainer = Color(0xFF5D3D75),
        darkOnPrimaryContainer = Color(0xFFF2DAFF)
    )
    ThemeColor.GREEN -> ManualPalette(
        lightPrimary = Color(0xFF3F6650),
        lightPrimaryContainer = Color(0xFFC1ECD0),
        lightOnPrimaryContainer = Color(0xFF002112),
        darkPrimary = Color(0xFFA5D0B4),
        darkOnPrimary = Color(0xFF0D3822),
        darkPrimaryContainer = Color(0xFF28503A),
        darkOnPrimaryContainer = Color(0xFFC1ECD0)
    )
    ThemeColor.ORANGE -> ManualPalette(
        lightPrimary = Color(0xFF8C4F24),
        lightPrimaryContainer = Color(0xFFFFDBC7),
        lightOnPrimaryContainer = Color(0xFF321200),
        darkPrimary = Color(0xFFFFB786),
        darkOnPrimary = Color(0xFF512400),
        darkPrimaryContainer = Color(0xFF703713),
        darkOnPrimaryContainer = Color(0xFFFFDBC7)
    )
}

internal fun themeColorPreview(themeColor: ThemeColor): Color = manualPalette(themeColor).lightPrimary

private fun manualLightColorScheme(themeColor: ThemeColor) = lightColorScheme(
    primary = manualPalette(themeColor).lightPrimary,
    onPrimary = Color.White,
    primaryContainer = manualPalette(themeColor).lightPrimaryContainer,
    onPrimaryContainer = manualPalette(themeColor).lightOnPrimaryContainer,
    secondary = manualPalette(themeColor).lightPrimary,
    onSecondary = Color.White,
    secondaryContainer = manualPalette(themeColor).lightPrimaryContainer,
    onSecondaryContainer = manualPalette(themeColor).lightOnPrimaryContainer,
    tertiary = manualPalette(themeColor).lightPrimary,
    onTertiary = Color.White,
    tertiaryContainer = manualPalette(themeColor).lightPrimaryContainer,
    onTertiaryContainer = manualPalette(themeColor).lightOnPrimaryContainer,
    background = Color(0xFFFFF8FA),
    surface = Color(0xFFFFF8FA),
    surfaceVariant = Color(0xFFF1E3E7),
    onSurface = Color(0xFF211A1D),
    onSurfaceVariant = Color(0xFF514348)
)

private fun manualDarkColorScheme(themeColor: ThemeColor) = darkColorScheme(
    primary = manualPalette(themeColor).darkPrimary,
    onPrimary = manualPalette(themeColor).darkOnPrimary,
    primaryContainer = manualPalette(themeColor).darkPrimaryContainer,
    onPrimaryContainer = manualPalette(themeColor).darkOnPrimaryContainer,
    secondary = manualPalette(themeColor).darkPrimary,
    onSecondary = manualPalette(themeColor).darkOnPrimary,
    secondaryContainer = manualPalette(themeColor).darkPrimaryContainer,
    onSecondaryContainer = manualPalette(themeColor).darkOnPrimaryContainer,
    tertiary = manualPalette(themeColor).darkPrimary,
    onTertiary = manualPalette(themeColor).darkOnPrimary,
    tertiaryContainer = manualPalette(themeColor).darkPrimaryContainer,
    onTertiaryContainer = manualPalette(themeColor).darkOnPrimaryContainer,
    background = Color(0xFF181113),
    onBackground = Color(0xFFF0DEE3),
    surface = Color(0xFF181113),
    onSurface = Color(0xFFF0DEE3),
    surfaceVariant = Color(0xFF382D31),
    onSurfaceVariant = Color(0xFFD7C1C7),
    outline = Color(0xFFA98D95),
    outlineVariant = Color(0xFF594148),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun GameHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    themeColor: ThemeColor = ThemeColor.PINK,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> manualDarkColorScheme(themeColor)
        else -> manualLightColorScheme(themeColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
