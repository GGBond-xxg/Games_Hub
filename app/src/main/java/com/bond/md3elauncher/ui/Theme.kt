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
import com.bond.md3elauncher.data.ThemeMode

private val FixedLightScheme = lightColorScheme(
    primary = Color(0xFF006EAA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF001E33),
    secondaryContainer = Color(0xFFE6E8F8),
    onSecondaryContainer = Color(0xFF1B1B28),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFF8FAFF),
    surfaceVariant = Color(0xFFE4E8F1),
    onSurface = Color(0xFF151922),
    onSurfaceVariant = Color(0xFF404753)
)

private val FixedDarkScheme = darkColorScheme(
    primary = Color(0xFFAED2FF),
    onPrimary = Color(0xFF061B2C),
    primaryContainer = Color(0xFF314D74),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD4E0FF),
    onSecondary = Color(0xFF101A2C),
    secondaryContainer = Color(0xFF263044),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF403452),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF0B111C),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0B111C),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1B2432),
    onSurfaceVariant = Color(0xFFF1F5FF),
    outline = Color(0xFFA9B4C5),
    outlineVariant = Color(0xFF596678),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun GameHubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
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
        // 夜间模式固定使用高对比配色，避免莫奈取色导致文字发灰或看不清。
        darkTheme -> FixedDarkScheme
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> FixedLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
