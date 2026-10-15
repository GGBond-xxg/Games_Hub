package com.bond.md3elauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** LiquidSlider's thin track and refracting capsule, with Compose's accessible slider gestures. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier,
    enabled: Boolean, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChangeFinished: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val scale by animateFloatAsState(if (enabled && (pressed || dragged)) 1.2f else 1f, label = "glassThumb")
    val trackBackdrop = rememberLayerBackdrop()
    val backdrop = LocalGlassBackdrop.current
    val combined = if (backdrop != null) rememberCombinedBackdrop(backdrop, trackBackdrop) else null
    val shape = RoundedCornerShape(50)
    val colors = MaterialTheme.colorScheme
    Slider(value = value, onValueChange = onValueChange, modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = {
            Box(Modifier.size(40.dp, 26.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
                .styleSurface(shape, colors.surfaceContainerHighest, combined))
        },
        track = { state ->
            val range = valueRange.endInclusive - valueRange.start
            val progress = if (range > 0f) ((state.value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).layerBackdrop(trackBackdrop).clip(shape)
                .background(colors.onSurface.copy(alpha = if (enabled) 0.18f else 0.08f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.primary.copy(alpha = if (enabled) 1f else 0.38f)))
            }
        })
}
