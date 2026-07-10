package com.bond.md3elauncher.emulator.psp
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

/**
 * Minimal PSP ISO reader.
 * Reads PSP_GAME/PARAM.SFO for the real game title and PSP_GAME/ICON0.PNG / PIC1.PNG for local artwork.
 * It intentionally supports plain ISO first. CSO/PBP/CHD can be added later with dedicated parsers.
 */
data class PspIsoMetadata(
    val title: String? = null,
    val discId: String? = null,
    val discVersion: String? = null,
    val iconPath: String? = null,
    val backgroundPath: String? = null
)

class PspIsoReader(private val context: Context) {
    fun read(uri: Uri): PspIsoMetadata? = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                val root = readRootRecord(channel) ?: return null
                val param = findPath(channel, root, listOf("PSP_GAME", "PARAM.SFO")) ?: return null
                val sfo = parseSfo(readFile(channel, param, 256 * 1024))
                val discId = sfo["DISC_ID"]?.trimToNull()
                val title = sfo["TITLE"]?.trimToNull()
                val version = sfo["DISC_VERSION"]?.trimToNull()
                val imageKey = discId ?: md5(uri.toString())

                val icon = findPath(channel, root, listOf("PSP_GAME", "ICON0.PNG"))
                    ?.let { extractPng(channel, it, imageKey, "icon") }
                val bg = findPath(channel, root, listOf("PSP_GAME", "PIC1.PNG"))
                    ?.let { extractPng(channel, it, imageKey, "bg") }

                PspIsoMetadata(
                    title = title,
                    discId = discId,
                    discVersion = version,
                    iconPath = icon,
                    backgroundPath = bg
                )
            }
        }
    }.getOrNull()

    private fun readRootRecord(channel: FileChannel): IsoRecord? {
        val pvd = readAt(channel, 16L * SECTOR_SIZE, SECTOR_SIZE)
        if (pvd.size < 190) return null
        val magic = pvd.copyOfRange(1, 6).toString(Charsets.US_ASCII)
        if (magic != "CD001") return null
        return parseRecord(pvd, 156)
    }

    private fun findPath(channel: FileChannel, root: IsoRecord, segments: List<String>): IsoRecord? {
        var current = root
        for (segment in segments) {
            val children = listDirectory(channel, current)
            current = children.firstOrNull { it.name.equals(segment, ignoreCase = true) } ?: return null
        }
        return current
    }

    private fun listDirectory(channel: FileChannel, dir: IsoRecord): List<IsoRecord> {
        if (!dir.isDirectory || dir.size <= 0) return emptyList()
        val maxDirectoryBytes = min(dir.size, 4 * 1024 * 1024)
        val bytes = readAt(channel, dir.extent * SECTOR_SIZE, maxDirectoryBytes)
        val result = mutableListOf<IsoRecord>()
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) {
                val nextSector = ((offset / SECTOR_SIZE) + 1) * SECTOR_SIZE
                if (nextSector <= offset) break
                offset = nextSector
                continue
            }
            if (offset + length > bytes.size) break
            parseRecord(bytes, offset)?.let { record ->
                if (record.name != "." && record.name != "..") result.add(record)
            }
            offset += length
        }
        return result
    }

    private fun parseRecord(bytes: ByteArray, offset: Int): IsoRecord? {
        if (offset < 0 || offset + 34 > bytes.size) return null
        val length = bytes[offset].toInt() and 0xFF
        if (length <= 0 || offset + length > bytes.size) return null
        val extent = u32le(bytes, offset + 2)
        val size = u32le(bytes, offset + 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val flags = bytes[offset + 25].toInt() and 0xFF
        val nameLength = bytes[offset + 32].toInt() and 0xFF
        if (offset + 33 + nameLength > bytes.size) return null
        val rawNameBytes = bytes.copyOfRange(offset + 33, offset + 33 + nameLength)
        val rawName = when {
            nameLength == 1 && rawNameBytes[0].toInt() == 0 -> "."
            nameLength == 1 && rawNameBytes[0].toInt() == 1 -> ".."
            else -> rawNameBytes.toString(Charsets.UTF_8)
        }
        val name = rawName.substringBefore(";").trim()
        return IsoRecord(name = name, extent = extent, size = size, isDirectory = flags and 0x02 != 0)
    }

    private fun readFile(channel: FileChannel, record: IsoRecord, maxBytes: Int): ByteArray {
        val size = record.size.coerceAtMost(maxBytes)
        return readAt(channel, record.extent * SECTOR_SIZE, size)
    }

    private fun readAt(channel: FileChannel, offset: Long, size: Int): ByteArray {
        if (size <= 0) return ByteArray(0)
        val buffer = java.nio.ByteBuffer.allocate(size)
        channel.position(offset)
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer)
            if (count <= 0) break
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun parseSfo(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 20) return emptyMap()
        val keyTableStart = u32le(bytes, 8).toInt()
        val dataTableStart = u32le(bytes, 12).toInt()
        val count = u32le(bytes, 16).toInt().coerceIn(0, 512)
        val result = linkedMapOf<String, String>()

        for (i in 0 until count) {
            val entry = 20 + i * 16
            if (entry + 16 > bytes.size) break
            val keyOffset = u16le(bytes, entry)
            val dataLength = u32le(bytes, entry + 4).toInt().coerceAtLeast(0)
            val dataOffset = u32le(bytes, entry + 12).toInt().coerceAtLeast(0)
            val key = readCString(bytes, keyTableStart + keyOffset)
            val dataStart = dataTableStart + dataOffset
            if (key.isBlank() || dataStart < 0 || dataStart >= bytes.size) continue
            val dataEnd = min(bytes.size, dataStart + dataLength)
            val value = bytes.copyOfRange(dataStart, dataEnd)
                .toString(Charsets.UTF_8)
                .trimEnd('\u0000')
                .trim()
            if (value.isNotBlank()) result[key] = value
        }
        return result
    }

    private fun readCString(bytes: ByteArray, start: Int): String {
        if (start < 0 || start >= bytes.size) return ""
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0) end++
        return bytes.copyOfRange(start, end).toString(Charsets.UTF_8)
    }

    private fun extractPng(channel: FileChannel, record: IsoRecord, key: String, suffix: String): String? {
        val bytes = readFile(channel, record, 2 * 1024 * 1024)
        if (bytes.size < 8) return null
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        if (!isPng) return null
        val dir = File(context.filesDir, "psp_meta").apply { mkdirs() }
        val file = File(dir, "${safeFileName(key)}_$suffix.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun safeFileName(value: String): String = value
        .uppercase(Locale.ROOT)
        .map { c -> if (c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '_' || c == '-') c else '_' }
        .joinToString("")
        .take(80)

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun u16le(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32le(bytes: ByteArray, offset: Int): Long {
        if (offset + 3 >= bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }

    private data class IsoRecord(
        val name: String,
        val extent: Long,
        val size: Int,
        val isDirectory: Boolean
    )

    private companion object {
        const val SECTOR_SIZE = 2048
    }
}
