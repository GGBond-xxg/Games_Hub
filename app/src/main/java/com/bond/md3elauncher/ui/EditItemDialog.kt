package com.bond.md3elauncher.ui

import androidx.core.net.toUri
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bond.md3elauncher.data.CoverCandidate
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.system.CoverScraper
import com.bond.md3elauncher.system.CoverArtworkType
import com.bond.md3elauncher.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private enum class EditImageSlot {
    PREVIEW,
    GRID
}

@Composable
internal fun EditItemPage(
    target: EditTarget,
    onSaveMetadata: (com.bond.md3elauncher.data.GameMetadata) -> Unit,
    scraperSettings: ScraperSettings,
    onBack: () -> Unit,
    onFooterTextChange: (String) -> Unit = {},
    onSave: (
        title: String,
        previewImageUriString: String?,
        gridImageUriString: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var metadata by remember(target.key) { mutableStateOf(target.metadata) }
    var showMetadata by rememberSaveable(target.key) { mutableStateOf(false) }
    if (showMetadata && metadata != null) {
        GameInfoEditor(metadata!!, onDismiss = { showMetadata = false }, onSave = { metadata = it; onSaveMetadata(it) })
    }
    var title by rememberSaveable(target.key) { mutableStateOf(target.currentTitle) }
    var selectedPreviewImageUri by rememberSaveable(target.key) { mutableStateOf<String?>(null) }
    var selectedGridImageUri by rememberSaveable(target.key) { mutableStateOf<String?>(null) }
    var scraperSlot by rememberSaveable(target.key) { mutableStateOf<EditImageSlot?>(null) }

    LaunchedEffect(scraperSlot, scraperSettings, I18n.languageFor(context)) {
        onFooterTextChange(
            if (scraperSlot != null) compactSourceText(context, scraperSettings)
            else ""
        )
    }

    val activeScraperSlot = scraperSlot
    if (activeScraperSlot != null) {
        CoverScrapePage(
            target = target,
            imageSlot = activeScraperSlot,
            queryTitle = title,
            scraperSettings = scraperSettings,
            onBack = { scraperSlot = null },
            onSelected = { localPath ->
                val uri = Uri.fromFile(File(localPath)).toString()
                when (activeScraperSlot) {
                    EditImageSlot.PREVIEW -> selectedPreviewImageUri = uri
                    EditImageSlot.GRID -> selectedGridImageUri = uri
                }
                scraperSlot = null
            }
        )
        return
    }

    EditInfoPage(
        target = target,
        onEditMetadata = if (metadata != null) ({ showMetadata = true }) else null,
        title = title,
        selectedPreviewImageUri = selectedPreviewImageUri,
        selectedGridImageUri = selectedGridImageUri,
        onTitleChange = { title = it },
        onPreviewImageSelected = { selectedPreviewImageUri = it },
        onGridImageSelected = { selectedGridImageUri = it },
        onSearchPreviewImage = { scraperSlot = EditImageSlot.PREVIEW },
        onSearchGridImage = { scraperSlot = EditImageSlot.GRID },
        onBack = onBack,
        onSave = { onSave(title, selectedPreviewImageUri, selectedGridImageUri) },
        onRestoreName = { title = target.defaultTitle }
    )
}

@Composable
private fun EditInfoPage(
    target: EditTarget,
    onEditMetadata: (() -> Unit)?,
    title: String,
    selectedPreviewImageUri: String?,
    selectedGridImageUri: String?,
    onTitleChange: (String) -> Unit,
    onPreviewImageSelected: (String?) -> Unit,
    onGridImageSelected: (String?) -> Unit,
    onSearchPreviewImage: () -> Unit,
    onSearchGridImage: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRestoreName: () -> Unit
) {
    val context = LocalContext.current
    var showNameDialog by rememberSaveable(target.key) { mutableStateOf(false) }
    var draftTitle by rememberSaveable(target.key) { mutableStateOf(title) }
    var pickerSlot by remember { mutableStateOf<EditImageSlot?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val slot = pickerSlot
        pickerSlot = null
        if (uri != null) {
            when (slot) {
                EditImageSlot.PREVIEW -> onPreviewImageSelected(uri.toString())
                EditImageSlot.GRID -> onGridImageSelected(uri.toString())
                null -> Unit
            }
        }
    }

    val previewBitmap = remember(
        target.currentPreviewImagePath,
        target.defaultPreviewImagePath,
        selectedPreviewImageUri
    ) {
        loadEditImageBitmap(
            context = context,
            selectedImageUri = selectedPreviewImageUri,
            currentImagePath = target.currentPreviewImagePath,
            defaultImagePath = target.defaultPreviewImagePath
        )
    }
    val gridBitmap = remember(
        target.currentGridImagePath,
        target.defaultGridImagePath,
        selectedGridImageUri
    ) {
        loadEditImageBitmap(
            context = context,
            selectedImageUri = selectedGridImageUri,
            currentImagePath = target.currentGridImagePath,
            defaultImagePath = target.defaultGridImagePath
        )
    }

    val canRestorePreview = selectedPreviewImageUri != "__REMOVE__" &&
        (target.currentPreviewImagePath != null || !selectedPreviewImageUri.isNullOrBlank())
    val canRestoreGrid = selectedGridImageUri != "__REMOVE__" &&
        (target.currentGridImagePath != null || !selectedGridImageUri.isNullOrBlank())

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoColumns = maxWidth >= 620.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HubIconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = I18n.t(context, "common.back", "返回")
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    I18n.t(context, "edit.title", "编辑显示信息"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(12.dp))
                HubOutlinedButton(
                    onClick = {
                        draftTitle = title.ifBlank { target.defaultTitle }
                        showNameDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = I18n.t(context, "edit.edit_display_name", "编辑显示名称"),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        title.ifBlank { target.defaultTitle },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                HubTextButton(
                    onClick = onBack,
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        I18n.t(context, "common.cancel", "取消"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
                HubButton(
                    onClick = onSave,
                    enabled = title.isNotBlank(),
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(
                        I18n.t(context, "common.save", "保存"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (onEditMetadata != null) {
                HubTextButton(onClick = onEditMetadata) {
                    Text(I18n.t(context, "metadata.edit", "Game information"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (useTwoColumns) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EditArtworkCard(
                        title = I18n.t(context, "edit.preview_image", "预览图"),
                        usageText = I18n.t(context, "edit.preview_usage", "右侧大图 · 建议竖版"),
                        titleIcon = Icons.Rounded.PhotoLibrary,
                        bitmap = previewBitmap,
                        canRestore = canRestorePreview,
                        onRestore = { onPreviewImageSelected("__REMOVE__") },
                        onSearch = onSearchPreviewImage,
                        onPick = {
                            pickerSlot = EditImageSlot.PREVIEW
                            imagePicker.launch("image/*")
                        },
                        searchAccessibilityLabel = I18n.t(
                            context,
                            "edit.search_preview_image",
                            "联网选择预览图"
                        ),
                        pickAccessibilityLabel = I18n.t(
                            context,
                            "edit.pick_preview_image",
                            "设备选择预览图"
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Fit,
                        portraitPreview = true
                    )
                    EditArtworkCard(
                        title = I18n.t(context, "edit.grid_image", "宫格图"),
                        usageText = I18n.t(context, "edit.grid_usage", "宫格卡片 · 建议横版"),
                        titleIcon = Icons.Rounded.Apps,
                        bitmap = gridBitmap,
                        canRestore = canRestoreGrid,
                        onRestore = { onGridImageSelected("__REMOVE__") },
                        onSearch = onSearchGridImage,
                        onPick = {
                            pickerSlot = EditImageSlot.GRID
                            imagePicker.launch("image/*")
                        },
                        searchAccessibilityLabel = I18n.t(
                            context,
                            "edit.search_grid_image",
                            "联网选择宫格图"
                        ),
                        pickAccessibilityLabel = I18n.t(
                            context,
                            "edit.pick_grid_image",
                            "设备选择宫格图"
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                        portraitPreview = false
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EditArtworkCard(
                        title = I18n.t(context, "edit.preview_image", "预览图"),
                        usageText = I18n.t(context, "edit.preview_usage", "右侧大图 · 建议竖版"),
                        titleIcon = Icons.Rounded.PhotoLibrary,
                        bitmap = previewBitmap,
                        canRestore = canRestorePreview,
                        onRestore = { onPreviewImageSelected("__REMOVE__") },
                        onSearch = onSearchPreviewImage,
                        onPick = {
                            pickerSlot = EditImageSlot.PREVIEW
                            imagePicker.launch("image/*")
                        },
                        searchAccessibilityLabel = I18n.t(context, "edit.search_preview_image", "联网选择预览图"),
                        pickAccessibilityLabel = I18n.t(context, "edit.pick_preview_image", "设备选择预览图"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Fit,
                        portraitPreview = true
                    )
                    EditArtworkCard(
                        title = I18n.t(context, "edit.grid_image", "宫格图"),
                        usageText = I18n.t(context, "edit.grid_usage", "宫格卡片 · 建议横版"),
                        titleIcon = Icons.Rounded.Apps,
                        bitmap = gridBitmap,
                        canRestore = canRestoreGrid,
                        onRestore = { onGridImageSelected("__REMOVE__") },
                        onSearch = onSearchGridImage,
                        onPick = {
                            pickerSlot = EditImageSlot.GRID
                            imagePicker.launch("image/*")
                        },
                        searchAccessibilityLabel = I18n.t(context, "edit.search_grid_image", "联网选择宫格图"),
                        pickAccessibilityLabel = I18n.t(context, "edit.pick_grid_image", "设备选择宫格图"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop,
                        portraitPreview = false
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(160)
            runCatching { focusRequester.requestFocus() }
        }

        HubAlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = {
                Text(
                    I18n.t(context, "edit.edit_display_name", "编辑显示名称"),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubOutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        label = {
                            Text(
                                I18n.t(context, "edit.display_name", "显示名称"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    Text(
                        I18n.t(
                            context,
                            "edit.name_length_hint",
                            "名称过长会在顶部和列表中自动以 ... 省略显示。"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                HubTextButton(
                    onClick = {
                        val cleanTitle = draftTitle.trim()
                        if (cleanTitle.isNotBlank()) {
                            onTitleChange(cleanTitle)
                            showNameDialog = false
                        }
                    },
                    enabled = draftTitle.isNotBlank()
                ) {
                    Text(
                        I18n.t(context, "edit.confirm", "确定"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HubTextButton(
                        onClick = {
                            draftTitle = target.defaultTitle
                            onRestoreName()
                            showNameDialog = false
                        }
                    ) {
                        Text(
                            I18n.t(context, "common.restore_default", "还原默认"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HubTextButton(onClick = { showNameDialog = false }) {
                        Text(
                            I18n.t(context, "common.cancel", "取消"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun EditArtworkCard(
    title: String,
    usageText: String,
    titleIcon: androidx.compose.ui.graphics.vector.ImageVector,
    bitmap: ImageBitmap?,
    canRestore: Boolean,
    onRestore: () -> Unit,
    onSearch: () -> Unit,
    onPick: () -> Unit,
    searchAccessibilityLabel: String,
    pickAccessibilityLabel: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale,
    portraitPreview: Boolean
) {
    val context = LocalContext.current
    HubSurface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    titleIcon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    usageText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (canRestore) {
                    HubIconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = I18n.t(
                                context,
                                "common.restore_default",
                                "还原默认"
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.26f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = if (portraitPreview) {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(2f / 3f)
                        } else {
                            Modifier.fillMaxSize()
                        },
                        contentScale = contentScale
                    )
                } else {
                    Icon(
                        titleIcon,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditArtworkActionButton(
                    text = I18n.t(context, "edit.choose_online", "联网选择"),
                    icon = Icons.Rounded.Search,
                    accessibilityLabel = searchAccessibilityLabel,
                    onClick = onSearch,
                    filled = true,
                    modifier = Modifier.weight(1f)
                )
                EditArtworkActionButton(
                    text = I18n.t(context, "edit.choose_device", "设备选择"),
                    icon = Icons.Rounded.PhotoLibrary,
                    accessibilityLabel = pickAccessibilityLabel,
                    onClick = onPick,
                    filled = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EditArtworkActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    accessibilityLabel: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    val body: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(icon, contentDescription = accessibilityLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
    if (filled) HubFilledTonalButton(onClick, modifier.height(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp), content = body)
    else HubOutlinedButton(onClick, modifier.height(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp), content = body)
}

private fun loadEditImageBitmap(
    context: android.content.Context,
    selectedImageUri: String?,
    currentImagePath: String?,
    defaultImagePath: String?
): ImageBitmap? {
    val source = when {
        selectedImageUri == "__REMOVE__" -> defaultImagePath
        !selectedImageUri.isNullOrBlank() -> selectedImageUri
        !currentImagePath.isNullOrBlank() -> currentImagePath
        else -> defaultImagePath
    } ?: return null

    return runCatching {
        val uri = (source).toUri()
        when {
            uri.scheme == "content" -> context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
            uri.scheme == "file" -> BitmapFactory.decodeFile(uri.path)?.asImageBitmap()
            else -> BitmapFactory.decodeFile(source)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
private fun CoverScrapePage(
    target: EditTarget,
    imageSlot: EditImageSlot,
    queryTitle: String,
    scraperSettings: ScraperSettings,
    onBack: () -> Unit,
    onSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualQuery by rememberSaveable(target.key) { mutableStateOf(queryTitle) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var debugText by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    var artworkType by rememberSaveable(target.key, imageSlot) {
        mutableStateOf(if (imageSlot == EditImageSlot.PREVIEW) CoverArtworkType.SCREENSHOT else CoverArtworkType.BOX_ART)
    }
    val scraper = remember { CoverScraper(context) }

    fun search() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            errorText = null
            debugText = null
            val activeQuery = manualQuery.ifBlank { queryTitle }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    scraper.searchCovers(
                        title = activeQuery,
                        platformLabel = target.typeLabel,
                        serial = null,
                        settings = scraperSettings,
                        artworkType = artworkType
                    )
                }
            }
            candidates = result.getOrDefault(emptyList())
            debugText = scraper.lastReport
            errorText = result.exceptionOrNull()?.let { I18n.t(context, "cover.report.network_failed", "Request failed. Check your network and try again.") }
            if (candidates.isEmpty() && errorText == null) {
                errorText = I18n.t(context, scraper.lastErrorKey ?: "edit.no_artwork_found", "No images of this type found. Try another artwork type or title.")
            }
            isLoading = false
        }
    }

    LaunchedEffect(target.key, imageSlot, artworkType) { search() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = I18n.t(context, "common.back", "返回")) }
            Column(Modifier.weight(1f)) {
                Text(
                    if (imageSlot == EditImageSlot.PREVIEW) {
                        I18n.t(context, "edit.search_preview_image", "联网选择预览图")
                    } else {
                        I18n.t(context, "edit.search_grid_image", "联网选择宫格图")
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(target.typeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        }

        ArtworkSearchBody(
            manualQuery = manualQuery, onQueryChange = { manualQuery = it },
            artworkType = artworkType, onArtworkTypeChange = { artworkType = it },
            preview = imageSlot == EditImageSlot.PREVIEW, isLoading = isLoading,
            candidates = candidates, errorText = errorText, debugText = debugText,
            onSearch = { search() }, modifier = Modifier.weight(1f),
            onCandidate = { candidate ->
                scope.launch {
                    isLoading = true
                    errorText = null
                    val path = withContext(Dispatchers.IO) { scraper.downloadCandidate(candidate, target.key) }
                    isLoading = false
                    if (path != null) onSelected(path) else errorText = I18n.t(context, "edit.download_cover_failed", "Download failed")
                }
            }
        )
    }
}

/** Search controls scroll with the results, leaving only the page's back/title bar fixed. */
@Composable
internal fun ArtworkSearchBody(
    manualQuery: String,
    onQueryChange: (String) -> Unit,
    artworkType: CoverArtworkType,
    onArtworkTypeChange: (CoverArtworkType) -> Unit,
    preview: Boolean,
    isLoading: Boolean,
    candidates: List<CoverCandidate>,
    errorText: String?,
    debugText: String?,
    onSearch: () -> Unit,
    onCandidate: (CoverCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(isLoading) { if (isLoading) gridState.scrollToItem(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / 126.dp).toInt().coerceIn(1, 4)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns), state = gridState,
            modifier = Modifier.fillMaxSize().testTag("artwork-search-scroll"),
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "query", span = { GridItemSpan(maxLineSpan) }) {
                CompactSearchField(manualQuery, onQueryChange, !isLoading, onSearch)
            }
            item(key = "type", span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.t(context, "edit.artwork_type", "Artwork type"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CoverArtworkType.entries.forEach { type ->
                        HubFilterChip(selected = artworkType == type, onClick = { onArtworkTypeChange(type) }, enabled = !isLoading,
                            label = { Text(I18n.t(context, if (type == CoverArtworkType.BOX_ART) "edit.artwork_cover" else "edit.artwork_screenshot", "Artwork"), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }
            if (preview) item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(I18n.t(context, "edit.preview_scraper_hint", "Choose screenshots or cover art."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                isLoading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> {
                    if (errorText != null || candidates.isEmpty()) item(key = "message", span = { GridItemSpan(maxLineSpan) }) {
                        Text(errorText ?: I18n.t(context, "edit.no_cover_candidate", "No images found"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    gridItems(candidates, key = { "image:${it.imageUrl}" }) { candidate ->
                        CoverCandidateCard(candidate, onClick = { onCandidate(candidate) })
                    }
                    if (candidates.isEmpty() && !debugText.isNullOrBlank()) item(key = "report", span = { GridItemSpan(maxLineSpan) }) {
                        Text(debugText, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, maxLines = 16, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSearchField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onSearch: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (enabled) {
            focusManager.clearFocus()
            keyboard?.hide()
            onSearch()
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HubOutlinedTextField(value, onValueChange, modifier = Modifier.weight(1f).height(56.dp), enabled = enabled, singleLine = true,
            placeholder = { Text(I18n.t(context, "edit.search_placeholder", "Search title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }), shape = RoundedCornerShape(16.dp))
        HubFilledTonalButton(onClick = submit, enabled = enabled, modifier = Modifier.height(56.dp)) {
            Text(I18n.t(context, if (enabled) "common.search" else "common.searching", "Search"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun compactSourceText(context: android.content.Context, settings: ScraperSettings): String {
    val sources = buildList {
        if (settings.useLibretro) add("Libretro")
        if (settings.steamGridDbApiKey.isNotBlank()) add("SteamGridDB")
        if (settings.theGamesDbApiKey.isNotBlank()) add("TheGamesDB")
        if (settings.screenScraperDevId.isNotBlank() && settings.screenScraperDevPassword.isNotBlank()) add("ScreenScraper")
    }
    val visible = sources.take(3).joinToString(" / ")
    val suffix = if (sources.size > 3) " / ..." else ""
    return I18n.t(context, "edit.source_prefix", "来源：{sources}", "sources" to (visible + suffix))
}

@Composable
private fun CoverCandidateCard(candidate: CoverCandidate, onClick: () -> Unit) {
    HubElevatedCard(
        modifier = Modifier
            .width(106.dp)
            .height(194.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteCandidateImage(candidate.imageUrl, Modifier.width(88.dp).height(126.dp))
            Spacer(Modifier.height(4.dp))
            Text(candidate.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(candidate.source, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RemoteCandidateImage(url: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { com.bond.md3elauncher.system.ScrapeNetwork.request {
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 7000; connection.readTimeout = 9000
                    check(connection.responseCode in 200..299)
                    require(connection.contentLengthLong <= 8 * 1024 * 1024)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 8 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 40_000_000)
                    options.inJustDecodeBounds = false
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                } finally { connection.disconnect() }
            } }.getOrNull()
        }
        loading = false
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = I18n.t(LocalContext.current, "edit.download_cover_failed", "Image download failed"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
