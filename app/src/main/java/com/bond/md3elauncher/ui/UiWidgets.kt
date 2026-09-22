package com.bond.md3elauncher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.data.ItemOverride

@Composable
internal fun AppIcon(app: InstalledApp, size: Int) {
    val context = LocalContext.current
    val imageBitmap = asyncBitmap("icon:${app.packageName}") {
        runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(app.packageName)).asImageBitmap() }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        } else {
            Icon(Icons.Rounded.Apps, contentDescription = null)
        }
    }
}

@Composable
internal fun AppPreviewImage(
    app: InstalledApp,
    overridePath: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val bitmap = asyncBitmap("app:${app.packageName}:$overridePath") {
        loadBitmapFromPath(overridePath)
            ?: runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(app.packageName)).asImageBitmap() }.getOrNull()
    }
    PreviewBitmapOrIcon(
        bitmap = bitmap,
        modifier = modifier,
        contentScale = contentScale,
        fallback = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.fillMaxSize().padding(12.dp)) }
    )
}

@Composable
internal fun GameCover(
    game: GameItem,
    overridePath: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val coverBitmap = asyncBitmap("cover:${game.coverPath}:$overridePath") {
        loadBitmapFromPath(overridePath) ?: loadBitmapFromPath(game.coverPath)
    }
    PreviewBitmapOrIcon(
        bitmap = coverBitmap,
        modifier = modifier,
        contentScale = contentScale,
        fallback = {
            Icon(
                Icons.Rounded.SportsEsports,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(26.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    )
}

@Composable
private fun PreviewBitmapOrIcon(
    bitmap: ImageBitmap?,
    modifier: Modifier,
    contentScale: ContentScale,
    fallback: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            fallback()
        }
    }
}

internal fun itemTitle(overrides: Map<String, ItemOverride>, key: String, fallback: String): String =
    overrides[key]?.title?.takeIf { it.isNotBlank() } ?: fallback

internal fun itemPreviewImagePath(overrides: Map<String, ItemOverride>, key: String, fallback: String? = null): String? =
    overrides[key]?.previewImagePath?.takeIf { it.isNotBlank() } ?: fallback

internal fun itemGridImagePath(overrides: Map<String, ItemOverride>, key: String, fallback: String? = null): String? =
    overrides[key]?.gridImagePath?.takeIf { it.isNotBlank() } ?: fallback

internal fun platformDisplayName(title: String): String = when (title.lowercase()) {
    "switch" -> "NS"
    else -> title
}

private fun loadBitmapFromPath(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 768) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }.getOrNull()
}

private val imageCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}
private val imageDecoders = Semaphore(2)
private var imageGeneration by mutableIntStateOf(0)

internal fun clearLauncherImageCache() {
    imageCache.evictAll()
    imageGeneration++
}

@Composable
private fun asyncBitmap(key: String, load: () -> ImageBitmap?): ImageBitmap? {
    // New artwork uses a new filename; restore explicitly clears the shared cache.
    // Key the producer's state too, so recycled cards never briefly show another game.
    val cacheKey = "$imageGeneration:$key"
    return androidx.compose.runtime.key(cacheKey) {
        val bitmap by produceState<ImageBitmap?>(imageCache.get(cacheKey), cacheKey) {
            value = withContext(Dispatchers.IO) {
                imageDecoders.withPermit {
                    imageCache.get(cacheKey) ?: load()?.also { imageCache.put(cacheKey, it) }
                }
            }
        }
        bitmap
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
