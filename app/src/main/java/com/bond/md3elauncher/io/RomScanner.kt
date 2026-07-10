package com.bond.md3elauncher.io

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.psp.PspIsoReader
import java.util.Locale

class RomScanner(private val context: Context) {
    private val pspReader = PspIsoReader(context)

    fun scan(platform: PlatformConfig): List<GameItem> {
        val folderUri = platform.folderUri ?: return emptyList()
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) }
            .getOrNull() ?: return emptyList()
        val allowed = platform.kind.extensions
        val result = linkedMapOf<String, GameItem>()

        fun addFile(file: DocumentFile) {
            val name = runCatching { file.name.orEmpty() }.getOrDefault("")
            if (name.isBlank()) return

            val ext = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            if (ext !in allowed) return

            val uriString = runCatching { file.uri.toString() }.getOrNull() ?: return

            // PSP metadata is best-effort only.
            // If PARAM.SFO / ICON0 fails, the ROM still appears using the cleaned file name.
            val pspMeta = if (platform.kind == PlatformKind.PSP && ext == "iso") {
                runCatching { pspReader.read(file.uri) }.getOrNull()
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

        fun walk(dir: DocumentFile, depth: Int = 0) {
            if (depth > 12) return
            val files = runCatching { dir.listFiles().toList() }.getOrDefault(emptyList())
            files.forEach { file ->
                val isDirectory = runCatching { file.isDirectory }.getOrDefault(false)
                val isFile = runCatching { file.isFile }.getOrDefault(false)
                when {
                    isDirectory -> walk(file, depth + 1)
                    isFile -> runCatching { addFile(file) }
                }
            }
        }

        walk(root)
        return result.values.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private fun cleanRomTitle(fileName: String): String {
        var title = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)

        title = Uri.decode(title)
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
}
