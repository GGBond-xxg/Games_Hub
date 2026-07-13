package com.bond.md3elauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.data.LandscapeMode
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.data.SafeMarginSettings
import com.bond.md3elauncher.data.ThemeMode
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings
import com.bond.md3elauncher.emulator.InternalEmulators
import com.bond.md3elauncher.emulator.fc.FcExternalEmulatorProfiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsBeaconScreen(
    platforms: List<PlatformConfig>,
    installedApps: List<InstalledApp>,
    favorites: Set<String>,
    androidGames: Set<String>,
    landscapeMode: LandscapeMode,
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    safeMargins: SafeMarginSettings,
    scraperSettings: ScraperSettings,
    tabOrder: List<String>,
    isScanning: Boolean,
    onOpenPlatform: (PlatformConfig) -> Unit,
    onOpenAndroid: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRescanAll: () -> Unit,
    onSetLandscapeMode: (LandscapeMode) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetSafeMargins: (SafeMarginSettings) -> Unit,
    onSaveScraperSettings: (ScraperSettings) -> Unit,
    onSaveTabOrder: (List<String>) -> Unit,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onControllerShortcutCaptureHandlerChange: (((AndroidKeyEvent) -> Boolean)?) -> Unit
) {
    var showDeferredSections by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onLaunchSelectedChange(null)
        // 先渲染设置页首屏，再补下面的高级设置，减少第一次点设置的卡顿感。
        showDeferredSections = true
    }
    var showEmulatorManager by rememberSaveable { mutableStateOf(false) }
    var showControllerShortcuts by rememberSaveable { mutableStateOf(false) }
    if (showControllerShortcuts) {
        ControllerShortcutSettingsScreen(
            onBack = { showControllerShortcuts = false },
            onCaptureHandlerChange = onControllerShortcutCaptureHandlerChange
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "platformManager") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    "平台管理",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                if (showEmulatorManager) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("模拟器", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { showEmulatorManager = false }, modifier = Modifier.height(34.dp)) {
                            Text("返回")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    EmulatorManagerRows(
                        tabOrder = tabOrder,
                        platforms = platforms,
                        onOpenPlatform = onOpenPlatform,
                        onSaveTabOrder = onSaveTabOrder
                    )
                } else {
                    PlatformManagerRows(
                        tabOrder = tabOrder,
                        platforms = platforms,
                        installedApps = installedApps,
                        favorites = favorites,
                        androidGames = androidGames,
                        onOpenAndroid = onOpenAndroid,
                        onOpenEmulators = { showEmulatorManager = true },
                        onSaveTabOrder = onSaveTabOrder
                    )
                }
            }
        }

        item(key = "appearance") {
            SettingSection(title = "外观") {
                ToggleSettingRow(
                    title = "跟随系统",
                    subtitle = if (themeMode == ThemeMode.SYSTEM) "会跟随系统深色 / 浅色模式自动切换" else "当前使用手动外观设置",
                    checked = themeMode == ThemeMode.SYSTEM,
                    onCheckedChange = { enabled -> onSetThemeMode(if (enabled) ThemeMode.SYSTEM else ThemeMode.LIGHT) }
                )
                Spacer(Modifier.height(8.dp))
                ToggleSettingRow(
                    title = "夜间模式",
                    subtitle = when (themeMode) {
                        ThemeMode.SYSTEM -> "当前由系统外观控制"
                        ThemeMode.DARK -> "当前使用高对比深色 MD3E 界面"
                        ThemeMode.LIGHT -> "当前使用浅色 MD3E 界面"
                    },
                    checked = themeMode == ThemeMode.DARK,
                    onCheckedChange = { enabled -> onSetThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT) }
                )
                Spacer(Modifier.height(8.dp))
                ToggleSettingRow(
                    title = "莫奈主题",
                    subtitle = "跟随系统壁纸动态取色，Android 12 及以上效果更明显。",
                    checked = useDynamicColor,
                    onCheckedChange = onSetDynamicColor
                )
                Spacer(Modifier.height(10.dp))
                Text("横屏方向", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(landscapeMode.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = landscapeMode == LandscapeMode.AUTO, onClick = { onSetLandscapeMode(LandscapeMode.AUTO) }, label = { Text("自动") })
                    FilterChip(selected = landscapeMode == LandscapeMode.LEFT, onClick = { onSetLandscapeMode(LandscapeMode.LEFT) }, label = { Text("横屏 1") })
                    FilterChip(selected = landscapeMode == LandscapeMode.RIGHT, onClick = { onSetLandscapeMode(LandscapeMode.RIGHT) }, label = { Text("横屏 2") })
                }
                Spacer(Modifier.height(12.dp))
                SafeMarginSetting(
                    safeMargins = safeMargins,
                    onSetSafeMargins = onSetSafeMargins
                )
            }
        }

        if (showDeferredSections) {
            item(key = "scraper") {
                ScraperSettingSection(
                    scraperSettings = scraperSettings,
                    onSaveScraperSettings = onSaveScraperSettings
                )
            }

            item(key = "system") {
                SettingSection(title = "系统") {
                    ActionSettingRow(title = "默认桌面", subtitle = "把本 App 设为系统 Home 桌面。", buttonText = "选择", onClick = onOpenHomeSettings)
                    Spacer(Modifier.height(8.dp))
                    ActionSettingRow(title = "手柄操作", subtitle = "设置内置模拟器通用快捷键，支持1~3键组合。", buttonText = "进入", onClick = { showControllerShortcuts = true })
                    Spacer(Modifier.height(8.dp))
                    ActionSettingRow(title = "重新扫描", subtitle = if (isScanning) "正在扫描，请稍等。" else "重新读取已配置平台的 ROM 文件夹。", buttonText = "扫描全部", onClick = onRescanAll)
                }
            }
        }
    }
}


@Composable
private fun ControllerShortcutSettingsScreen(
    onBack: () -> Unit,
    onCaptureHandlerChange: (((AndroidKeyEvent) -> Boolean)?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(ControllerShortcutSettings.load(context)) }
    var captureAction by remember { mutableStateOf<ControllerShortcutAction?>(null) }
    var captureKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var finishCaptureJob by remember { mutableStateOf<Job?>(null) }
    var conflictText by remember { mutableStateOf<String?>(null) }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val shortcutListState = rememberLazyListState()

    fun beginCapture(action: ControllerShortcutAction) {
        finishCaptureJob?.cancel()
        conflictText = null
        captureKeys = emptySet()
        captureAction = action
    }

    fun cancelCapture() {
        finishCaptureJob?.cancel()
        finishCaptureJob = null
        captureAction = null
        captureKeys = emptySet()
        onCaptureHandlerChange(null)
    }

    fun finishCapture() {
        val action = captureAction ?: return
        val keys = ControllerShortcutSettings.normalizeKeys(captureKeys)
        finishCaptureJob?.cancel()
        finishCaptureJob = null
        if (keys.isEmpty()) {
            captureAction = null
            captureKeys = emptySet()
            onCaptureHandlerChange(null)
            return
        }
        val conflict = ControllerShortcutSettings.conflictAction(settings, action, keys)
        if (conflict != null) {
            conflictText = "${ControllerShortcutSettings.comboLabel(keys)} 已经被“${conflict.title}”使用，请换一个按键组合。"
            captureAction = null
            captureKeys = emptySet()
            onCaptureHandlerChange(null)
            return
        }
        settings = ControllerShortcutSettings.saveBinding(context, action, keys)
        Toast.makeText(context, "${action.title} 已改为 ${ControllerShortcutSettings.comboLabel(keys)}", Toast.LENGTH_SHORT).show()
        captureAction = null
        captureKeys = emptySet()
        onCaptureHandlerChange(null)
    }

    fun scheduleFinishCapture() {
        finishCaptureJob?.cancel()
        finishCaptureJob = scope.launch {
            delay(360L)
            finishCapture()
        }
    }

    fun handleControllerScreenKey(nativeEvent: AndroidKeyEvent): Boolean {
        val action = captureAction
        if (action != null) {
            if (nativeEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                val keyCode = nativeEvent.keyCode
                if (nativeEvent.repeatCount == 0 && ControllerShortcutSettings.isCaptureCandidate(keyCode)) {
                    val next = (captureKeys + keyCode).take(3).toSet()
                    captureKeys = next
                    scheduleFinishCapture()
                }
                return true
            }
            if (nativeEvent.action == AndroidKeyEvent.ACTION_UP) return true
            return true
        }

        val keyCode = nativeEvent.keyCode
        if (nativeEvent.action == AndroidKeyEvent.ACTION_UP) {
            return keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                keyCode == AndroidKeyEvent.KEYCODE_BUTTON_B ||
                keyCode == AndroidKeyEvent.KEYCODE_BACK
        }
        if (nativeEvent.action != AndroidKeyEvent.ACTION_DOWN || nativeEvent.repeatCount > 0) return false
        return when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                selectedIndex = (selectedIndex - 1 + ControllerShortcutSettings.EDITABLE_ACTIONS.size) % ControllerShortcutSettings.EDITABLE_ACTIONS.size
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                selectedIndex = (selectedIndex + 1) % ControllerShortcutSettings.EDITABLE_ACTIONS.size
                true
            }
            AndroidKeyEvent.KEYCODE_BUTTON_A,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER -> {
                beginCapture(ControllerShortcutSettings.EDITABLE_ACTIONS[selectedIndex])
                true
            }
            AndroidKeyEvent.KEYCODE_BUTTON_B,
            AndroidKeyEvent.KEYCODE_BACK -> {
                onBack()
                true
            }
            else -> false
        }
    }

    LaunchedEffect(captureAction, captureKeys, settings, selectedIndex) {
        onCaptureHandlerChange(::handleControllerScreenKey)
    }

    LaunchedEffect(selectedIndex) {
        // Header 和标题各占一个 LazyColumn item，所以快捷键行从 index 2 开始。
        shortcutListState.animateScrollToItem((selectedIndex + 2).coerceAtLeast(0))
    }

    DisposableEffect(Unit) {
        onDispose {
            finishCaptureJob?.cancel()
            onCaptureHandlerChange(null)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = shortcutListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "controllerHeader") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("手柄操作", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    TextButton(onClick = onBack) { Text("返回") }
                }
                Text(
                    "设置内置模拟器通用快捷键，支持1~3键组合。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item(key = "controllerRowsTitle") {
                Text(
                    "快捷键",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            ControllerShortcutSettings.EDITABLE_ACTIONS.forEachIndexed { index, action ->
                item(key = "controllerAction_${action.name}") {
                    ActionSettingRow(
                        title = action.title,
                        subtitle = "${action.subtitle} · 当前：${ControllerShortcutSettings.comboLabel(settings.keysFor(action))}",
                        buttonText = "修改",
                        selected = index == selectedIndex,
                        onClick = {
                            selectedIndex = index
                            beginCapture(action)
                        }
                    )
                }
            }

            item(key = "controllerRestore") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            settings = ControllerShortcutSettings.resetToDefault(context)
                            Toast.makeText(context, "已恢复默认手柄快捷键", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("恢复默认") }
                }
            }

            item(key = "controllerTips") {
                OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("说明", fontWeight = FontWeight.Black)
                        Text(
                            "默认：快速保存=L1，快速读取=R1，快进=R3，菜单=L2，退出=SELECT+X，连发A=X，连发B=Y。多键组合会优先于单键，例如 SELECT+X 会优先触发退出，不会触发 X 连发。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val action = captureAction
        if (action != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.62f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("正在修改：${action.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (captureKeys.isEmpty()) "请按下手柄按键" else "已读取：${ControllerShortcutSettings.comboLabel(captureKeys)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("支持同时按 2 个或 3 个按键；停止输入约 360ms 后保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { cancelCapture() }) { Text("取消") }
                }
            }
        }
    }

    val conflict = conflictText
    if (conflict != null) {
        AlertDialog(
            onDismissRequest = { conflictText = null },
            title = { Text("按键冲突") },
            text = { Text(conflict) },
            confirmButton = {
                FilledTonalButton(onClick = { conflictText = null }) { Text("知道了") }
            }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlatformManagerRows(
    tabOrder: List<String>,
    platforms: List<PlatformConfig>,
    installedApps: List<InstalledApp>,
    favorites: Set<String>,
    androidGames: Set<String>,
    onOpenAndroid: () -> Unit,
    onOpenEmulators: () -> Unit,
    onSaveTabOrder: (List<String>) -> Unit
) {
    var sorting by rememberSaveable { mutableStateOf(false) }
    val groups = platformGroupOrder(tabOrder)
    Text(
        if (sorting) "排序模式：使用上移 / 下移调整；完成后顶部导航会跟随变化。" else "单点进入配置，长按任意一项进入排序。",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    groups.forEachIndexed { index, group ->
        val title: String
        val subtitle: String
        val icon: androidx.compose.ui.graphics.vector.ImageVector
        val actionText: String
        val openAction: () -> Unit
        when (group) {
            PlatformGroup.EMULATORS -> {
                val emulators = emulatorTabs(tabOrder)
                title = "模拟器"
                subtitle = if (emulators.isEmpty()) {
                    "未添加模拟器"
                } else {
                    "${emulators.joinToString("、") { it.label }} · ${emulators.size} 个模拟器"
                }
                icon = Icons.Rounded.SportsEsports
                actionText = "进入"
                openAction = onOpenEmulators
            }
            PlatformGroup.ANDROID_APPS -> {
                title = "安卓应用"
                subtitle = "${installedApps.size} 个应用 · 已标记游戏 ${androidGames.count { it.startsWith("app:") }} 个"
                icon = Icons.Rounded.Apps
                actionText = "管理"
                openAction = onOpenAndroid
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = { if (!sorting) openAction() },
                    onLongClick = { sorting = true }
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${index + 1}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sorting) {
                TextButton(
                    onClick = {
                        if (index > 0) {
                            val next = groups.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index - 1, moved)
                            onSaveTabOrder(platformGroupOrderKeys(next, tabOrder))
                        }
                    },
                    enabled = index > 0
                ) { Text("上移") }
                TextButton(
                    onClick = {
                        if (index < groups.lastIndex) {
                            val next = groups.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index + 1, moved)
                            onSaveTabOrder(platformGroupOrderKeys(next, tabOrder))
                        }
                    },
                    enabled = index < groups.lastIndex
                ) { Text("下移") }
            } else {
                FilledTonalButton(onClick = openAction, modifier = Modifier.height(36.dp)) { Text(actionText) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (sorting) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = { sorting = false }, modifier = Modifier.height(34.dp)) { Text("完成排序") }
        }
    }
}

private enum class PlatformGroup { EMULATORS, ANDROID_APPS }

private fun emulatorTabs(tabOrder: List<String>): List<BeaconTab> =
    normalizedTabOrder(tabOrder).filter { it != BeaconTab.ANDROID }

private fun platformGroupOrder(tabOrder: List<String>): List<PlatformGroup> {
    val order = normalizedTabOrder(tabOrder)
    val androidIndex = order.indexOf(BeaconTab.ANDROID)
    val firstEmulatorIndex = order.indexOfFirst { it != BeaconTab.ANDROID }
    return if (androidIndex >= 0 && firstEmulatorIndex >= 0 && androidIndex < firstEmulatorIndex) {
        listOf(PlatformGroup.ANDROID_APPS, PlatformGroup.EMULATORS)
    } else {
        listOf(PlatformGroup.EMULATORS, PlatformGroup.ANDROID_APPS)
    }
}

private fun platformGroupOrderKeys(groups: List<PlatformGroup>, tabOrder: List<String>): List<String> {
    val emulators = emulatorTabs(tabOrder)
    return groups.flatMap { group ->
        when (group) {
            PlatformGroup.EMULATORS -> emulators.map { it.name }
            PlatformGroup.ANDROID_APPS -> listOf(BeaconTab.ANDROID.name)
        }
    }
}

private fun tabOrderKeysWithEmulators(emulators: List<BeaconTab>, tabOrder: List<String>): List<String> {
    val groups = platformGroupOrder(tabOrder)
    return groups.flatMap { group ->
        when (group) {
            PlatformGroup.EMULATORS -> emulators.map { it.name }
            PlatformGroup.ANDROID_APPS -> listOf(BeaconTab.ANDROID.name)
        }
    }
}


private fun platformEmulatorSubtitle(platform: PlatformConfig): String = when {
    InternalEmulators.usesInternalGb(platform) -> "${InternalEmulators.GB_NAME} · ${platform.gameCount} 个游戏"
    InternalEmulators.usesInternalGba(platform) -> "${InternalEmulators.GBA_NAME} · ${platform.gameCount} 个游戏"
    InternalEmulators.usesInternalFc(platform) -> "${InternalEmulators.FC_NAME} · ${platform.gameCount} 个游戏"
    platform.emulatorName.isNullOrBlank() -> "未绑定模拟器 · ${platform.gameCount} 个游戏"
    else -> "${platform.emulatorName} · ${platform.gameCount} 个游戏"
}

private fun platformHasReadyEmulator(platform: PlatformConfig): Boolean =
    InternalEmulators.usesInternal(platform) || !platform.emulatorPackage.isNullOrBlank()

private fun currentEmulatorLabel(platform: PlatformConfig): String = when {
    InternalEmulators.usesInternalGb(platform) -> "当前：${InternalEmulators.GB_NAME}"
    InternalEmulators.usesInternalGba(platform) -> "当前：${InternalEmulators.GBA_NAME}"
    InternalEmulators.usesInternalFc(platform) -> "当前：${InternalEmulators.FC_NAME}"
    platform.emulatorName.isNullOrBlank() -> "当前未绑定模拟器"
    else -> "当前：${platform.emulatorName}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmulatorManagerRows(
    tabOrder: List<String>,
    platforms: List<PlatformConfig>,
    onOpenPlatform: (PlatformConfig) -> Unit,
    onSaveTabOrder: (List<String>) -> Unit
) {
    var sorting by rememberSaveable { mutableStateOf(false) }
    val order = emulatorTabs(tabOrder)
    Text(
        if (sorting) "排序模式：使用上移 / 下移调整模拟器顺序。" else "单点进入模拟器配置，长按任意一项进入排序。",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    order.forEachIndexed { index, tab ->
        val platform = when (tab) {
            BeaconTab.PSP -> platforms.firstOrNull { it.kind == PlatformKind.PSP }
            BeaconTab.NS -> platforms.firstOrNull { it.kind == PlatformKind.SWITCH }
            BeaconTab.GBA -> platforms.firstOrNull { it.kind == PlatformKind.GBA }
            BeaconTab.GB -> platforms.firstOrNull { it.kind == PlatformKind.GB }
            BeaconTab.NES -> platforms.firstOrNull { it.kind == PlatformKind.NES }
            else -> null
        }
        val title = tab.label
        val subtitle = platform?.let { platformEmulatorSubtitle(it) } ?: "平台未创建"
        val icon = if (platform?.let { platformHasReadyEmulator(it) } == true) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen
        val openAction: () -> Unit = { platform?.let(onOpenPlatform) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = { if (!sorting) openAction() },
                    onLongClick = { sorting = true }
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${index + 1}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sorting) {
                TextButton(
                    onClick = {
                        if (index > 0) {
                            val next = order.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index - 1, moved)
                            onSaveTabOrder(tabOrderKeysWithEmulators(next, tabOrder))
                        }
                    },
                    enabled = index > 0
                ) { Text("上移") }
                TextButton(
                    onClick = {
                        if (index < order.lastIndex) {
                            val next = order.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index + 1, moved)
                            onSaveTabOrder(tabOrderKeysWithEmulators(next, tabOrder))
                        }
                    },
                    enabled = index < order.lastIndex
                ) { Text("下移") }
            } else {
                FilledTonalButton(onClick = openAction, modifier = Modifier.height(36.dp)) { Text("配置") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    PlatformPlaceholderRow(
        title = "添加其他",
        subtitle = "占位：后续可添加 SFC / MD / PS1 等模拟器",
        actionText = "待添加",
        icon = Icons.Rounded.AddCircle
    )

    if (sorting) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = { sorting = false }, modifier = Modifier.height(34.dp)) { Text("完成排序") }
        }
    }
}

@Composable
private fun PlatformPlaceholderRow(
    title: String,
    subtitle: String,
    actionText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.height(36.dp)) { Text(actionText) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabOrderEditor(
    tabOrder: List<String>,
    onSaveTabOrder: (List<String>) -> Unit
) {
    var sorting by rememberSaveable { mutableStateOf(false) }
    val order = normalizedTabOrder(tabOrder)

    Text(
        if (sorting) "启动顺序：点击上移 / 下移调整，顶部导航会跟随变化。" else "启动顺序：长按任一项进入排序。",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    order.forEachIndexed { index, tab ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { sorting = true }
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${index + 1}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tab.label, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    when (tab) {
                        BeaconTab.NS -> "NS / Switch 平台入口"
                        BeaconTab.PSP -> "PSP 平台入口"
                        BeaconTab.GBA -> "GBA / 内置模拟器平台入口"
                        BeaconTab.GB -> "GB/GBC / 内置 mGBA 平台入口"
                        BeaconTab.NES -> "FC/NES 内置 / 外部模拟器入口"
                        BeaconTab.ANDROID -> "安卓应用入口"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sorting) {
                TextButton(
                    onClick = {
                        if (index > 0) {
                            val next = order.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index - 1, moved)
                            onSaveTabOrder(tabOrderKeys(next))
                        }
                    },
                    enabled = index > 0
                ) { Text("上移") }
                TextButton(
                    onClick = {
                        if (index < order.lastIndex) {
                            val next = order.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index + 1, moved)
                            onSaveTabOrder(tabOrderKeys(next))
                        }
                    },
                    enabled = index < order.lastIndex
                ) { Text("下移") }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    if (sorting) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = { sorting = false }, modifier = Modifier.height(34.dp)) { Text("完成排序") }
        }
    }
}

@Composable
private fun OrderedPlatformRows(
    tabOrder: List<String>,
    platforms: List<PlatformConfig>,
    installedApps: List<InstalledApp>,
    favorites: Set<String>,
    androidGames: Set<String>,
    onOpenAndroid: () -> Unit,
    onOpenPlatform: (PlatformConfig) -> Unit
) {
    normalizedTabOrder(tabOrder).forEach { tab ->
        when (tab) {
            BeaconTab.ANDROID -> {
                PlatformSettingRow(
                    title = "安卓应用",
                    subtitle = "${installedApps.size} 个应用 · 已标记游戏 ${androidGames.count { it.startsWith("app:") }} 个",
                    actionText = "管理",
                    icon = Icons.Rounded.Apps,
                    onClick = onOpenAndroid
                )
                Spacer(Modifier.height(8.dp))
            }
            BeaconTab.NS -> {
                platforms.firstOrNull { it.kind == PlatformKind.SWITCH }?.let { platform ->
                    PlatformSettingRow(platform = platform, onOpenPlatform = onOpenPlatform)
                    Spacer(Modifier.height(8.dp))
                }
            }
            BeaconTab.PSP -> {
                platforms.firstOrNull { it.kind == PlatformKind.PSP }?.let { platform ->
                    PlatformSettingRow(platform = platform, onOpenPlatform = onOpenPlatform)
                    Spacer(Modifier.height(8.dp))
                }
            }
            BeaconTab.GBA -> {
                platforms.firstOrNull { it.kind == PlatformKind.GBA }?.let { platform ->
                    PlatformSettingRow(platform = platform, onOpenPlatform = onOpenPlatform)
                    Spacer(Modifier.height(8.dp))
                }
            }
            BeaconTab.GB -> {
                platforms.firstOrNull { it.kind == PlatformKind.GB }?.let { platform ->
                    PlatformSettingRow(platform = platform, onOpenPlatform = onOpenPlatform)
                    Spacer(Modifier.height(8.dp))
                }
            }
            BeaconTab.NES -> {
                platforms.firstOrNull { it.kind == PlatformKind.NES }?.let { platform ->
                    PlatformSettingRow(platform = platform, onOpenPlatform = onOpenPlatform)
                    Spacer(Modifier.height(8.dp))
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PlatformSettingRow(platform: PlatformConfig, onOpenPlatform: (PlatformConfig) -> Unit) {
    PlatformSettingRow(
        title = platformDisplayName(platform.kind.title),
        subtitle = platformEmulatorSubtitle(platform),
        actionText = "配置",
        icon = if (platformHasReadyEmulator(platform)) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
        onClick = { onOpenPlatform(platform) }
    )
}

@Composable
private fun PlatformSettingRow(
    title: String,
    subtitle: String,
    actionText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onClick, modifier = Modifier.height(36.dp)) { Text(actionText) }
    }
}

@Composable
private fun SafeMarginSetting(
    safeMargins: SafeMarginSettings,
    onSetSafeMargins: (SafeMarginSettings) -> Unit
) {
    fun setLeft(value: Int) {
        onSetSafeMargins(safeMargins.copy(leftDp = value.coerceIn(SafeMarginSettings.MIN_DP, SafeMarginSettings.MAX_DP)))
    }

    fun setRight(value: Int) {
        onSetSafeMargins(safeMargins.copy(rightDp = value.coerceIn(SafeMarginSettings.MIN_DP, SafeMarginSettings.MAX_DP)))
    }

    Text("左右安全边距", fontWeight = FontWeight.Black)
    Spacer(Modifier.height(4.dp))
    Text(
        "用于避开挖孔屏、圆角或刘海遮挡。默认左右各预留 ${SafeMarginSettings.DEFAULT_DP}dp，可按设备单独微调。",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    SafeMarginAdjustRow(label = "左边距", value = safeMargins.leftDp, onChange = ::setLeft)
    Spacer(Modifier.height(6.dp))
    SafeMarginAdjustRow(label = "右边距", value = safeMargins.rightDp, onChange = ::setRight)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = safeMargins.leftDp == SafeMarginSettings.DEFAULT_DP && safeMargins.rightDp == SafeMarginSettings.DEFAULT_DP,
            onClick = { onSetSafeMargins(SafeMarginSettings()) },
            label = { Text("默认预留") }
        )
        FilterChip(
            selected = safeMargins.leftDp == 0 && safeMargins.rightDp == 0,
            onClick = { onSetSafeMargins(SafeMarginSettings(leftDp = 0, rightDp = 0)) },
            label = { Text("无边距") }
        )
        FilterChip(
            selected = safeMargins.leftDp == 40 && safeMargins.rightDp == 40,
            onClick = { onSetSafeMargins(SafeMarginSettings(leftDp = 40, rightDp = 40)) },
            label = { Text("大挖孔") }
        )
    }
}

@Composable
private fun SafeMarginAdjustRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange(value - 4) }, enabled = value > SafeMarginSettings.MIN_DP) { Text("−") }
        Text("${value}dp", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { onChange(value + 4) }, enabled = value < SafeMarginSettings.MAX_DP) { Text("+") }
    }
}

@Composable
private fun ToggleSettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSettingRow(
    title: String,
    subtitle: String,
    buttonText: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onClick, modifier = Modifier.height(36.dp)) { Text(buttonText) }
    }
}

@Composable
private fun ScraperSettingSection(
    scraperSettings: ScraperSettings,
    onSaveScraperSettings: (ScraperSettings) -> Unit
) {
    var useLibretro by rememberSaveable(scraperSettings.useLibretro) { mutableStateOf(scraperSettings.useLibretro) }
    var steamGridKey by rememberSaveable(scraperSettings.steamGridDbApiKey) { mutableStateOf(scraperSettings.steamGridDbApiKey) }
    var theGamesDbKey by rememberSaveable(scraperSettings.theGamesDbApiKey) { mutableStateOf(scraperSettings.theGamesDbApiKey) }
    var screenUser by rememberSaveable(scraperSettings.screenScraperUser) { mutableStateOf(scraperSettings.screenScraperUser) }
    var screenPass by rememberSaveable(scraperSettings.screenScraperPassword) { mutableStateOf(scraperSettings.screenScraperPassword) }

    SettingSection(title = "封面刮削") {
        ToggleSettingRow(
            title = "Libretro 缩略图",
            subtitle = "免费免 Key，适合 PSP 等复古平台；命名匹配时可直接下载封面。",
            checked = useLibretro,
            onCheckedChange = { useLibretro = it }
        )
        Spacer(Modifier.height(8.dp))
        ScraperTextField(
            label = "SteamGridDB API Key",
            value = steamGridKey,
            onValueChange = { steamGridKey = it },
            placeholder = "不填写就跳过 SteamGridDB"
        )
        Spacer(Modifier.height(8.dp))
        ScraperTextField(
            label = "TheGamesDB API Key",
            value = theGamesDbKey,
            onValueChange = { theGamesDbKey = it },
            placeholder = "不填写就跳过 TheGamesDB"
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScraperTextField(
                label = "ScreenScraper 账号",
                value = screenUser,
                onValueChange = { screenUser = it },
                placeholder = "预留",
                modifier = Modifier.weight(1f)
            )
            ScraperTextField(
                label = "ScreenScraper 密码",
                value = screenPass,
                onValueChange = { screenPass = it },
                placeholder = "预留",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "未填写 Key / 账号的来源会自动跳过；长按游戏进入编辑后，可点“联网搜索封面”。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                onClick = {
                    onSaveScraperSettings(
                        ScraperSettings(
                            useLibretro = useLibretro,
                            theGamesDbApiKey = theGamesDbKey,
                            steamGridDbApiKey = steamGridKey,
                            screenScraperUser = screenUser,
                            screenScraperPassword = screenPass
                        )
                    )
                }
            ) { Text("保存刮削设置") }
        }
    }
}

@Composable
private fun ScraperTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable(label) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editing) onValueChange(it) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { canFocus = editing },
            singleLine = true,
            readOnly = !editing,
            label = { Text(label) },
            placeholder = { Text(if (editing) placeholder else "点右侧“编辑”后输入") },
            shape = RoundedCornerShape(18.dp)
        )
        FilledTonalButton(
            onClick = {
                if (editing) {
                    editing = false
                    focusManager.clearFocus(force = true)
                } else {
                    editing = true
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Text(if (editing) "保存" else "编辑")
        }
    }
}

@Composable
internal fun PlatformSetupScreen(
    platform: PlatformConfig,
    emulatorApp: InstalledApp?,
    isScanning: Boolean,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onPickEmulator: () -> Unit,
    onClearEmulator: () -> Unit,
    onUseInternalEmulator: () -> Unit,
    onScan: () -> Unit
) {
    val hasFolder = !platform.folderUri.isNullOrBlank()
    val usesInternalGba = InternalEmulators.usesInternalGba(platform)
    val usesInternalGb = InternalEmulators.usesInternalGb(platform)
    val usesInternalFc = InternalEmulators.usesInternalFc(platform)
    val usesInternal = usesInternalGba || usesInternalGb || usesInternalFc
    val hasExternalEmulator = !platform.emulatorPackage.isNullOrBlank() && !usesInternal
    val displayApp = emulatorApp ?: InstalledApp(
        label = platform.emulatorName ?: "已选择的模拟器",
        packageName = platform.emulatorPackage.orEmpty()
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${platformDisplayName(platform.kind.title)} 平台设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("返回") }
        }

        ElevatedCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformConfigRow(
                    title = "ROM 文件夹",
                    subtitle = if (hasFolder) "已选择 ROM 文件夹 · 支持 ${platform.kind.extensions.joinToString { it.uppercase() }}" else "还没有选择 ROM 文件夹",
                    actionText = if (hasFolder) "更换" else "选择",
                    onClick = onPickFolder
                )
                if (platform.kind == PlatformKind.GBA || platform.kind == PlatformKind.GB || platform.kind == PlatformKind.NES) {
                    val internalTitle = when (platform.kind) {
                        PlatformKind.GB -> "内置 GB/GBC 模拟器"
                        PlatformKind.NES -> "内置 FC/NES 模拟器"
                        else -> "内置 GBA 模拟器"
                    }
                    val internalSubtitle = when (platform.kind) {
                        PlatformKind.GB -> "复用 mGBA libretro core，支持 .gb / .gbc 和普通 .zip 内 ROM。"
                        PlatformKind.NES -> "基于 Nestopia libretro core，支持 .nes 和普通 .zip 内 ROM。"
                        else -> "默认启动方式，不需要安装外部模拟器。"
                    }
                    PlatformConfigRow(
                        title = internalTitle,
                        subtitle = internalSubtitle,
                        actionText = if (usesInternal) "使用中" else "使用",
                        enabled = !usesInternal,
                        onClick = onUseInternalEmulator
                    )
                }
                PlatformConfigRow(
                    title = "外部模拟器 App",
                    subtitle = if (hasExternalEmulator) "${displayApp.label} · ${displayApp.packageName}" else externalEmulatorHelpText(platform.kind),
                    actionText = if (hasExternalEmulator) "更换" else "选择",
                    leading = if (hasExternalEmulator) { { AppIcon(app = displayApp, size = 34) } } else null,
                    onClick = onPickEmulator
                )
                if (hasExternalEmulator) {
                    PlatformConfigRow(
                        title = "清除外部模拟器",
                        subtitle = if (platform.kind == PlatformKind.GBA || platform.kind == PlatformKind.GB || platform.kind == PlatformKind.NES) "清除后会回到内置模拟器。" else "只清除绑定关系，不会删除模拟器 App。",
                        actionText = "清除",
                        onClick = onClearEmulator
                    )
                }
                PlatformConfigRow(
                    title = "扫描游戏",
                    subtitle = "${platform.gameCount} 个游戏 · ${if (hasFolder) "读取当前 ROM 文件夹" else "请先选择 ROM 文件夹"}",
                    actionText = if (isScanning) "扫描中" else "扫描",
                    enabled = hasFolder && !isScanning,
                    onClick = onScan
                )
            }
        }

        OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("提示", fontWeight = FontWeight.Black)
                Text(
                    "GBA、GB/GBC 和 FC/NES 默认使用内置模拟器；PSP/FC/NES/GBA/GB/GBC 也可以改用外部模拟器。长按游戏可以自定义显示名称和图标。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlatformConfigRow(
    title: String,
    subtitle: String,
    actionText: String,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(36.dp)) { Text(actionText, maxLines = 1) }
    }
}

@Composable
internal fun SearchDialog(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.56f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("输入名称 / 包名") },
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear) { Text("清空") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

@Composable
internal fun AppPickerPage(platform: PlatformConfig, apps: List<InstalledApp>, onBack: () -> Unit, onSelect: (InstalledApp) -> Unit) {
    var query by rememberSaveable(platform.id) { mutableStateOf("") }
    var showAll by rememberSaveable(platform.id) { mutableStateOf(false) }
    var showSearch by rememberSaveable(platform.id) { mutableStateOf(false) }

    val recommendedApps = remember(apps, platform.id) { apps.filter { app -> isRecommendedEmulatorForPlatform(platform, app) } }
    val pickerApps = remember(apps, recommendedApps, query, showAll, platform.id) {
        val base = if (query.isBlank() && !showAll) recommendedApps else apps
        base.filter { app -> query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true) }
            .sortedWith(
                compareByDescending<InstalledApp> { isRecommendedEmulatorForPlatform(platform, it) }
                    .thenByDescending { it.isLikelyEmulator }
                    .thenBy { it.label.lowercase() }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "选择 ${platformDisplayName(platform.kind.title)} 模拟器",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    currentEmulatorLabel(platform),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "搜索模拟器")
            }
            FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("推荐") })
            FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("全部") })
            TextButton(onClick = onBack) { Text("返回") }
        }

        if (showSearch) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(emulatorSearchHint(platform)) },
                shape = RoundedCornerShape(20.dp),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (pickerApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到推荐模拟器，可以点“全部”或搜索包名。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lazyItems(pickerApps, key = { it.packageName }) { app ->
                    PickerAppCard(
                        app = app,
                        recommended = isRecommendedEmulatorForPlatform(platform, app),
                        onClick = { onSelect(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerAppCard(app: InstalledApp, recommended: Boolean, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().height(84.dp), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app = app, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (recommended) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("推荐") })
            }
        }
    }
}

private fun emulatorSearchHint(platform: PlatformConfig): String = when (platform.kind) {
    PlatformKind.PSP -> "搜索 PPSSPP / Rocket PSP / MYPSP / RetroArch / 包名"
    PlatformKind.SWITCH -> "搜索 Yuzu / Suyu / Citron / 包名"
    PlatformKind.GBA -> "搜索 My Boy / Pizza Boy / John GBA / GBA.emu / RetroArch / 包名"
    PlatformKind.GB -> "搜索 My OldBoy / Pizza Boy C / GBC.emu / RetroArch / 包名"
    PlatformKind.NES -> "搜索 Nes.emu / Nostalgia.NES / RetroArch / 包名"
}

private fun externalEmulatorHelpText(kind: PlatformKind): String = when (kind) {
    PlatformKind.GBA -> "可选：改用 My Boy / Pizza Boy / John GBA / RetroArch"
    PlatformKind.GB -> "可选：改用 My OldBoy / Pizza Boy C / GBC.emu / RetroArch"
    PlatformKind.PSP -> "请选择 PPSSPP / PPSSPP Gold / RetroArch 等 PSP 模拟器"
    PlatformKind.NES -> "可选：改用 Nes.emu / Nostalgia.NES / RetroArch 等 FC/NES 外部模拟器"
    PlatformKind.SWITCH -> "请选择 Switch 外部模拟器；本项目不包含 keys / firmware"
}

private fun isRecommendedEmulatorForPlatform(platform: PlatformConfig, app: InstalledApp): Boolean {
    val text = (app.label + " " + app.packageName).lowercase()
    if (looksLikeRomDownloader(text)) return false
    val platformKeywords = when (platform.kind) {
        PlatformKind.PSP -> listOf(
            "ppsspp",
            "org.ppsspp.ppsspp",
            "org.ppsspp.ppssppgold",
            "rocket psp",
            "rocketpsp",
            "com.emultech.rocketpsp",
            "enjoy psp",
            "enjoypsp",
            "com.emultech.enjoypsp",
            "mypsp",
            "kr.co.iefriends.mypsp",
            "retroarch",
            "com.retroarch"
        )
        PlatformKind.SWITCH -> listOf("yuzu", "suyu", "sudachi", "skyline", "eden", "strato", "uzuy", "citron", "egg ns", "eggns")
        PlatformKind.GBA -> listOf(
            "my boy",
            "myboy",
            "gba",
            "fastemulator",
            "com.fastemulator.gba",
            "pizza boy",
            "pizzaboy",
            "it.dbtecno.pizzaboygba",
            "john gba",
            "johngba",
            "johnemulators",
            "com.johnemulators.johngba",
            "com.johnemulators.johngbalite",
            "gba.emu",
            "gbaemu",
            "com.explusalpha.gbaemu",
            "nostalgia.gba",
            "nostalgiaemulators",
            "mgba",
            "retroarch",
            "com.retroarch"
        )
        PlatformKind.GB -> listOf(
            "my oldboy",
            "myoldboy",
            "oldboy",
            "gbc",
            "game boy",
            "gameboy",
            "pizza boy c",
            "pizzaboyc",
            "gbc.emu",
            "gbcemu",
            "com.explusalpha.gbcemu",
            "gambatte",
            "sameboy",
            "mgba",
            "retroarch",
            "com.retroarch"
        )
        PlatformKind.NES -> FcExternalEmulatorProfiles.recommendedKeywords
    }
    return platformKeywords.any { it in text }
}

private fun looksLikeRomDownloader(text: String): Boolean = listOf(
    "downloader",
    "download psp",
    "download gba",
    "rom downloader",
    "roms downloader",
    "games downloader",
    "iso games",
    "psp games download",
    "gba games download"
).any { it in text }
