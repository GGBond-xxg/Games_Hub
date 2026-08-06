package com.bond.md3elauncher.emulator

import com.bond.md3elauncher.emulator.n64.N64RomByteOrder
import com.bond.md3elauncher.emulator.n64.detectN64RomFormat
import com.bond.md3elauncher.emulator.n64.isLikelyN64Rom
import com.bond.md3elauncher.emulator.n64.normalizeN64Rom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class N64RomSupportTest {
    @Test
    fun detectsAllThreeN64ByteOrdersEvenWhenExtensionIsWrong() {
        assertEquals(N64RomByteOrder.BIG_ENDIAN, detectN64RomFormat(bytes(0x80, 0x37, 0x12, 0x40))?.byteOrder)
        assertEquals(N64RomByteOrder.BYTE_SWAPPED, detectN64RomFormat(bytes(0x37, 0x80, 0x40, 0x12))?.byteOrder)
        assertEquals(N64RomByteOrder.LITTLE_ENDIAN, detectN64RomFormat(bytes(0x40, 0x12, 0x37, 0x80))?.byteOrder)
    }

    @Test
    fun rejectsNonN64BinHeader() {
        assertFalse(isLikelyN64Rom(bytes(0x00, 0xFF, 0xFF, 0xFF)))
    }

    @Test
    fun acceptsAndRemovesCopierHeader() {
        val header = ByteArray(516)
        byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40).copyInto(header, destinationOffset = 512)
        val format = detectN64RomFormat(header)
        assertEquals(512, format?.headerOffset)
    }

    @Test
    fun normalizesByteSwappedContentToBigEndian() {
        val dir = createTempDirectory(prefix = "n64-normalize-").toFile()
        try {
            val source = File(dir, "patched.n64").apply {
                writeBytes(bytes(0x37, 0x80, 0x40, 0x12, 0x22, 0x11, 0x44, 0x33))
            }
            val output = File(dir, "normalized.z64")
            normalizeN64Rom(source, output)
            assertArrayEquals(bytes(0x80, 0x37, 0x12, 0x40, 0x11, 0x22, 0x33, 0x44), output.readBytes())
            assertTrue(output.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index -> values[index].toByte() }
}
