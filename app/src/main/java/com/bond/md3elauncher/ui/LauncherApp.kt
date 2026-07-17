package com.bond.md3elauncher.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.data.ItemOverride
import com.bond.md3elauncher.data.LandscapeMode
import com.bond.md3elauncher.data.LauncherLayoutMode
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.data.SafeMarginSettings
import com.bond.md3elauncher.data.ThemeMode
import com.bond.md3elauncher.i18n.I18n

@Composable
fun LauncherApp(
    platforms: List<PlatformConfig>,
    games: List<GameItem>,
    favorites: Set<String>,
    recentIds: List<String>,
    installedApps: List<InstalledApp>,
    itemOverrides: Map<String, ItemOverride>,
    androidGames: Set<String>,
    landscapeMode: LandscapeMode,
    launcherLayoutMode: LauncherLayoutMode,
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    safeMargins: SafeMarginSettings,
    scraperSettings: ScraperSettings,
    tabOrder: List<String>,
    itemOrders: Map<String, List<String>>,
    languageMode: String,
    isScanning: Boolean,
    showHomePrompt: Boolean,
    onPickFolder: (PlatformConfig) -> Unit,
    onSelectEmulator: (PlatformConfig, InstalledApp) -> Unit,
    onClearEmulator: (PlatformConfig) -> Unit,
    onUseInternalEmulator: (PlatformConfig) -> Unit,
    onScanPlatform: (PlatformConfig) -> Unit,
    onRescanAll: () -> Unit,
    onLaunchGame: (GameItem) -> Unit,
    onToggleFavorite: (GameItem) -> Unit,
    onToggleAndroidFavorite: (InstalledApp) -> Unit,
    onToggleAndroidGame: (InstalledApp) -> Unit,
    onSaveItemOverride: (
        key: String,
        title: String,
        previewImageUriString: String?,
        gridImageUriString: String?
    ) -> Unit,
    onLaunchAndroidApp: (InstalledApp) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onSetLandscapeMode: (LandscapeMode) -> Unit,
    onSetLauncherLayoutMode: (LauncherLayoutMode) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetSafeMargins: (SafeMarginSettings) -> Unit,
    onSaveScraperSettings: (ScraperSettings) -> Unit,
    onSaveTabOrder: (List<String>) -> Unit,
    onSaveItemOrder: (scope: String, order: List<String>) -> Unit,
    onSetLanguageMode: (String) -> Unit,
    isDefaultHome: Boolean,
    onExitApp: () -> Unit,
    onDismissHomePrompt: () -> Unit
) {
    val context = LocalContext.current
    val lang = I18n.languageFor(context)
    val favoriteText = I18n.t(context, "launcher.bottom.favorite", "收藏")
    val unfavoriteText = I18n.t(context, "launcher.bottom.unfavorite", "取消收藏")
    val addText = I18n.t(context, "launcher.bottom.add", "添加")
    val removeAddText = I18n.t(context, "launcher.bottom.remove_add", "取消添加")
    val backText = I18n.t(context, "common.back", "返回")

    var tabName by rememberSaveable { mutableStateOf(BeaconTab.NOW.name) }
    val tab = BeaconTab.valueOf(tabName)
    var setupPlatformId by rememberSaveable { mutableStateOf<String?>(null) }
    val setupPlatform = platforms.firstOrNull { it.id == setupPlatformId }
    var appPickerPlatform by remember { mutableStateOf<PlatformConfig?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var launchSelected by remember { mutableStateOf<(() -> Unit)?>(null) }
    var bottomBSelected by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editSelected by remember { mutableStateOf<(() -> Unit)?>(null) }
    var bottomBLabel by rememberSaveable { mutableStateOf(favoriteText) }
    var moveSelectionUp by remember { mutableStateOf<(() -> Unit)?>(null) }
    var moveSelectionDown by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var editCenterText by remember { mutableStateOf("") }
    var controllerShortcutCaptureHandler by remember { mutableStateOf<((AndroidKeyEvent) -> Boolean)?>(null) }
    val visibleTabs = remember(games, tabOrder, tab) {
        val orderedMiddle = normalizedTabOrder(tabOrder).filter { candidate ->
            when (candidate) {
                BeaconTab.NS -> games.any { it.platformId == PlatformKind.SWITCH.name } || tab == BeaconTab.NS
                BeaconTab.PSP -> games.any { it.platformId == PlatformKind.PSP.name } || tab == BeaconTab.PSP
                BeaconTab.GBA -> games.any { it.platformId == PlatformKind.GBA.name } || tab == BeaconTab.GBA
                BeaconTab.GB -> games.any { it.platformId == PlatformKind.GB.name } || tab == BeaconTab.GB
                BeaconTab.SFC -> games.any { it.platformId == PlatformKind.SFC.name } || tab == BeaconTab.SFC
                BeaconTab.NES -> games.any { it.platformId == PlatformKind.NES.name } || tab == BeaconTab.NES
                BeaconTab.ANDROID -> true
                else -> false
            }
        }
        (listOf(BeaconTab.NOW) + orderedMiddle).distinct()
    }

    val gamepadScope = rememberCoroutineScope()
    val currentLaunchSelected by rememberUpdatedState(launchSelected)
    val currentEditSelected by rememberUpdatedState(editSelected)
    var aLongPressJob by remember { mutableStateOf<Job?>(null) }
    var aLongPressFired by remember { mutableStateOf(false) }

    fun cancelPrimaryPress() {
        aLongPressJob?.cancel()
        aLongPressJob = null
        aLongPressFired = false
    }

    fun isPrimaryKey(key: Key): Boolean = key == Key.ButtonA ||
        key == Key.A ||
        key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter

    fun isShortcutKey(key: Key): Boolean = key == Key.ButtonL1 ||
        key == Key.ButtonR1 ||
        key == Key.ButtonY ||
        key == Key.Y ||
        key == Key.ButtonX ||
        key == Key.X ||
        key == Key.ButtonB ||
        key == Key.B

    fun isMoveOrderKey(nativeEvent: AndroidKeyEvent): Boolean =
        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_THUMBL ||
            nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_THUMBR

    fun startPrimaryPress(): Boolean {
        if (aLongPressJob == null && !aLongPressFired) {
            aLongPressJob = gamepadScope.launch {
                delay(ViewConfiguration.getLongPressTimeout().toLong())
                val edit = currentEditSelected
                if (edit != null) {
                    aLongPressFired = true
                    aLongPressJob = null
                    edit()
                }
            }
        }
        return currentLaunchSelected != null || currentEditSelected != null
    }

    fun finishPrimaryPress(): Boolean {
        val fired = aLongPressFired
        val hadAction = currentLaunchSelected != null || currentEditSelected != null
        aLongPressJob?.cancel()
        aLongPressJob = null
        aLongPressFired = false
        if (!fired) {
            currentLaunchSelected?.invoke()
        }
        return hadAction
    }

    fun estimatedBottomBLabel(next: BeaconTab): String {
        return when (next) {
            BeaconTab.NOW -> {
                val hasFavoriteGame = games.any { it.id in favorites }
                val hasFavoriteApp = installedApps.any { "app:${it.packageName}" in favorites }
                if (hasFavoriteGame || hasFavoriteApp) unfavoriteText else favoriteText
            }
            BeaconTab.PSP -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.PSP.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.NS -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.SWITCH.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.GBA -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.GBA.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.GB -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.GB.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.SFC -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.SFC.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.NES -> {
                val first = games.firstOrNull { it.platformId == PlatformKind.NES.name }
                if (first != null && first.id in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.ANDROID -> {
                val first = installedApps.firstOrNull { "app:${it.packageName}" in androidGames }
                val key = first?.let { "app:${it.packageName}" }
                if (key != null && key in favorites) unfavoriteText else favoriteText
            }
            BeaconTab.SETTINGS -> favoriteText
        }
    }

    fun estimatedAndroidAddLabel(): String {
        val first = installedApps.firstOrNull()
        val key = first?.let { "app:${it.packageName}" }
        return if (key != null && key in androidGames) removeAddText else addText
    }

    fun selectTab(next: BeaconTab) {
        appPickerPlatform = null
        showAllApps = false
        setupPlatformId = null
        searchQuery = ""
        showSearchDialog = false
        launchSelected = null
        bottomBSelected = null
        editSelected = null
        cancelPrimaryPress()
        moveSelectionUp = null
        moveSelectionDown = null
        bottomBLabel = estimatedBottomBLabel(next)
        editTarget = null
        tabName = next.name
    }

    fun goBackOneStep() {
        when {
            editTarget != null -> editTarget = null
            showSearchDialog -> showSearchDialog = false
            appPickerPlatform != null -> appPickerPlatform = null
            setupPlatformId != null -> setupPlatformId = null
            showAllApps -> {
                showAllApps = false
                bottomBSelected = null
                moveSelectionUp = null
                moveSelectionDown = null
                bottomBLabel = estimatedBottomBLabel(tab)
            }
            tab != BeaconTab.NOW -> selectTab(BeaconTab.NOW)
            !isDefaultHome -> onExitApp()
            else -> Unit
        }
    }

    BackHandler(enabled = true) { goBackOneStep() }

    val colors = MaterialTheme.colorScheme

    LaunchedEffect(setupPlatformId) {
        if (setupPlatformId != null) {
            launchSelected = null
            bottomBSelected = null
            editSelected = null
            cancelPrimaryPress()
            moveSelectionUp = null
            moveSelectionDown = null
        }
    }

    val isBackPage = appPickerPlatform != null || editTarget != null || setupPlatform != null || (tab == BeaconTab.SETTINGS && !showAllApps)
    val bottomBAction: (() -> Unit)? = if (isBackPage) {
        { goBackOneStep() }
    } else {
        { bottomBSelected?.invoke() }
    }
    val bottomBDisplayLabel = if (isBackPage) backText else bottomBLabel
    val bottomSearchAction: (() -> Unit)? = if (isBackPage) null else ({ showSearchDialog = true })

    fun setMoveSelectionActions(up: (() -> Unit)?, down: (() -> Unit)?) {
        moveSelectionUp = up
        moveSelectionDown = down
    }



    LaunchedEffect(lang, tab, favorites, androidGames, games, installedApps, showAllApps, isBackPage) {
        if (!isBackPage) {
            bottomBLabel = if (showAllApps && tab == BeaconTab.ANDROID) {
                estimatedAndroidAddLabel()
            } else {
                estimatedBottomBLabel(tab)
            }
        }
    }

    LaunchedEffect(isBackPage, showAllApps, tab) {
        if (isBackPage || (tab == BeaconTab.SETTINGS && !showAllApps)) {
            moveSelectionUp = null
            moveSelectionDown = null
            if (isBackPage) {
                editSelected = null
                cancelPrimaryPress()
            }
        }
    }

    fun handleGamepadShortcut(key: Key): Boolean {
        return when (key) {
            Key.ButtonL1 -> {
                selectTab(previousTab(if (tab in visibleTabs) tab else BeaconTab.NOW, visibleTabs))
                true
            }
            Key.ButtonR1 -> {
                selectTab(nextTab(if (tab in visibleTabs) tab else BeaconTab.NOW, visibleTabs))
                true
            }
            Key.ButtonY, Key.Y -> {
                selectTab(BeaconTab.SETTINGS)
                true
            }
            Key.ButtonX, Key.X -> {
                bottomSearchAction?.invoke()
                bottomSearchAction != null
            }
            Key.ButtonB, Key.B -> {
                bottomBAction?.invoke()
                bottomBAction != null
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .onPreviewKeyEvent { event ->
                controllerShortcutCaptureHandler?.let { handler ->
                    return@onPreviewKeyEvent handler(event.nativeKeyEvent)
                }
                if (showSearchDialog) return@onPreviewKeyEvent false
                val selectionPage = !isBackPage
                when (event.type) {
                    KeyEventType.KeyDown -> when {
                        selectionPage && isMoveOrderKey(event.nativeKeyEvent) -> true
                        selectionPage && isPrimaryKey(event.key) -> startPrimaryPress()
                        isShortcutKey(event.key) -> true
                        else -> false
                    }
                    KeyEventType.KeyUp -> when {
                        selectionPage && event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_THUMBL -> {
                            cancelPrimaryPress()
                            moveSelectionUp?.invoke()
                            moveSelectionUp != null
                        }
                        selectionPage && event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_THUMBR -> {
                            cancelPrimaryPress()
                            moveSelectionDown?.invoke()
                            moveSelectionDown != null
                        }
                        selectionPage && isPrimaryKey(event.key) -> finishPrimaryPress()
                        event.key == Key.ButtonB || event.key == Key.B -> {
                            cancelPrimaryPress()
                            bottomBAction?.invoke()
                            bottomBAction != null
                        }
                        selectionPage -> handleGamepadShortcut(event.key)
                        else -> false
                    }
                    else -> false
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = safeMargins.leftDp.dp, end = safeMargins.rightDp.dp),
            color = colors.surface,
            shape = RoundedCornerShape(0.dp),
            tonalElevation = 0.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                BeaconTopBar(
                    tabs = visibleTabs,
                    selected = tab,
                    onSelect = { selectTab(it) },
                    onPrevious = { selectTab(previousTab(if (tab in visibleTabs) tab else BeaconTab.NOW, visibleTabs)) },
                    onNext = { selectTab(nextTab(if (tab in visibleTabs) tab else BeaconTab.NOW, visibleTabs)) }
                )

                Box(Modifier.weight(1f)) {
                    val pickerPlatform = appPickerPlatform
                    if (pickerPlatform != null) {
                        AppPickerPage(
                            platform = pickerPlatform,
                            apps = installedApps,
                            onBack = { appPickerPlatform = null },
                            onSelect = { app ->
                                onSelectEmulator(pickerPlatform, app)
                                appPickerPlatform = null
                            }
                        )
                    } else if (editTarget != null) {
                        EditItemPage(
                            target = editTarget!!,
                            scraperSettings = scraperSettings,
                            onBack = {
                                editCenterText = ""
                                editTarget = null
                            },
                            onFooterTextChange = { editCenterText = it },
                            onSave = { title, previewImageUriString, gridImageUriString ->
                                onSaveItemOverride(
                                    editTarget!!.key,
                                    title,
                                    previewImageUriString,
                                    gridImageUriString
                                )
                                editCenterText = ""
                                editTarget = null
                            }
                        )
                    } else if (setupPlatform != null) {
                        PlatformSetupScreen(
                            platform = setupPlatform,
                            emulatorApp = installedApps.firstOrNull { it.packageName == setupPlatform.emulatorPackage },
                            isScanning = isScanning,
                            onBack = { setupPlatformId = null },
                            onPickFolder = { onPickFolder(setupPlatform) },
                            onPickEmulator = { appPickerPlatform = setupPlatform },
                            onClearEmulator = { onClearEmulator(setupPlatform) },
                            onUseInternalEmulator = { onUseInternalEmulator(setupPlatform) },
                            onScan = { onScanPlatform(setupPlatform) }
                        )
                    } else if (showAllApps) {
                        AndroidBeaconScreen(
                            apps = installedApps,
                            favorites = favorites,
                            taggedApps = androidGames,
                            itemOverrides = itemOverrides,
                            query = searchQuery,
                            layoutMode = launcherLayoutMode,
                            itemOrder = itemOrders["android_all"].orEmpty(),
                            onSaveItemOrder = { order -> onSaveItemOrder("android_all", order) },
                            onLaunchSelectedChange = { launchSelected = it },
                            onToggleSelectedChange = { bottomBSelected = it },
                            onEditSelectedChange = { editSelected = it },
                            onBottomBLabelChange = { bottomBLabel = it },
                            onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                            onEdit = { editTarget = it },
                            onLaunchAndroidApp = onLaunchAndroidApp,
                            onToggleAndroidFavorite = onToggleAndroidFavorite,
                            onToggleAndroidTag = onToggleAndroidGame,
                            onlyTagged = false
                        )
                    } else {
                        when (tab) {
                            BeaconTab.NOW -> FavoritesBeaconScreen(
                                games = games,
                                favorites = favorites,
                                installedApps = installedApps,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["favorites"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("favorites", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite,
                                onLaunchAndroidApp = onLaunchAndroidApp,
                                onToggleAndroidFavorite = onToggleAndroidFavorite
                            )

                            BeaconTab.NS -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.SWITCH },
                                games = games.filter { it.platformId == PlatformKind.SWITCH.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.SWITCH.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.SWITCH.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )

                            BeaconTab.PSP -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.PSP },
                                games = games.filter { it.platformId == PlatformKind.PSP.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.PSP.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.PSP.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )

                            BeaconTab.GBA -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.GBA },
                                games = games.filter { it.platformId == PlatformKind.GBA.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.GBA.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.GBA.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )

                            BeaconTab.GB -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.GB },
                                games = games.filter { it.platformId == PlatformKind.GB.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.GB.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.GB.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )



                            BeaconTab.SFC -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.SFC },
                                games = games.filter { it.platformId == PlatformKind.SFC.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.SFC.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.SFC.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )

                            BeaconTab.NES -> PlatformBeaconScreen(
                                platform = platforms.firstOrNull { it.kind == PlatformKind.NES },
                                games = games.filter { it.platformId == PlatformKind.NES.name },
                                favorites = favorites,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["platform:${PlatformKind.NES.name}"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("platform:${PlatformKind.NES.name}", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onOpenPlatform = { setupPlatformId = it.id },
                                onLaunchGame = onLaunchGame,
                                onToggleFavorite = onToggleFavorite
                            )

                            BeaconTab.ANDROID -> AndroidBeaconScreen(
                                apps = installedApps.filter { "app:${it.packageName}" in androidGames },
                                favorites = favorites,
                                taggedApps = androidGames,
                                itemOverrides = itemOverrides,
                                query = searchQuery,
                                layoutMode = launcherLayoutMode,
                                itemOrder = itemOrders["android_games"].orEmpty(),
                                onSaveItemOrder = { order -> onSaveItemOrder("android_games", order) },
                                onLaunchSelectedChange = { launchSelected = it },
                                onToggleSelectedChange = { bottomBSelected = it },
                                onEditSelectedChange = { editSelected = it },
                                onBottomBLabelChange = { bottomBLabel = it },
                                onMoveSelectionActionsChange = { up, down -> setMoveSelectionActions(up, down) },
                                onEdit = { editTarget = it },
                                onLaunchAndroidApp = onLaunchAndroidApp,
                                onToggleAndroidFavorite = onToggleAndroidFavorite,
                                onToggleAndroidTag = onToggleAndroidGame,
                                onlyTagged = true
                            )

                            BeaconTab.SETTINGS -> SettingsBeaconScreen(
                                platforms = platforms,
                                installedApps = installedApps,
                                favorites = favorites,
                                androidGames = androidGames,
                                landscapeMode = landscapeMode,
                                themeMode = themeMode,
                                useDynamicColor = useDynamicColor,
                                safeMargins = safeMargins,
                                scraperSettings = scraperSettings,
                                tabOrder = tabOrder,
                                languageMode = languageMode,
                                isScanning = isScanning,
                                onOpenPlatform = { setupPlatformId = it.id },
                                onOpenAndroid = {
                                    appPickerPlatform = null
                                    showAllApps = true
                                    searchQuery = ""
                                    showSearchDialog = false
                                    launchSelected = null
                                    bottomBSelected = null
                                    editSelected = null
                                    cancelPrimaryPress()
                                    moveSelectionUp = null
                                    moveSelectionDown = null
                                    bottomBLabel = estimatedAndroidAddLabel()
                                    editTarget = null
                                },
                                onOpenHomeSettings = onOpenHomeSettings,
                                onRescanAll = onRescanAll,
                                onSetLandscapeMode = onSetLandscapeMode,
                                onSetThemeMode = onSetThemeMode,
                                onSetDynamicColor = onSetDynamicColor,
                                onSetSafeMargins = onSetSafeMargins,
                                onSaveScraperSettings = onSaveScraperSettings,
                                onSaveTabOrder = onSaveTabOrder,
                                onSetLanguageMode = onSetLanguageMode,
                                onLaunchSelectedChange = { launchSelected = it },
                                onControllerShortcutCaptureHandlerChange = { controllerShortcutCaptureHandler = it }
                            )
                        }
                    }

                    if (isScanning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                        )
                    }
                }

                BeaconBottomBar(
                    onBAction = bottomBAction,
                    bLabel = bottomBDisplayLabel,
                    onSettings = if (!isBackPage) {
                        {
                            if (tab != BeaconTab.SETTINGS || showAllApps || setupPlatformId != null || appPickerPlatform != null || editTarget != null) {
                                selectTab(BeaconTab.SETTINGS)
                            }
                        }
                    } else {
                        null
                    },
                    onSearch = bottomSearchAction,
                    onMoveUp = if (!isBackPage) moveSelectionUp else null,
                    onMoveDown = if (!isBackPage) moveSelectionDown else null,
                    layoutMode = launcherLayoutMode,
                    onLayoutModeChange = if (!isBackPage) onSetLauncherLayoutMode else null,
                    onLaunchSelected = if (editTarget == null && appPickerPlatform == null) launchSelected else null,
                    centerText = if (editTarget != null) {
                        editCenterText
                    } else if (showAllApps) {
                        I18n.t(context, "launcher.center.all_apps", "全部应用")
                    } else {
                        when (tab) {
                            BeaconTab.NOW -> I18n.t(context, "launcher.center.favorites", "收藏")
                            BeaconTab.NS -> I18n.t(context, "launcher.center.ns", "NS 游戏")
                            BeaconTab.ANDROID -> I18n.t(context, "launcher.center.android", "安卓游戏")
                            BeaconTab.PSP -> I18n.t(context, "launcher.center.psp", "PSP 游戏")
                            BeaconTab.GBA -> I18n.t(context, "launcher.center.gba", "GBA 游戏")
                            BeaconTab.GB -> I18n.t(context, "launcher.center.gb", "GB/GBC 游戏")
                            BeaconTab.SFC -> I18n.t(context, "launcher.center.sfc", "SFC/SNES 游戏")
                            BeaconTab.NES -> I18n.t(context, "launcher.center.nes", "FC 游戏")
                            BeaconTab.SETTINGS -> I18n.t(context, "launcher.center.settings", "设置")
                        }
                    }
                )
            }
        }
    }

    if (showSearchDialog) {
        SearchDialog(
            title = when {
                setupPlatform != null -> I18n.t(context, "launcher.search.current_page", "搜索当前页面")
                showAllApps -> I18n.t(context, "launcher.search.all_apps", "搜索全部应用")
                tab == BeaconTab.NOW -> I18n.t(context, "launcher.search.favorites", "搜索收藏")
                tab == BeaconTab.NS -> I18n.t(context, "launcher.search.ns", "搜索 NS 游戏")
                tab == BeaconTab.ANDROID -> I18n.t(context, "launcher.search.android", "搜索安卓游戏")
                tab == BeaconTab.PSP -> I18n.t(context, "launcher.search.psp", "搜索 PSP 游戏")
                tab == BeaconTab.GBA -> I18n.t(context, "launcher.search.gba", "搜索 GBA 游戏")
                tab == BeaconTab.GB -> I18n.t(context, "launcher.search.gb", "搜索 GB/GBC 游戏")
                tab == BeaconTab.SFC -> I18n.t(context, "launcher.search.sfc", "搜索 SFC/SNES 游戏")
                tab == BeaconTab.NES -> I18n.t(context, "launcher.search.nes", "搜索 FC/NES 游戏")
                tab == BeaconTab.SETTINGS -> I18n.t(context, "launcher.search.settings", "搜索设置")
                else -> I18n.t(context, "common.search", "搜索")
            },
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = { searchQuery = "" },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showHomePrompt) {
        AlertDialog(
            onDismissRequest = onDismissHomePrompt,
            title = { Text(I18n.t(context, "launcher.home.title", "设为默认桌面？"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = { Text(I18n.t(context, "launcher.home.text", "如果这台设备主要用来玩游戏，可以把这个 App 设为默认桌面。以后按 Home 键会回到这里。"), maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            confirmButton = {
                Button(onClick = {
                    onDismissHomePrompt()
                    onOpenHomeSettings()
                }) { Text(I18n.t(context, "launcher.home.confirm", "去选择"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            },
            dismissButton = {
                TextButton(onClick = onDismissHomePrompt) { Text(I18n.t(context, "launcher.home.dismiss", "暂时不要"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
        )
    }

}
