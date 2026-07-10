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
import androidx.compose.runtime.remember
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
import java.io.File

@Composable
internal fun AppIcon(app: InstalledApp, size: Int) {
    val context = LocalContext.current
    val imageBitmap = remember(app.packageName) {
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
internal fun AppPreviewImage(app: InstalledApp, overridePath: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val overrideVersion = imagePathVersion(overridePath)
    val bitmap = remember(app.packageName, overridePath, overrideVersion) {
        loadBitmapFromPath(overridePath)
            ?: runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(app.packageName)).asImageBitmap() }.getOrNull()
    }
    PreviewBitmapOrIcon(
        bitmap = bitmap,
        modifier = modifier,
        fallback = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.fillMaxSize().padding(12.dp)) }
    )
}

@Composable
internal fun GameCover(game: GameItem, overridePath: String? = null, modifier: Modifier = Modifier) {
    val overrideVersion = imagePathVersion(overridePath)
    val coverVersion = if (overridePath.isNullOrBlank()) imagePathVersion(game.coverPath) else 0L
    val coverBitmap = remember(game.coverPath, overridePath, overrideVersion, coverVersion) {
        loadBitmapFromPath(overridePath) ?: loadBitmapFromPath(game.coverPath)
    }
    PreviewBitmapOrIcon(
        bitmap = coverBitmap,
        modifier = modifier,
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
                contentScale = ContentScale.Fit
            )
        } else {
            fallback()
        }
    }
}

internal fun itemTitle(overrides: Map<String, ItemOverride>, key: String, fallback: String): String =
    overrides[key]?.title?.takeIf { it.isNotBlank() } ?: fallback

internal fun itemImagePath(overrides: Map<String, ItemOverride>, key: String, fallback: String? = null): String? =
    overrides[key]?.imagePath?.takeIf { it.isNotBlank() } ?: fallback

internal fun platformDisplayName(title: String): String = when (title.lowercase()) {
    "switch" -> "NS"
    else -> title
}

private fun loadBitmapFromPath(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}

private fun imagePathVersion(path: String?): Long {
    if (path.isNullOrBlank()) return 0L
    return runCatching {
        val file = File(path)
        file.lastModified().takeIf { it > 0L } ?: file.length()
    }.getOrDefault(0L)
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
