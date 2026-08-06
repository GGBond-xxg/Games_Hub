package com.bond.md3elauncher.emulator.n64

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal enum class N64RomByteOrder {
    BIG_ENDIAN,
    BYTE_SWAPPED,
    LITTLE_ENDIAN
}

internal data class N64RomFormat(
    val byteOrder: N64RomByteOrder,
    val headerOffset: Int
)

internal fun detectN64RomFormat(header: ByteArray): N64RomFormat? {
    for (offset in intArrayOf(0, N64_COPIER_HEADER_SIZE)) {
        if (header.size < offset + 4) continue
        val order = when {
            header.matchesAt(offset, 0x80, 0x37, 0x12, 0x40) -> N64RomByteOrder.BIG_ENDIAN
            header.matchesAt(offset, 0x37, 0x80, 0x40, 0x12) -> N64RomByteOrder.BYTE_SWAPPED
            header.matchesAt(offset, 0x40, 0x12, 0x37, 0x80) -> N64RomByteOrder.LITTLE_ENDIAN
            else -> null
        }
        if (order != null) return N64RomFormat(order, offset)
    }
    return null
}

internal fun isLikelyN64Rom(header: ByteArray): Boolean = detectN64RomFormat(header) != null

internal fun normalizeN64Rom(source: File, destination: File): N64RomFormat {
    val header = ByteArray(N64_HEADER_PROBE_SIZE)
    val headerSize = FileInputStream(source).use { it.read(header) }.coerceAtLeast(0)
    val format = detectN64RomFormat(header.copyOf(headerSize))
        ?: throw IllegalArgumentException("File does not contain a recognized N64 cartridge header")

    destination.parentFile?.mkdirs()
    FileInputStream(source).use { input ->
        var remainingHeader = format.headerOffset
        while (remainingHeader > 0) {
            val skipped = input.skip(remainingHeader.toLong()).toInt()
            if (skipped <= 0) {
                if (input.read() < 0) throw IllegalArgumentException("N64 copier header is incomplete")
                remainingHeader--
            } else {
                remainingHeader -= skipped
            }
        }

        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(N64_NORMALIZE_BUFFER_SIZE)
            var carry = 0
            while (true) {
                val read = input.read(buffer, carry, buffer.size - carry)
                if (read < 0) {
                    if (carry > 0) output.write(buffer, 0, carry)
                    break
                }
                val total = carry + read
                val complete = total - (total % 4)
                normalizeWordsInPlace(buffer, complete, format.byteOrder)
                output.write(buffer, 0, complete)
                carry = total - complete
                if (carry > 0) {
                    buffer.copyInto(buffer, destinationOffset = 0, startIndex = complete, endIndex = total)
                }
            }
        }
    }
    return format
}

private fun normalizeWordsInPlace(buffer: ByteArray, length: Int, order: N64RomByteOrder) {
    if (order == N64RomByteOrder.BIG_ENDIAN) return
    var index = 0
    while (index < length) {
        if (order == N64RomByteOrder.BYTE_SWAPPED) {
            buffer.swap(index, index + 1)
            buffer.swap(index + 2, index + 3)
        } else {
            buffer.swap(index, index + 3)
            buffer.swap(index + 1, index + 2)
        }
        index += 4
    }
}

private fun ByteArray.matchesAt(offset: Int, a: Int, b: Int, c: Int, d: Int): Boolean =
    this[offset].toInt() and 0xFF == a &&
        this[offset + 1].toInt() and 0xFF == b &&
        this[offset + 2].toInt() and 0xFF == c &&
        this[offset + 3].toInt() and 0xFF == d

private fun ByteArray.swap(first: Int, second: Int) {
    val value = this[first]
    this[first] = this[second]
    this[second] = value
}

private const val N64_COPIER_HEADER_SIZE = 512
private const val N64_HEADER_PROBE_SIZE = N64_COPIER_HEADER_SIZE + 4
private const val N64_NORMALIZE_BUFFER_SIZE = 64 * 1024
