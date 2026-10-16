package com.bond.md3elauncher.system

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Capture launch requests without starting/installing RetroArch or touching the user's ROM library. */
class RetroArchLaunchContractTest {
    @Test fun mainLaunchUsesTheSelectedPackagesDataDirectory() = verifyLaunch(rejectMain = false)

    @Test fun viewFallbackKeepsCoreRomAndTaskFlags() = verifyLaunch(rejectMain = true)

    private fun verifyLaunch(rejectMain: Boolean) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "qa-retroarch-${UUID.randomUUID()}").apply { mkdirs() }
        val requests = mutableListOf<Intent>()
        val context = object : ContextWrapper(target) {
            override fun getCacheDir() = directory
            override fun getExternalCacheDir() = directory
            override fun startActivity(intent: Intent) {
                requests += Intent(intent)
                if (rejectMain && intent.action == Intent.ACTION_MAIN) throw ActivityNotFoundException("QA fallback")
            }
        }
        // The installed test target supplies real PackageManager metadata. Invoke the existing
        // private route because the public dispatcher intentionally requires a RetroArch package name.
        val route = ExternalLauncher::class.java.getDeclaredMethod(
            "launchWithRetroArch", GameItem::class.java, PlatformKind::class.java, String::class.java
        ).apply { isAccessible = true }
        val expectedCores = mapOf(
            PlatformKind.PSP to "ppsspp", PlatformKind.GBA to "mgba", PlatformKind.GB to "gambatte",
            PlatformKind.NES to "fceumm", PlatformKind.SFC to "snes9x", PlatformKind.MD to "genesis_plus_gx",
            PlatformKind.SMS to "genesis_plus_gx", PlatformKind.GG to "genesis_plus_gx",
            PlatformKind.SG1000 to "genesis_plus_gx", PlatformKind.PS1 to "pcsx_rearmed",
            PlatformKind.N64 to "mupen64plus_next_gles3", PlatformKind.ARCADE to "mame2003_plus"
        )
        val oldPolicy = StrictMode.getVmPolicy()
        try {
            val source = File(directory, "probe.rom").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            for ((platform, core) in expectedCores) {
                requests.clear()
                val game = GameItem("qa", platform.name, platform.name, "QA", source.name, "rom", Uri.fromFile(source).toString())
                assertEquals(true, route.invoke(ExternalLauncher(context), game, platform, target.packageName))
                assertEquals(if (rejectMain) 2 else 1, requests.size)
                val copiedRom = File(directory, "rom_launch/probe.rom")
                assertArrayEquals(source.readBytes(), copiedRom.readBytes())
                requests.forEach { intent ->
                    assertEquals(target.packageName, intent.component?.packageName)
                    assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", intent.component?.className)
                    assertEquals(File(target.applicationInfo.dataDir, "cores/${core}_libretro_android.so").absolutePath,
                        intent.getStringExtra("LIBRETRO"))
                    assertEquals(copiedRom.absolutePath, intent.getStringExtra("ROM"))
                    assertEquals("/storage/emulated/0/Android/data/${target.packageName}/files/retroarch.cfg",
                        intent.getStringExtra("CONFIGFILE"))
                    assertEquals("", intent.getStringExtra("QUITFOCUS"))
                    assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
                }
                assertEquals(Intent.ACTION_MAIN, requests.first().action)
                if (rejectMain) {
                    assertEquals(Intent.ACTION_VIEW, requests.last().action)
                    assertEquals(Uri.fromFile(copiedRom), requests.last().data)
                    assertEquals("application/octet-stream", requests.last().type)
                }
            }
        } finally {
            StrictMode.setVmPolicy(oldPolicy)
            directory.deleteRecursively()
        }
    }
}
