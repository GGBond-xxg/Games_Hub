package com.bond.md3elauncher.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** A single glass thumb over a recorded track, with native switch accessibility and focus. */
@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val fraction by animateFloatAsState(if (checked) 1f else 0f, label = "glassSwitch")
    val track = rememberLayerBackdrop()
    val background = LocalGlassBackdrop.current
    val combined = if (background != null && Build.VERSION.SDK_INT >= 31) rememberCombinedBackdrop(background, track) else null
    val shape = RoundedCornerShape(50)
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(64.dp, 48.dp).alpha(if (enabled) 1f else 0.38f)
        .then(if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp, 30.dp).layerBackdrop(track)
            .background(if (checked) colors.primary.copy(alpha = 0.72f) else colors.onSurface.copy(alpha = 0.18f), shape))
        Box(Modifier.align(Alignment.CenterStart).offset { IntOffset((2 + fraction * 22).dp.roundToPx(), 0) }.size(38.dp, 26.dp)
            .styleSurface(shape, colors.surface.copy(alpha = 0.86f), combined))
    }
}
