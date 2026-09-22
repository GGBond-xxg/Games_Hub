package com.bond.md3elauncher.io

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.psp.PspIsoReader
import com.bond.md3elauncher.emulator.n64.isLikelyN64Rom
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class RomScanner(private val context: Context) {
    private val pspReader = PspIsoReader(context)

    suspend fun scan(platform: PlatformConfig): List<GameItem> {
        val folderUri = platform.folderUri ?: return emptyList()
        val tree = Uri.parse(folderUri)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val allowed = platform.kind.extensions
        val result = linkedMapOf<String, GameItem>()

        fun addFile(uri: Uri, name: String) {
            if (name.isBlank()) return

            val ext = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            if (ext !in allowed) return

            val uriString = uri.toString()
            if (platform.kind == PlatformKind.N64 && ext !in setOf("zip", "7z")) {
                val header = run {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        val probe = ByteArray(516)
                        val size = input.read(probe).coerceAtLeast(0)
                        probe.copyOf(size)
                    }
                }
                if (!isLikelyN64Rom(header)) return
            }

            // PSP metadata is best-effort only.
            // If PARAM.SFO / ICON0 fails, the ROM still appears using the cleaned file name.
            val pspMeta = if (platform.kind == PlatformKind.PSP && ext == "iso") {
                runCatching { pspReader.read(uri) }.getOrNull()
            } else {
                null
            }

            val fallbackTitle = runCatching { cleanRomTitle(name) }
                .getOrDefault(name.substringBeforeLast('.', missingDelimiterValue = name))
                .ifBlank { name.substringBeforeLast('.', missingDelimiterValue = name) }

            result["${platform.id}:$uriString"] = GameItem(
                id = "${platform.id}:$uriString",
                platformId = platform.id,
                platformTitle = platform.kind.title,
                title = pspMeta?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                fileName = name,
                extension = ext.uppercase(Locale.ROOT),
                uri = uriString,
                serial = pspMeta?.discId,
                coverPath = pspMeta?.iconPath,
                backgroundPath = pspMeta?.backgroundPath
            )
        }

        suspend fun walk(dir: Uri, depth: Int = 0) {
            currentCoroutineContext().ensureActive()
            if (depth > 12) throw IOException("Directory nesting exceeds scan limit")
            context.contentResolver.query(dir, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null).use { info ->
                if (info == null || !info.moveToFirst() || info.getString(0) != DocumentsContract.Document.MIME_TYPE_DIR) {
                    throw IOException("ROM folder no longer exists")
                }
            }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(dir))
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            // DocumentFile.listFiles swallows provider errors and can return a partial/empty list.
            // Query directly so a failed scan never replaces the previous library.
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: throw IOException("Cannot read ROM folder")
            cursor.use {
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) || it.extras.containsKey(DocumentsContract.EXTRA_ERROR)) throw IOException("Folder is not ready")
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val child = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
                    if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(child, depth + 1)
                    else addFile(child, it.getString(1).orEmpty())
                }
            }
        }

        walk(root)
        return result.values.sortedBy { it.title.lowercase(Locale.ROOT) }
    }
}

internal fun cleanRomTitle(fileName: String): String {
    var title = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)

    title = decodePercentEscapes(title)
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    // No-Intro / ROM-set prefix: "2171 - Game Name" -> "Game Name"
    title = title.replace(Regex("^\\s*\\d{1,6}\\s*[-._]\\s*"), "")

    // Remove common tag blocks without risky bracket regex on Android.
    title = removeBetween(title, '[', ']')
    title = removeBetween(title, '【', '】')
    title = removeBetween(title, '(', ')')
    title = removeBetween(title, '（', '）')
    title = removeBetween(title, '{', '}')

    return title
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', '–', '—', '_', '.', ' ')
}

private fun removeBetween(input: String, start: Char, end: Char): String {
    val out = StringBuilder(input.length)
    var skipping = false
    input.forEach { c ->
        when {
            c == start -> {
                skipping = true
                out.append(' ')
            }
            c == end && skipping -> {
                skipping = false
                out.append(' ')
            }
            !skipping -> out.append(c)
        }
    }
    return out.toString()
}

private fun decodePercentEscapes(value: String): String =
    runCatching {
        URLDecoder.decode(
            value.replace("+", "%2B"),
            StandardCharsets.UTF_8.name()
        )
    }.getOrDefault(value)
