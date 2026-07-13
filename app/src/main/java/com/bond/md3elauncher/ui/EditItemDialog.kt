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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@Composable
internal fun EditItemPage(
    target: EditTarget,
    scraperSettings: ScraperSettings,
    onBack: () -> Unit,
    onFooterTextChange: (String) -> Unit = {},
    onSave: (title: String, imageUriString: String?) -> Unit
) {
    var title by rememberSaveable(target.key) { mutableStateOf(target.currentTitle) }
    var selectedImageUri by rememberSaveable(target.key) { mutableStateOf<String?>(null) }
    var showScraper by rememberSaveable(target.key) { mutableStateOf(false) }

    LaunchedEffect(showScraper, scraperSettings) {
        onFooterTextChange(if (showScraper) compactSourceText(scraperSettings) else "编辑显示信息")
    }

    if (showScraper) {
        CoverScrapePage(
            target = target,
            queryTitle = title,
            scraperSettings = scraperSettings,
            onBack = { showScraper = false },
            onSelected = { localPath ->
                selectedImageUri = Uri.fromFile(File(localPath)).toString()
                showScraper = false
            }
        )
        return
    }

    EditInfoPage(
        target = target,
        title = title,
        selectedImageUri = selectedImageUri,
        onTitleChange = { title = it },
        onImageSelected = { selectedImageUri = it },
        onSearchCover = { showScraper = true },
        onBack = onBack,
        onSave = { onSave(title, selectedImageUri) },
        onRestoreName = { title = target.defaultTitle },
        onRemoveImage = { selectedImageUri = "__REMOVE__" }
    )
}

@Composable
private fun EditInfoPage(
    target: EditTarget,
    title: String,
    selectedImageUri: String?,
    onTitleChange: (String) -> Unit,
    onImageSelected: (String?) -> Unit,
    onSearchCover: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRestoreName: () -> Unit,
    onRemoveImage: () -> Unit
) {
    val context = LocalContext.current
    var showNameDialog by rememberSaveable(target.key) { mutableStateOf(false) }
    var draftTitle by rememberSaveable(target.key) { mutableStateOf(title) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) onImageSelected(uri.toString())
    }
    val previewBitmap = remember(target.currentImagePath, selectedImageUri) {
        runCatching {
            when {
                selectedImageUri == "__REMOVE__" -> null
                !selectedImageUri.isNullOrBlank() -> {
                    val uri = Uri.parse(selectedImageUri)
                    if (uri.scheme == "file") {
                        BitmapFactory.decodeFile(uri.path)?.asImageBitmap()
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    }
                }
                !target.currentImagePath.isNullOrBlank() -> BitmapFactory.decodeFile(target.currentImagePath)?.asImageBitmap()
                else -> null
            }
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Text(
                "编辑显示信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title.ifBlank { target.defaultTitle },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            if (previewBitmap != null) {
                TextButton(onClick = onRemoveImage) { Text("移除封面") }
                Spacer(Modifier.width(6.dp))
            }
            OutlinedButton(onClick = onBack) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onSave, enabled = title.isNotBlank()) { Text("保存") }
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(
                modifier = Modifier
                    .width(184.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(74.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(onClick = onSearchCover, modifier = Modifier.fillMaxWidth()) { Text("联网搜索封面") }
                FilledTonalButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("设备选择封面") }
                FilledTonalButton(
                    onClick = {
                        draftTitle = title.ifBlank { target.defaultTitle }
                        showNameDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("编辑显示名称") }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "封面建议使用竖版 3:4 图片，推荐 600×800 px；PNG/JPG 都可以，过大图片会自动适配显示。",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))
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
            title = { Text("编辑显示名称", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        label = { Text("显示名称") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    Text(
                        "名称过长会在顶部和列表中自动以 ... 省略显示。",
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
                ) { Text("确定") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            draftTitle = target.defaultTitle
                            onRestoreName()
                            showNameDialog = false
                        }
                    ) { Text("还原默认") }
                    TextButton(onClick = { showNameDialog = false }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun CoverScrapePage(
    target: EditTarget,
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
                errorText = "没有找到封面。可以换关键词，例如“星之卡比”或“Kirby and the Forgotten Land”。"
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
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("联网搜索封面", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(target.typeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSearchField = !showSearchField }) {
                Icon(Icons.Rounded.Search, contentDescription = "搜索关键词")
            }
            FilledTonalButton(onClick = { search() }, enabled = !isLoading) { Text(if (isLoading) "搜索中" else "搜索") }
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
                    Text(errorText ?: "没有封面候选", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    if (path != null) onSelected(path) else errorText = "下载封面失败"
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
                                "输入中文名 / 英文名 / 日文罗马音",
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
                Text("搜索")
            }
        }
    }
}

private fun compactSourceText(settings: ScraperSettings): String {
    val sources = buildList {
        add("Libretro")
        if (settings.steamGridDbApiKey.isNotBlank()) add("SteamGridDB")
        if (settings.theGamesDbApiKey.isNotBlank()) add("TheGamesDB")
    }
    val visible = sources.take(3).joinToString(" / ")
    val suffix = if (sources.size > 3) " / ..." else ""
    return "来源：$visible$suffix"
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
