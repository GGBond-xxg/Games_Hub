package com.bond.md3elauncher.system

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.InternalEmulators
import java.io.File

internal object LaunchPreflight {
    fun formatIssue(kind: PlatformKind, extension: String, internal: Boolean): String? = when {
        internal && extension.equals("7z", true) -> "launch.check.archive"
        internal && kind == PlatformKind.PS1 && extension.lowercase() !in setOf("chd", "pbp", "iso", "bin") -> "launch.check.disc"
        else -> null
    }

    fun coreName(kind: PlatformKind, gles3: Boolean): String? = when (kind) {
        PlatformKind.GBA, PlatformKind.GB -> "libmgba_libretro_android.so"
        PlatformKind.NES -> "libnestopia_libretro_android.so"
        PlatformKind.SFC -> "libsnes9x_libretro_android.so"
        PlatformKind.MD -> "libgenesis_plus_gx_libretro_android.so"
        PlatformKind.PS1 -> "libpcsx_rearmed_libretro_android.so"
        PlatformKind.N64 -> "libmupen64plus_next_gles${if (gles3) 3 else 2}_libretro_android.so"
        PlatformKind.ARCADE -> "libmame2003_plus_libretro_android.so"
        else -> null
    }

    /** Run on IO; a failed preflight never records a recent launch. */
    fun check(context: Context, game: GameItem, platform: PlatformConfig): String? {
        val internal = InternalEmulators.usesInternal(platform)
        formatIssue(platform.kind, game.extension, internal)?.let { return it }
        if (internal) {
            val gles3 = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x30000
            val core = coreName(platform.kind, gles3) ?: return "launch.check.core"
            if (!File(context.applicationInfo.nativeLibraryDir, core).isFile) return "launch.check.core"
        } else {
            val name = platform.emulatorPackage
            if (name.isNullOrBlank()) return "launch.check.emulator"
            val installed = runCatching { context.packageManager.getApplicationInfo(name, 0).enabled }.getOrDefault(false)
            if (!installed) return "launch.check.emulator"
        }
        val readable = runCatching { context.contentResolver.openInputStream(Uri.parse(game.uri))?.use { it.read() >= 0 } == true }.getOrDefault(false)
        return if (readable) null else "launch.check.file"
    }
}
