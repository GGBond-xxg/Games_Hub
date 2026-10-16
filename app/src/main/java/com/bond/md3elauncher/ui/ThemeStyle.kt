package com.bond.md3elauncher.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.data.GlassSettings
import com.bond.md3elauncher.data.ThemeStyle
import com.bond.md3elauncher.i18n.I18n
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

internal data class ThemePreferences(
    val style: ThemeStyle = ThemeStyle.M3, val glass: GlassSettings = GlassSettings(),
    val setStyle: (ThemeStyle) -> Unit = {}, val setGlass: (GlassSettings) -> Unit = {},
    val backgroundMotion: Boolean = false, val setBackgroundMotion: (Boolean) -> Unit = {}
)
internal val LocalThemePreferences = staticCompositionLocalOf { ThemePreferences() }
internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Record only the backdrop, never a subtree that draws itself. Dialog windows get their own source. */
@Composable
internal fun StyleBackdrop(content: @Composable () -> Unit) {
    val prefs = LocalThemePreferences.current
    val glass = prefs.style == ThemeStyle.GLASS
    val styled = prefs.style != ThemeStyle.M3
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = styled && prefs.backgroundMotion && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val transition = if (animate) rememberInfiniteTransition(label = "backgroundWaves") else null
    val phases = listOf(20_000, 15_000, 10_000).mapIndexed { index, duration ->
        transition?.animateFloat(initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "wave$index") ?: remember { mutableFloatStateOf(0f) }
    }
    val paths = remember { List(3) { Path() } }

    val backdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    val waveBrushes = remember(colors) {
        val alpha = if (colors.background.luminance() < 0.5f) 0.25f else 0.30f
        listOf(colors.primary, colors.secondary, colors.tertiary).mapIndexed { index, color ->
            Brush.verticalGradient(listOf(color.copy(alpha = alpha - index * 0.02f), Color.Transparent))
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (styled) Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            drawRect(colors.background)
            // Independent three-wave geometry; only drawing observes animation state.
            paths.forEachIndexed { index, path ->
                val phase = phases[index].value
                val direction = if (index % 2 == 0) 1f else -1f
                val bend = (phase * 2f - 1f) * direction
                val base = size.height * (0.5f + index * 0.1f)
                val amplitude = size.height * (0.34f - index * 0.035f)
                path.reset()
                path.moveTo(0f, base)
                path.cubicTo(size.width * (0.25f + 0.12f * bend), base - amplitude * bend,
                    size.width * (0.75f - 0.12f * bend), base + amplitude * bend, size.width, base)
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
                path.close()
                drawPath(path, waveBrushes[index])
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides if (glass) backdrop else null) { content() }
    }
}

@Composable
internal fun Modifier.styleSurface(shape: Shape = RoundedCornerShape(20.dp), color: Color = MaterialTheme.colorScheme.surface, backdrop: Backdrop? = LocalGlassBackdrop.current): Modifier {
    val prefs = LocalThemePreferences.current
    if (prefs.style != ThemeStyle.GLASS) return background(color, shape)
    val settings = prefs.glass.clamped()
    // Respect deliberately translucent nested panels instead of turning every layer opaque.
    val tint = color.copy(alpha = if (Build.VERSION.SDK_INT < 31) 0.94f else settings.opacity * color.alpha)
    if (backdrop == null || Build.VERSION.SDK_INT < 31) return background(tint, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    return drawBackdrop(backdrop, shape = { shape }, effects = {
        blur(settings.blur.dp.toPx())
        // Backdrop 1.0.6 requires a rounded-rectangle SDF for lens effects.
        if (Build.VERSION.SDK_INT >= 33 && shape is androidx.compose.foundation.shape.CornerBasedShape) {
            lens(10.dp.toPx(), settings.refraction.dp.toPx())
        }
    }, shadow = null, onDrawSurface = { drawRect(tint) })
}

@Composable
internal fun ThemeStyleSetting() {
    val prefs = LocalThemePreferences.current
    val context = LocalContext.current
    var showGlass by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(I18n.t(context, "theme.style", "Interface style"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (prefs.style == ThemeStyle.GLASS) HubTextButton(onClick = { showGlass = true }) {
            Text(I18n.t(context, "theme.glass.settings", "Glass settings"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeStyle.entries.forEach { style ->
            HubFilterChip(selected = prefs.style == style, onClick = { prefs.setStyle(style) },
                label = { Text(I18n.t(context, "theme.${style.name.lowercase()}", style.name), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
    if (prefs.style != ThemeStyle.M3) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(
            value = prefs.backgroundMotion, role = Role.Switch, onValueChange = prefs.setBackgroundMotion
        ).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(I18n.t(context, "theme.background_motion", "Flowing background"), modifier = Modifier.weight(1f).padding(end = 16.dp),
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HubSwitch(prefs.backgroundMotion, null)
        }
    }
    Spacer(Modifier.height(8.dp))
    if (showGlass && prefs.style == ThemeStyle.GLASS) GlassSettingsDialog(onDismiss = { showGlass = false })
}

@Composable
private fun GlassSettingsDialog(onDismiss: () -> Unit) {
    val prefs = LocalThemePreferences.current
    val context = LocalContext.current
    fun t(key: String) = I18n.t(context, key, key)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).heightIn(max = 480.dp).fillMaxHeight(0.92f).clip(RoundedCornerShape(28.dp))) {
            StyleBackdrop {
                HubSurface(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("theme.glass.settings"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text(t("theme.glass.blur"), maxLines = 1)
                            HubSlider(prefs.glass.blur, { prefs.setGlass(prefs.glass.copy(blur = it)) }, valueRange = 0f..30f)
                            Text(t("theme.glass.refraction"), maxLines = 1)
                            HubSlider(prefs.glass.refraction, { prefs.setGlass(prefs.glass.copy(refraction = it)) }, valueRange = 0f..28f)
                            Text(t("theme.glass.opacity"), maxLines = 1)
                            HubSlider(prefs.glass.opacity, { prefs.setGlass(prefs.glass.copy(opacity = it)) }, valueRange = 0.4f..0.95f)
                            Text(t("theme.glass.compatibility"), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                            HubTextButton(onClick = { prefs.setGlass(GlassSettings()) }) { Text(t("theme.reset"), maxLines = 1) }
                            HubButton(onClick = onDismiss) { Text(t("theme.done"), maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
