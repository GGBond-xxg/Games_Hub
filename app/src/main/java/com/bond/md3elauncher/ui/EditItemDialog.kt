package com.bond.md3elauncher.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bond.md3elauncher.data.CoverCandidate
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.system.CoverScraper
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
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = I18n.t(context, "common.back", "返回")
                    )
                }
                Text(
                    I18n.t(context, "edit.title", "编辑显示信息"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        draftTitle = title.ifBlank { target.defaultTitle }
                        showNameDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
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
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.height(38.dp),
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
                Button(
                    onClick = onSave,
                    enabled = title.isNotBlank(),
                    modifier = Modifier.height(38.dp),
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

        AlertDialog(
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
                    OutlinedTextField(
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
                TextButton(
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
                    TextButton(
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
                    TextButton(onClick = { showNameDialog = false }) {
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
    Surface(
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
                    IconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
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
private fun EditArtworkActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accessibilityLabel: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(13.dp)
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (filled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (filled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = accessibilityLabel,
                modifier = Modifier.size(18.dp),
                tint = if (filled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
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
        val uri = Uri.parse(source)
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
    var showSearchField by rememberSaveable(target.key) { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var debugText by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    val scraper = remember { CoverScraper(context) }

    fun search() {
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
                        settings = scraperSettings
                    )
                }
            }
            candidates = result.getOrDefault(emptyList())
            debugText = scraper.lastReport
            errorText = result.exceptionOrNull()?.message
            if (candidates.isEmpty() && errorText == null) {
                errorText = I18n.t(context, "edit.no_cover_found", "没有找到封面。可以换关键词，例如“星之卡比”或“Kirby and the Forgotten Land”。")
            }
            isLoading = false
        }
    }

    LaunchedEffect(target.key) { search() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = I18n.t(context, "common.back", "返回")) }
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
            IconButton(onClick = { showSearchField = !showSearchField }) {
                Icon(Icons.Rounded.Search, contentDescription = I18n.t(context, "common.search", "搜索"))
            }
            FilledTonalButton(onClick = { search() }, enabled = !isLoading) { Text(if (isLoading) I18n.t(context, "common.searching", "搜索中") else I18n.t(context, "common.search", "搜索"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        if (showSearchField) {
            CompactSearchField(
                value = manualQuery,
                onValueChange = { manualQuery = it },
                onSearch = { search() }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (candidates.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(errorText ?: I18n.t(context, "edit.no_cover_candidate", "没有封面候选"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!debugText.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            debugText.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 7,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(candidates, key = { it.imageUrl }) { candidate ->
                        CoverCandidateCard(
                            candidate = candidate,
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val path = withContext(Dispatchers.IO) { scraper.downloadCandidate(candidate, target.key) }
                                    isLoading = false
                                    if (path != null) onSelected(path) else errorText = I18n.t(context, "edit.download_cover_failed", "下载封面失败")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                I18n.t(context, "edit.search_placeholder", "输入中文名 / 英文名 / 日文罗马音"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = onSearch, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                Text(I18n.t(context, "common.search", "搜索"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun compactSourceText(context: android.content.Context, settings: ScraperSettings): String {
    val sources = buildList {
        add("Libretro")
        if (settings.steamGridDbApiKey.isNotBlank()) add("SteamGridDB")
        if (settings.theGamesDbApiKey.isNotBlank()) add("TheGamesDB")
    }
    val visible = sources.take(3).joinToString(" / ")
    val suffix = if (sources.size > 3) " / ..." else ""
    return I18n.t(context, "edit.source_prefix", "来源：{sources}", "sources" to (visible + suffix))
}

@Composable
private fun CoverCandidateCard(candidate: CoverCandidate, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .width(106.dp)
            .height(164.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteCandidateImage(candidate.imageUrl, Modifier.width(88.dp).height(126.dp))
            Spacer(Modifier.height(4.dp))
            Text(candidate.source, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RemoteCandidateImage(url: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { input -> BitmapFactory.decodeStream(input)?.asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        }
    }
}
