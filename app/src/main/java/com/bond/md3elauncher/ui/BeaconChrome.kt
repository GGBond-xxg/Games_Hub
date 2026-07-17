package com.bond.md3elauncher.ui

import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.bond.md3elauncher.data.LauncherLayoutMode
import com.bond.md3elauncher.i18n.I18n
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BeaconTopBar(
    tabs: List<BeaconTab>,
    selected: BeaconTab,
    onSelect: (BeaconTab) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShoulderKey(text = "L1", onClick = onPrevious)

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { tab ->
                TopTabPill(
                    label = tab.localizedLabel(context),
                    selected = selected == tab,
                    width = if (tab == BeaconTab.NOW) 34.dp else 68.dp,
                    onClick = { onSelect(tab) }
                )
            }
        }

        StatusCluster(onNext = onNext)
    }
}

@Composable
private fun TopTabPill(label: String, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(width)
            .height(34.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.22f))
            .border(1.dp, if (selected) colors.primaryContainer else colors.outline.copy(alpha = 0.70f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (width <= 40.dp) 0.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusCluster(onNext: () -> Unit) {
    val context = LocalContext.current
    var timeText by remember { mutableStateOf(currentTimeText()) }
    var batteryText by remember { mutableStateOf(batteryPercentText(context)) }
    var networkKind by remember { mutableStateOf(currentNetworkKind(context)) }

    LaunchedEffect(context) {
        while (true) {
            timeText = currentTimeText()
            batteryText = batteryPercentText(context)
            networkKind = currentNetworkKind(context)
            delay(1_000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(timeText, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
            NetworkStatusIcon(kind = networkKind)
            Text(batteryText, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
        }
        ShoulderKey(text = "R1", onClick = onNext)
    }
}

private enum class NetKind { WIFI, CELLULAR, NONE }

@Composable
private fun NetworkStatusIcon(kind: NetKind) {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(18.dp)) {
        when (kind) {
            NetKind.WIFI -> {
                val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
                val cx = size.width / 2f
                val cy = size.height * 0.78f
                drawCircle(color = color, radius = size.minDimension * 0.07f, center = Offset(cx, cy))
                listOf(0.24f, 0.42f, 0.60f).forEach { scale ->
                    val r = size.minDimension * scale
                    drawArc(
                        color = color,
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = stroke
                    )
                }
            }
            NetKind.CELLULAR -> {
                val barWidth = size.width * 0.13f
                val gap = size.width * 0.06f
                val base = size.height * 0.86f
                repeat(4) { index ->
                    val height = size.height * (0.26f + index * 0.14f)
                    val left = size.width * 0.22f + index * (barWidth + gap)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, base - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
            NetKind.NONE -> {
                val stroke = Stroke(width = size.minDimension * 0.10f, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.25f, size.height * 0.25f), Offset(size.width * 0.75f, size.height * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.75f, size.height * 0.25f), Offset(size.width * 0.25f, size.height * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ShoulderKey(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun BeaconBottomBar(
    onBAction: (() -> Unit)?,
    bLabel: String,
    onSettings: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    layoutMode: LauncherLayoutMode,
    onLayoutModeChange: ((LauncherLayoutMode) -> Unit)?,
    onLaunchSelected: (() -> Unit)?,
    centerText: String
) {
    val context = LocalContext.current
    val showBottomTexts = I18n.isChinese(context)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = if (showBottomTexts) 20.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBottomTexts) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onSettings != null) {
                    BottomHint("Y", I18n.short(context, "launcher.bottom.settings", "Settings", maxChars = 5), onSettings)
                }
                if (onSearch != null) {
                    BottomHint("X", I18n.short(context, "launcher.bottom.search", "Search", maxChars = 5), onSearch)
                }
                if (onMoveUp != null) {
                    BottomHint("L3", I18n.short(context, "launcher.bottom.move_up", "Up", maxChars = 5), onMoveUp)
                }
                if (onMoveDown != null) {
                    BottomHint("R3", I18n.short(context, "launcher.bottom.move_down", "Down", maxChars = 5), onMoveDown)
                }
                if (onBAction != null) {
                    BottomHint("B", I18n.ellipsize(bLabel, 5), onBAction)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onSettings != null) CompactBottomHint("Y", onSettings)
                if (onSearch != null) CompactBottomHint("X", onSearch)
                if (onMoveUp != null) CompactBottomHint("L3", onMoveUp)
                if (onMoveDown != null) CompactBottomHint("R3", onMoveDown)
                if (onBAction != null) CompactBottomHint("B", onBAction)
            }
        }
        Spacer(Modifier.weight(1f))
        val layoutModeChange = onLayoutModeChange
        if (layoutModeChange == null) {
            if (centerText.isNotBlank()) {
                Text(
                    centerText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(if (showBottomTexts) 12.dp else 6.dp))
            }
        } else {
            LayoutModeIconButton(
                selected = layoutMode == LauncherLayoutMode.LIST,
                contentDescription = I18n.t(context, "launcher.layout.list", "列表"),
                onClick = { layoutModeChange(LauncherLayoutMode.LIST) }
            ) {
                Icon(Icons.Rounded.ViewList, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(5.dp))
            LayoutModeIconButton(
                selected = layoutMode == LauncherLayoutMode.GRID,
                contentDescription = I18n.t(context, "launcher.layout.grid", "宫格"),
                onClick = { layoutModeChange(LauncherLayoutMode.GRID) }
            ) {
                Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        if (onLaunchSelected != null) {
            if (showBottomTexts) {
                TextButton(
                    onClick = onLaunchSelected,
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    KeyBubble("A")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        I18n.short(context, "launcher.bottom.launch", "Launch", maxChars = 5),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                CompactBottomHint("A", onLaunchSelected)
            }
        }
    }
}

@Composable
private fun LayoutModeIconButton(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val accessibilityLabel = contentDescription
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
                shape = shape
            )
            .semantics { this.contentDescription = accessibilityLabel }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}

@Composable
private fun CompactBottomHint(key: String, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        KeyBubble(key, enabled = enabled)
    }
}

@Composable
private fun BottomHint(key: String, text: String, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    TextButton(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        KeyBubble(key, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyBubble(key: String, enabled: Boolean = true) {
    val background = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(key, fontWeight = FontWeight.Black, color = foreground, textAlign = TextAlign.Center)
    }
}

private fun currentNetworkKind(context: Context): NetKind {
    return runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching NetKind.NONE
        val network = manager.activeNetwork ?: return@runCatching NetKind.NONE
        val caps = manager.getNetworkCapabilities(network) ?: return@runCatching NetKind.NONE
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
            else -> NetKind.NONE
        }
    }.getOrDefault(NetKind.NONE)
}

private fun currentTimeText(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun batteryPercentText(context: Context): String {
    return runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val value = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (value in 0..100) "$value%" else "--%"
    }.getOrDefault("--%")
}
