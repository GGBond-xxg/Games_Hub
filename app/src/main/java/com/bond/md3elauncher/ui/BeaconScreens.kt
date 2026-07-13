package com.bond.md3elauncher.ui

import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.data.ItemOverride
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.i18n.I18n
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun <T> applySavedItemOrder(
    items: List<T>,
    savedOrder: List<String>,
    keyOf: (T) -> String
): List<T> {
    if (items.size <= 1 || savedOrder.isEmpty()) return items
    val baseIndex = items.mapIndexed { index, item -> keyOf(item) to index }.toMap()
    val savedIndex = savedOrder.withIndex().associate { it.value to it.index }
    return items.sortedWith(
        compareBy<T> { item -> savedIndex[keyOf(item)] ?: (savedOrder.size + (baseIndex[keyOf(item)] ?: 0)) }
            .thenBy { item -> baseIndex[keyOf(item)] ?: 0 }
    )
}

private fun reorderedKeys(keys: List<String>, selectedKey: String?, delta: Int): List<String>? {
    if (keys.size <= 1 || selectedKey == null) return null
    val currentIndex = keys.indexOf(selectedKey)
    if (currentIndex < 0) return null
    val nextIndex = currentIndex + delta
    if (nextIndex !in keys.indices) return null
    return keys.toMutableList().apply {
        removeAt(currentIndex)
        add(nextIndex, selectedKey)
    }
}

@Composable
internal fun FavoritesBeaconScreen(
    games: List<GameItem>,
    favorites: Set<String>,
    installedApps: List<InstalledApp>,
    itemOverrides: Map<String, ItemOverride>,
    query: String,
    itemOrder: List<String>,
    onSaveItemOrder: (List<String>) -> Unit,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onBottomBLabelChange: (String) -> Unit,
    onMoveSelectionActionsChange: ((() -> Unit)?, (() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit,
    onLaunchGame: (GameItem) -> Unit,
    onToggleFavorite: (GameItem) -> Unit,
    onLaunchAndroidApp: (InstalledApp) -> Unit,
    onToggleAndroidFavorite: (InstalledApp) -> Unit
) {
    val context = LocalContext.current
    val lang = I18n.languageFor(context)
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val entries = remember(games, installedApps, favorites, query, itemOverrides, itemOrder, lang) {
        val base = buildList {
            games.filter { it.id in favorites }.forEach { game ->
                add(
                    FavoriteEntry(
                        key = game.id,
                        title = itemTitle(itemOverrides, game.id, game.title),
                        subtitle = game.serial ?: game.extension,
                        typeLabel = platformDisplayName(game.platformTitle),
                        game = game
                    )
                )
            }
            installedApps.filter { "app:${it.packageName}" in favorites }.forEach { app ->
                val key = "app:${app.packageName}"
                add(
                    FavoriteEntry(
                        key = key,
                        title = itemTitle(itemOverrides, key, app.label),
                        subtitle = app.packageName,
                        typeLabel = if (app.isLikelyEmulator) I18n.t(context, "launcher.type.emulator", "模拟器") else I18n.t(context, "launcher.type.android", "安卓"),
                        app = app
                    )
                )
            }
        }
            .filter { entry ->
                query.isBlank() ||
                    entry.title.contains(query, true) ||
                    entry.subtitle.contains(query, true) ||
                    entry.typeLabel.contains(query, true)
            }
            .sortedBy { it.title.lowercase() }
        applySavedItemOrder(base, itemOrder) { it.key }
    }
    val selected = entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull()
    PublishFavoriteLaunchAction(selected, onLaunchSelectedChange, onLaunchGame, onLaunchAndroidApp)
    PublishFavoriteToggleAction(selected, onToggleSelectedChange, onToggleFavorite, onToggleAndroidFavorite)
    PublishFavoriteEditAction(selected, itemOverrides, onEditSelectedChange, onEdit)
    LaunchedEffect(selected?.key, lang) {
        onBottomBLabelChange(if (selected != null) I18n.t(context, "launcher.bottom.unfavorite", "取消收藏") else I18n.t(context, "launcher.bottom.favorite", "收藏"))
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun moveFavoriteSelection(delta: Int) {
        val key = selected?.key ?: return
        val keys = entries.map { it.key }
        val next = reorderedKeys(keys, key, delta) ?: return
        selectedKey = key
        onSaveItemOrder(next)
        val nextIndex = next.indexOf(key).coerceAtLeast(0)
        scope.launch {
            delay(80L)
            listState.scrollToItem(nextIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(entries, selected?.key, query) {
        val index = entries.indexOfFirst { it.key == selected?.key }
        val canReorder = query.isBlank() && index >= 0
        onMoveSelectionActionsChange(
            if (canReorder && index > 0) { { moveFavoriteSelection(-1) } } else null,
            if (canReorder && index < entries.lastIndex) { { moveFavoriteSelection(1) } } else null
        )
    }

    if (entries.isEmpty()) {
        EmptyBeaconState(
            title = if (query.isBlank()) I18n.t(context, "launcher.empty.favorites.title", "还没有收藏") else I18n.t(context, "launcher.empty.favorites.search_title", "没有匹配的收藏"),
            subtitle = if (query.isBlank()) I18n.t(context, "launcher.empty.favorites.subtitle", "进入设置里的平台管理，或进入安卓应用列表后点击星标收藏。") else I18n.t(context, "launcher.empty.search_subtitle", "按 X 修改搜索内容，或清空搜索。"),
            onLaunchSelectedChange = onLaunchSelectedChange
        )
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                lazyItems(entries, key = { it.key }) { entry ->
                    FavoriteRow(
                        entry = entry,
                        selected = selected?.key == entry.key,
                        onFocus = { selectedKey = entry.key },
                        onClick = {
                            if (selected?.key == entry.key) {
                                entry.game?.let(onLaunchGame)
                                entry.app?.let(onLaunchAndroidApp)
                            } else {
                                selectedKey = entry.key
                            }
                        },
                        onLongClick = {
                            onEdit(entry.toEditTarget(itemOverrides, context))
                        },
                        onToggle = {
                            entry.game?.let(onToggleFavorite)
                            entry.app?.let(onToggleAndroidFavorite)
                        }
                    )
                }
            }
            FavoritePreview(entry = selected, itemOverrides = itemOverrides)
        }
    }
}

@Composable
private fun PublishFavoriteEditAction(
    selected: FavoriteEntry?,
    itemOverrides: Map<String, ItemOverride>,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(selected?.key, itemOverrides, I18n.languageFor(context)) {
        val edit: (() -> Unit)? = selected?.let { entry -> { onEdit(entry.toEditTarget(itemOverrides, context)) } }
        onEditSelectedChange(edit)
    }
}

@Composable
private fun PublishGameEditAction(
    selected: GameItem?,
    itemOverrides: Map<String, ItemOverride>,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit
) {
    LaunchedEffect(selected?.id, itemOverrides) {
        val edit: (() -> Unit)? = selected?.let { game -> { onEdit(game.toEditTarget(itemOverrides)) } }
        onEditSelectedChange(edit)
    }
}

@Composable
private fun PublishAndroidEditAction(
    selected: InstalledApp?,
    itemOverrides: Map<String, ItemOverride>,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(selected?.packageName, itemOverrides, I18n.languageFor(context)) {
        val edit: (() -> Unit)? = selected?.let { app -> { onEdit(app.toEditTarget(itemOverrides, context)) } }
        onEditSelectedChange(edit)
    }
}

@Composable
private fun PublishFavoriteLaunchAction(
    selected: FavoriteEntry?,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onLaunchGame: (GameItem) -> Unit,
    onLaunchAndroidApp: (InstalledApp) -> Unit
) {
    LaunchedEffect(selected?.key) {
        val launch: (() -> Unit)? = when {
            selected?.game != null -> {
                val game = selected.game
                { onLaunchGame(game) }
            }
            selected?.app != null -> {
                val app = selected.app
                { onLaunchAndroidApp(app) }
            }
            else -> null
        }
        onLaunchSelectedChange(launch)
    }
}

@Composable
private fun PublishFavoriteToggleAction(
    selected: FavoriteEntry?,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleFavorite: (GameItem) -> Unit,
    onToggleAndroidFavorite: (InstalledApp) -> Unit
) {
    LaunchedEffect(selected?.key) {
        val toggle: (() -> Unit)? = when {
            selected?.game != null -> {
                val game = selected.game
                { onToggleFavorite(game) }
            }
            selected?.app != null -> {
                val app = selected.app
                { onToggleAndroidFavorite(app) }
            }
            else -> null
        }
        onToggleSelectedChange(toggle)
    }
}

@Composable
internal fun PlatformBeaconScreen(
    platform: PlatformConfig?,
    games: List<GameItem>,
    favorites: Set<String>,
    itemOverrides: Map<String, ItemOverride>,
    query: String,
    itemOrder: List<String>,
    onSaveItemOrder: (List<String>) -> Unit,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onBottomBLabelChange: (String) -> Unit,
    onMoveSelectionActionsChange: ((() -> Unit)?, (() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit,
    onOpenPlatform: (PlatformConfig) -> Unit,
    onLaunchGame: (GameItem) -> Unit,
    onToggleFavorite: (GameItem) -> Unit
) {
    val context = LocalContext.current
    val lang = I18n.languageFor(context)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(games, query, itemOverrides, itemOrder) {
        val base = games.filter {
            val displayTitle = itemTitle(itemOverrides, it.id, it.title)
            query.isBlank() ||
                displayTitle.contains(query, true) ||
                it.fileName.contains(query, true) ||
                it.serial.orEmpty().contains(query, true)
        }
        applySavedItemOrder(base, itemOrder) { it.id }
    }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()
    PublishGameLaunchAction(selected, onLaunchSelectedChange, onLaunchGame)
    PublishGameToggleAction(selected, onToggleSelectedChange, onToggleFavorite)
    PublishGameEditAction(selected, itemOverrides, onEditSelectedChange, onEdit)
    LaunchedEffect(selected?.id, favorites, lang) {
        val isFavorite = selected?.let { it.id in favorites } == true
        onBottomBLabelChange(if (isFavorite) I18n.t(context, "launcher.bottom.unfavorite", "取消收藏") else I18n.t(context, "launcher.bottom.favorite", "收藏"))
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun moveGameSelection(delta: Int) {
        val key = selected?.id ?: return
        val keys = visible.map { it.id }
        val next = reorderedKeys(keys, key, delta) ?: return
        selectedId = key
        onSaveItemOrder(next)
        val nextIndex = next.indexOf(key).coerceAtLeast(0)
        scope.launch {
            delay(80L)
            listState.scrollToItem(nextIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(visible, selected?.id, query) {
        val index = visible.indexOfFirst { it.id == selected?.id }
        val canReorder = query.isBlank() && index >= 0
        onMoveSelectionActionsChange(
            if (canReorder && index > 0) { { moveGameSelection(-1) } } else null,
            if (canReorder && index < visible.lastIndex) { { moveGameSelection(1) } } else null
        )
    }

    if (platform == null) {
        EmptyBeaconState(I18n.t(context, "launcher.empty.platform_missing.title", "平台不存在"), I18n.t(context, "launcher.empty.platform_missing.subtitle", "没有找到这个平台配置。"), onLaunchSelectedChange)
        return
    }

    if (visible.isEmpty()) {
        EmptyBeaconState(
            title = if (query.isBlank()) I18n.t(context, "launcher.empty.platform.no_games", "没有扫描到 {platform} 游戏", "platform" to platformDisplayName(platform.kind.title)) else I18n.t(context, "launcher.empty.platform.no_match", "没有匹配的 {platform} 游戏", "platform" to platformDisplayName(platform.kind.title)),
            subtitle = if (query.isBlank()) I18n.t(context, "launcher.empty.platform.subtitle_setup", "进入“设置”里的平台管理，选择 ROM 文件夹和模拟器后再扫描。") else I18n.t(context, "launcher.empty.search_subtitle", "按 X 修改搜索内容，或清空搜索。"),
            onLaunchSelectedChange = onLaunchSelectedChange
        )
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                lazyItems(visible, key = { it.id }) { game ->
                    val isSelected = selected?.id == game.id
                    GameRow(
                        game = game,
                        selected = isSelected,
                        favorite = game.id in favorites,
                        itemOverrides = itemOverrides,
                        onFocus = { selectedId = game.id },
                        onClick = {
                            if (isSelected) onLaunchGame(game) else selectedId = game.id
                        },
                        onLongClick = {
                            onEdit(game.toEditTarget(itemOverrides))
                        },
                        onToggleFavorite = { onToggleFavorite(game) }
                    )
                }
            }
            GamePreview(game = selected, itemOverrides = itemOverrides)
        }
    }
}

@Composable
private fun PublishGameLaunchAction(
    selected: GameItem?,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onLaunchGame: (GameItem) -> Unit
) {
    LaunchedEffect(selected?.id) {
        val launch: (() -> Unit)? = selected?.let { game -> { onLaunchGame(game) } }
        onLaunchSelectedChange(launch)
    }
}

@Composable
private fun PublishGameToggleAction(
    selected: GameItem?,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleFavorite: (GameItem) -> Unit
) {
    LaunchedEffect(selected?.id) {
        val toggle: (() -> Unit)? = selected?.let { game -> { onToggleFavorite(game) } }
        onToggleSelectedChange(toggle)
    }
}

@Composable
internal fun AndroidBeaconScreen(
    apps: List<InstalledApp>,
    favorites: Set<String>,
    taggedApps: Set<String>,
    itemOverrides: Map<String, ItemOverride>,
    query: String,
    itemOrder: List<String>,
    onSaveItemOrder: (List<String>) -> Unit,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onEditSelectedChange: ((() -> Unit)?) -> Unit,
    onBottomBLabelChange: (String) -> Unit,
    onMoveSelectionActionsChange: ((() -> Unit)?, (() -> Unit)?) -> Unit,
    onEdit: (EditTarget) -> Unit,
    onLaunchAndroidApp: (InstalledApp) -> Unit,
    onToggleAndroidFavorite: (InstalledApp) -> Unit,
    onToggleAndroidTag: (InstalledApp) -> Unit,
    onlyTagged: Boolean
) {
    val context = LocalContext.current
    val lang = I18n.languageFor(context)
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(apps, query, itemOverrides, itemOrder) {
        val base = apps.filter { app ->
            val key = "app:${app.packageName}"
            val displayTitle = itemTitle(itemOverrides, key, app.label)
            query.isBlank() || displayTitle.contains(query, true) || app.packageName.contains(query, true)
        }
        applySavedItemOrder(base, itemOrder) { "app:${it.packageName}" }
    }
    val selected = visible.firstOrNull { it.packageName == selectedPackage } ?: visible.firstOrNull()
    PublishAndroidLaunchAction(selected, onLaunchSelectedChange, onLaunchAndroidApp)
    if (onlyTagged) {
        PublishAndroidFavoriteAction(selected, onToggleSelectedChange, onToggleAndroidFavorite)
    } else {
        PublishAndroidTagAction(selected, onToggleSelectedChange, onToggleAndroidTag)
    }
    PublishAndroidEditAction(selected, itemOverrides, onEditSelectedChange, onEdit)
    LaunchedEffect(selected?.packageName, favorites, taggedApps, onlyTagged, lang) {
        val key = selected?.let { "app:${it.packageName}" }
        val label = if (onlyTagged) {
            if (key != null && key in favorites) I18n.t(context, "launcher.bottom.unfavorite", "取消收藏") else I18n.t(context, "launcher.bottom.favorite", "收藏")
        } else {
            if (key != null && key in taggedApps) I18n.t(context, "launcher.bottom.remove_add", "取消添加") else I18n.t(context, "launcher.bottom.add", "添加")
        }
        onBottomBLabelChange(label)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun moveAndroidSelection(delta: Int) {
        val selectedApp = selected ?: return
        val key = "app:${selectedApp.packageName}"
        val keys = visible.map { "app:${it.packageName}" }
        val next = reorderedKeys(keys, key, delta) ?: return
        selectedPackage = selectedApp.packageName
        onSaveItemOrder(next)
        val nextIndex = next.indexOf(key).coerceAtLeast(0)
        scope.launch {
            delay(80L)
            listState.scrollToItem(nextIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(visible, selected?.packageName, query) {
        val index = visible.indexOfFirst { it.packageName == selected?.packageName }
        val canReorder = query.isBlank() && index >= 0
        onMoveSelectionActionsChange(
            if (canReorder && index > 0) { { moveAndroidSelection(-1) } } else null,
            if (canReorder && index < visible.lastIndex) { { moveAndroidSelection(1) } } else null
        )
    }

    if (visible.isEmpty()) {
        EmptyBeaconState(
            title = if (query.isBlank()) {
                if (onlyTagged) I18n.t(context, "launcher.empty.android.no_games", "还没有安卓游戏") else I18n.t(context, "launcher.empty.android.no_apps", "没有应用")
            } else {
                if (onlyTagged) I18n.t(context, "launcher.empty.android.no_match_games", "没有匹配的安卓游戏") else I18n.t(context, "launcher.empty.android.no_match_apps", "没有匹配的应用")
            },
            subtitle = if (query.isBlank()) {
                if (onlyTagged) I18n.t(context, "launcher.empty.android.subtitle_tag", "按 B 进入全部应用列表，把想显示在主页安卓里的应用标记为游戏。") else I18n.t(context, "launcher.empty.android.subtitle_empty", "当前没有读取到可启动的安卓应用。")
            } else {
                I18n.t(context, "launcher.empty.search_subtitle", "按 X 修改搜索内容，或清空搜索。")
            },
            onLaunchSelectedChange = onLaunchSelectedChange
        )
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                lazyItems(visible, key = { it.packageName }) { app ->
                    val key = "app:${app.packageName}"
                    val isSelected = selected?.packageName == app.packageName
                    AndroidAppRow(
                        app = app,
                        selected = isSelected,
                        favorite = key in favorites,
                        tagged = key in taggedApps,
                        itemOverrides = itemOverrides,
                        showFavoriteButton = onlyTagged,
                        onFocus = { selectedPackage = app.packageName },
                        onClick = {
                            if (isSelected) onLaunchAndroidApp(app) else selectedPackage = app.packageName
                        },
                        onLongClick = {
                            onEdit(app.toEditTarget(itemOverrides, context))
                        },
                        onToggleFavorite = { onToggleAndroidFavorite(app) },
                        onToggleTag = { onToggleAndroidTag(app) }
                    )
                }
            }
            AndroidAppPreview(app = selected, itemOverrides = itemOverrides)
        }
    }
}

@Composable
private fun PublishAndroidLaunchAction(
    selected: InstalledApp?,
    onLaunchSelectedChange: ((() -> Unit)?) -> Unit,
    onLaunchAndroidApp: (InstalledApp) -> Unit
) {
    LaunchedEffect(selected?.packageName) {
        val launch: (() -> Unit)? = selected?.let { app -> { onLaunchAndroidApp(app) } }
        onLaunchSelectedChange(launch)
    }
}

@Composable
private fun PublishAndroidTagAction(
    selected: InstalledApp?,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleAndroidTag: (InstalledApp) -> Unit
) {
    LaunchedEffect(selected?.packageName) {
        val toggle: (() -> Unit)? = selected?.let { app -> { onToggleAndroidTag(app) } }
        onToggleSelectedChange(toggle)
    }
}

@Composable
private fun PublishAndroidFavoriteAction(
    selected: InstalledApp?,
    onToggleSelectedChange: ((() -> Unit)?) -> Unit,
    onToggleAndroidFavorite: (InstalledApp) -> Unit
) {
    LaunchedEffect(selected?.packageName) {
        val toggle: (() -> Unit)? = selected?.let { app -> { onToggleAndroidFavorite(app) } }
        onToggleSelectedChange(toggle)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(entry: FavoriteEntry, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, onToggle: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .gamepadLongPressEdit(onLongClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
            IconButton(onClick = onToggle, modifier = Modifier.focusProperties { canFocus = false }) { Icon(Icons.Rounded.Star, contentDescription = I18n.t(LocalContext.current, "launcher.bottom.unfavorite", "取消收藏")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameRow(
    game: GameItem,
    selected: Boolean,
    favorite: Boolean,
    itemOverrides: Map<String, ItemOverride>,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .gamepadLongPressEdit(onLongClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(itemTitle(itemOverrides, game.id, game.title), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
            IconButton(onClick = onToggleFavorite, modifier = Modifier.focusProperties { canFocus = false }) { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = I18n.t(LocalContext.current, "launcher.bottom.favorite", "收藏")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AndroidAppRow(
    app: InstalledApp,
    selected: Boolean,
    favorite: Boolean,
    tagged: Boolean,
    itemOverrides: Map<String, ItemOverride>,
    showFavoriteButton: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTag: () -> Unit
) {
    val key = "app:${app.packageName}"
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .gamepadLongPressEdit(onLongClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPreviewImage(app = app, overridePath = itemImagePath(itemOverrides, key), modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(itemTitle(itemOverrides, key, app.label), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
            if (showFavoriteButton) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.focusProperties { canFocus = false }) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = I18n.t(LocalContext.current, "launcher.bottom.favorite", "收藏")
                    )
                }
            } else {
                IconButton(onClick = onToggleTag, modifier = Modifier.focusProperties { canFocus = false }) {
                    Icon(
                        if (tagged) Icons.Rounded.SportsEsports else Icons.Outlined.SportsEsports,
                        contentDescription = I18n.t(LocalContext.current, "launcher.action.mark_as_game", "标记为游戏")
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.gamepadLongPressEdit(onLongPress: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(false) }

    fun isEditKey(key: Key): Boolean = key == Key.ButtonA ||
        key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter

    return onPreviewKeyEvent { event ->
        if (!isEditKey(event.key)) return@onPreviewKeyEvent false

        when (event.type) {
            KeyEventType.KeyDown -> {
                if (longPressJob == null && !longPressFired) {
                    longPressJob = scope.launch {
                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                        longPressFired = true
                        longPressJob = null
                        currentOnLongPress()
                    }
                }
                longPressFired
            }
            KeyEventType.KeyUp -> {
                val consume = longPressFired
                longPressJob?.cancel()
                longPressJob = null
                longPressFired = false
                consume
            }
            else -> false
        }
    }
}

@Composable
private fun FavoritePreview(entry: FavoriteEntry?, itemOverrides: Map<String, ItemOverride>) {
    when {
        entry?.game != null -> GamePreview(game = entry.game, itemOverrides = itemOverrides)
        entry?.app != null -> AndroidAppPreview(app = entry.app, itemOverrides = itemOverrides)
        else -> PreviewPlaceholder(I18n.t(LocalContext.current, "launcher.preview.select_favorite", "选择一个收藏"))
    }
}

@Composable
private fun GamePreview(game: GameItem?, itemOverrides: Map<String, ItemOverride>) {
    if (game == null) {
        PreviewPlaceholder(I18n.t(LocalContext.current, "launcher.preview.select_game", "选择一个游戏"))
        return
    }
    PreviewFrame {
        GameCover(
            game = game,
            overridePath = itemImagePath(itemOverrides, game.id),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun AndroidAppPreview(app: InstalledApp?, itemOverrides: Map<String, ItemOverride>) {
    if (app == null) {
        PreviewPlaceholder(I18n.t(LocalContext.current, "launcher.preview.select_android_app", "选择一个安卓应用"))
        return
    }
    PreviewFrame {
        AppPreviewImage(
            app = app,
            overridePath = itemImagePath(itemOverrides, "app:${app.packageName}"),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    // 按草图把右侧改成更靠右、无外边框、竖向拉长的展示区。
    // 左侧列表尽量扩展，右侧只保留一个窄而高的预览位。
    Box(
        modifier = Modifier
            .width(208.dp)
            .fillMaxHeight()
            .padding(top = 2.dp, bottom = 2.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}

@Composable
private fun PreviewPlaceholder(text: String) {
    PreviewFrame {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun EmptyBeaconState(title: String, subtitle: String, onLaunchSelectedChange: ((() -> Unit)?) -> Unit) {
    LaunchedEffect(title, subtitle) { onLaunchSelectedChange(null) }
    Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
        OutlinedCard(shape = RoundedCornerShape(24.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun GameItem.toEditTarget(overrides: Map<String, ItemOverride>): EditTarget =
    EditTarget(
        key = id,
        defaultTitle = title,
        currentTitle = itemTitle(overrides, id, title),
        currentImagePath = itemImagePath(overrides, id, coverPath),
        typeLabel = platformDisplayName(platformTitle)
    )

private fun InstalledApp.toEditTarget(overrides: Map<String, ItemOverride>, context: android.content.Context): EditTarget {
    val key = "app:$packageName"
    return EditTarget(
        key = key,
        defaultTitle = label,
        currentTitle = itemTitle(overrides, key, label),
        currentImagePath = itemImagePath(overrides, key),
        typeLabel = I18n.t(context, "launcher.type.android_app", "安卓应用")
    )
}

private fun FavoriteEntry.toEditTarget(overrides: Map<String, ItemOverride>, context: android.content.Context): EditTarget {
    game?.let { return it.toEditTarget(overrides) }
    app?.let { return it.toEditTarget(overrides, context) }
    return EditTarget(key = key, defaultTitle = title, currentTitle = title, currentImagePath = itemImagePath(overrides, key), typeLabel = typeLabel)
}
