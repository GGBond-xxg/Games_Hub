package com.bond.md3elauncher.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExternalLauncher(private val context: Context) {
    fun launchAndroidApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "找不到这个 App", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchGame(game: GameItem, platform: PlatformConfig) {
        val emulatorPackage = platform.emulatorPackage
        if (emulatorPackage.isNullOrBlank()) {
            Toast.makeText(context, "请先给 ${platform.kind.title} 选择模拟器 App", Toast.LENGTH_SHORT).show()
            return
        }

        val launched = when {
            platform.kind == PlatformKind.GBA && emulatorPackage.isMyBoyPackage() -> launchGbaWithMyBoy(game, emulatorPackage)
            emulatorPackage.isRetroArchPackage() -> launchWithRetroArch(game, platform.kind, emulatorPackage)
            platform.kind == PlatformKind.PSP && emulatorPackage.isPspFamilyPackage() -> launchWithPspFamily(game, emulatorPackage)
            platform.kind == PlatformKind.GBA && emulatorPackage.isGbaFamilyPackage() -> launchWithGbaFamily(game, emulatorPackage)
            else -> launchWithGenericView(
                game = game,
                emulatorPackage = emulatorPackage,
                includeCacheUris = platform.kind == PlatformKind.GBA,
                extraBuilder = { uri -> addCommonRomExtras(uri, game, platform.kind) }
            )
        }

        if (!launched) {
            openEmulatorOnly(emulatorPackage)
        }
    }

    private fun launchWithGenericView(
        game: GameItem,
        emulatorPackage: String,
        includeCacheUris: Boolean,
        extraBuilder: (Intent.(Uri) -> Unit)? = null
    ): Boolean {
        val uriCandidates = romUriCandidates(game, includeCacheUris = includeCacheUris)
        val candidates = buildList {
            uriCandidates.forEach { uri ->
                mimeTypesForGame(game).forEach { mime ->
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            setPackage(emulatorPackage)
                            extraBuilder?.invoke(this, uri)
                            addRomFlags(uri)
                        }
                    )
                }
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        setPackage(emulatorPackage)
                        extraBuilder?.invoke(this, uri)
                        addRomFlags(uri)
                    }
                )
                add(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeTypeForGame(game)
                        setPackage(emulatorPackage)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        extraBuilder?.invoke(this, uri)
                        addRomFlags(uri)
                    }
                )
            }
        }

        candidates.forEach { intent -> if (tryStartIntent(intent)) return true }
        candidates.forEach { baseIntent ->
            queryExplicitRomIntents(baseIntent, emulatorPackage).forEach { explicitIntent ->
                if (tryStartIntent(explicitIntent)) return true
            }
        }
        return false
    }

    private fun launchWithPspFamily(game: GameItem, emulatorPackage: String): Boolean {
        // PSP ROMs are often very large, so avoid copying them to cache by default.
        return launchWithGenericView(
            game = game,
            emulatorPackage = emulatorPackage,
            includeCacheUris = false,
            extraBuilder = { uri -> addCommonRomExtras(uri, game, PlatformKind.PSP) }
        )
    }

    private fun launchWithGbaFamily(game: GameItem, emulatorPackage: String): Boolean {
        // Most GBA emulators accept either a granted content:// uri or a temporary file uri.
        return launchWithGenericView(
            game = game,
            emulatorPackage = emulatorPackage,
            includeCacheUris = true,
            extraBuilder = { uri -> addCommonRomExtras(uri, game, PlatformKind.GBA) }
        )
    }

    private fun Intent.addCommonRomExtras(uri: Uri, game: GameItem, platformKind: PlatformKind) {
        val value = uri.toString()
        val pathValue = uri.path.orEmpty()
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, value)
        putExtra("uri", value)
        putExtra("Uri", value)
        putExtra("URI", value)
        putExtra("rom", value)
        putExtra("ROM", value)
        putExtra("romUri", value)
        putExtra("rom_uri", value)
        putExtra("path", value)
        putExtra("file", value)
        putExtra("filepath", value)
        putExtra("file_path", value)
        if (pathValue.isNotBlank()) {
            putExtra("rawPath", pathValue)
            putExtra("romPath", pathValue)
            putExtra("rom_path", pathValue)
            putExtra("absolutePath", pathValue)
        }
        putExtra("filename", game.fileName)
        putExtra("fileName", game.fileName)
        putExtra("game", game.title)
        putExtra("title", game.title)
        putExtra("platform", platformKind.title)
        putExtra("system", platformKind.title)
        putExtra("systemId", platformKind.title.lowercase(Locale.ROOT))
    }

    private fun launchWithRetroArch(game: GameItem, platformKind: PlatformKind, emulatorPackage: String): Boolean {
        val cachedFile = copyRomToLaunchCache(game) ?: return launchWithGenericView(game, emulatorPackage, includeCacheUris = false)
        val coreNames = when (platformKind) {
            PlatformKind.PSP -> listOf("ppsspp_libretro_android.so")
            PlatformKind.GBA -> listOf("mgba_libretro_android.so", "gpsp_libretro_android.so", "vba_next_libretro_android.so", "vba_m_libretro_android.so")
            PlatformKind.SWITCH -> emptyList()
        }
        if (coreNames.isEmpty()) return launchWithGenericView(game, emulatorPackage, includeCacheUris = false)

        val retroActivity = ComponentName(emulatorPackage, "com.retroarch.browser.retroactivity.RetroActivityFuture")
        val candidates = buildList {
            coreNames.forEach { core ->
                add(
                    Intent(Intent.ACTION_MAIN).apply {
                        component = retroActivity
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("ROM", cachedFile.absolutePath)
                        putExtra("LIBRETRO", "/data/data/$emulatorPackage/cores/$core")
                        putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$emulatorPackage/files/retroarch.cfg")
                        putExtra("QUITFOCUS", "")
                    }
                )
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        component = retroActivity
                        setDataAndType(Uri.fromFile(cachedFile), mimeTypeForGame(game))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("ROM", cachedFile.absolutePath)
                        putExtra("LIBRETRO", "/data/data/$emulatorPackage/cores/$core")
                        putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$emulatorPackage/files/retroarch.cfg")
                        putExtra("QUITFOCUS", "")
                    }
                )
            }
        }

        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        candidates.forEach { intent -> if (tryStartIntent(intent)) return true }
        return launchWithGenericView(game, emulatorPackage, includeCacheUris = true)
    }

    private fun launchGbaWithMyBoy(game: GameItem, emulatorPackage: String): Boolean {
        val originalUri = Uri.parse(game.uri)
        val cachedFile = copyRomToLaunchCache(game)
        val cachedContentUri = cachedFile?.let { file ->
            runCatching {
                FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            }.getOrNull()
        }
        val cachedFileUri = cachedFile?.let { file ->
            runCatching {
                StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                Uri.fromFile(file)
            }.getOrNull()
        }

        val candidates = buildList {
            addAll(myBoyEmulatorActivityIntents(originalUri, game, emulatorPackage))
            addAll(myBoyLaunchActivityIntents(originalUri, game, emulatorPackage))
            addAll(myBoyViewIntents(originalUri, game, emulatorPackage))

            cachedContentUri?.let { uri ->
                addAll(myBoyEmulatorActivityIntents(uri, game, emulatorPackage))
                addAll(myBoyLaunchActivityIntents(uri, game, emulatorPackage))
                addAll(myBoyViewIntents(uri, game, emulatorPackage))
            }

            cachedFileUri?.let { uri ->
                addAll(myBoyEmulatorActivityIntents(uri, game, emulatorPackage))
                addAll(myBoyLaunchActivityIntents(uri, game, emulatorPackage))
                addAll(myBoyViewIntents(uri, game, emulatorPackage))
            }
        }

        candidates.forEach { intent -> if (tryStartIntent(intent)) return true }
        candidates.forEach { baseIntent ->
            queryExplicitRomIntents(baseIntent, emulatorPackage).forEach { explicitIntent ->
                if (tryStartIntent(explicitIntent)) return true
            }
        }

        openEmulatorOnly(emulatorPackage)
        return true
    }

    private fun myBoyEmulatorActivityIntents(uri: Uri, game: GameItem, emulatorPackage: String): List<Intent> {
        val component = ComponentName(emulatorPackage, "com.fastemulator.gba.EmulatorActivity")
        val mimeTypes = linkedSetOf(
            mimeTypeForGame(game),
            "application/x-gameboy-advance-rom",
            "application/x-gba-rom",
            "application/octet-stream"
        )
        return buildList {
            add(
                Intent(Intent.ACTION_VIEW).apply {
                    this.component = component
                    data = uri
                    addMyBoyExtras(uri, game)
                    addRomFlags(uri)
                }
            )
            mimeTypes.forEach { mime ->
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        this.component = component
                        setDataAndType(uri, mime)
                        addMyBoyExtras(uri, game)
                        addRomFlags(uri)
                    }
                )
            }
        }
    }

    private fun myBoyLaunchActivityIntents(uri: Uri, game: GameItem, emulatorPackage: String): List<Intent> {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(emulatorPackage) ?: return emptyList()
        val launchComponent = launchIntent.component
        val mimeTypes = linkedSetOf(
            mimeTypeForGame(game),
            "application/x-gameboy-advance-rom",
            "application/x-gba-rom",
            "application/octet-stream"
        )
        return buildList {
            mimeTypes.forEach { mime ->
                add(
                    Intent(launchIntent).apply {
                        action = Intent.ACTION_VIEW
                        setDataAndType(uri, mime)
                        launchComponent?.let { component = it }
                        setPackage(emulatorPackage)
                        addMyBoyExtras(uri, game)
                        addRomFlags(uri)
                    }
                )
            }
            add(
                Intent(launchIntent).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                    launchComponent?.let { component = it }
                    setPackage(emulatorPackage)
                    addMyBoyExtras(uri, game)
                    addRomFlags(uri)
                }
            )
        }
    }

    private fun myBoyViewIntents(uri: Uri, game: GameItem, emulatorPackage: String): List<Intent> {
        val mimeTypes = linkedSetOf(
            mimeTypeForGame(game),
            "application/x-gameboy-advance-rom",
            "application/x-gba-rom",
            "application/octet-stream"
        )
        return buildList {
            mimeTypes.forEach { mime ->
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        setPackage(emulatorPackage)
                        addMyBoyExtras(uri, game)
                        addRomFlags(uri)
                    }
                )
            }
            add(
                Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    setPackage(emulatorPackage)
                    addMyBoyExtras(uri, game)
                    addRomFlags(uri)
                }
            )
            add(
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeTypeForGame(game)
                    setPackage(emulatorPackage)
                    addMyBoyExtras(uri, game)
                    addRomFlags(uri)
                }
            )
        }
    }

    private fun Intent.addMyBoyExtras(uri: Uri, game: GameItem) {
        val value = uri.toString()
        val pathValue = uri.path.orEmpty()
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, value)
        putExtra("uri", value)
        putExtra("Uri", value)
        putExtra("URI", value)
        putExtra("rom", value)
        putExtra("ROM", value)
        putExtra("romUri", value)
        putExtra("rom_uri", value)
        putExtra("path", value)
        putExtra("file", value)
        putExtra("filepath", value)
        putExtra("file_path", value)
        if (pathValue.isNotBlank()) {
            putExtra("rawPath", pathValue)
            putExtra("romPath", pathValue)
            putExtra("rom_path", pathValue)
            putExtra("absolutePath", pathValue)
        }
        putExtra("filename", game.fileName)
        putExtra("fileName", game.fileName)
        putExtra("game", game.title)
    }

    private fun romUriCandidates(game: GameItem, includeCacheUris: Boolean): List<Uri> {
        val originalUri = Uri.parse(game.uri)
        if (!includeCacheUris) return listOf(originalUri)

        val cachedFile = copyRomToLaunchCache(game)
        val cachedContentUri = cachedFile?.let { file ->
            runCatching { FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file) }.getOrNull()
        }
        val cachedFileUri = cachedFile?.let { file ->
            runCatching {
                StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                Uri.fromFile(file)
            }.getOrNull()
        }
        return listOfNotNull(originalUri, cachedContentUri, cachedFileUri).distinctBy { it.toString() }
    }

    private fun Intent.addRomFlags(uri: Uri) {
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "rom", uri)
    }

    private fun tryStartIntent(intent: Intent): Boolean {
        val targetPackage = intent.`package` ?: intent.component?.packageName
        if (!targetPackage.isNullOrBlank()) {
            intent.data?.let { uri ->
                runCatching { context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let { uri ->
                        runCatching { context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                }
            }
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun queryExplicitRomIntents(baseIntent: Intent, emulatorPackage: String): List<Intent> {
        val pm = context.packageManager
        val matches = runCatching { pm.queryIntentActivities(baseIntent, 0) }.getOrDefault(emptyList())
        return matches
            .filter { it.activityInfo?.packageName == emulatorPackage }
            .mapNotNull { info ->
                val activityName = info.activityInfo?.name ?: return@mapNotNull null
                Intent(baseIntent).apply {
                    component = ComponentName(emulatorPackage, activityName)
                    setPackage(null)
                }
            }
    }

    private fun copyRomToLaunchCache(game: GameItem): File? {
        val sourceUri = Uri.parse(game.uri)
        val extension = game.extension.lowercase(Locale.ROOT).ifBlank { "rom" }
        val safeName = game.fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(90)
            .ifBlank { "launch_rom.$extension" }
            .let { name -> if (name.contains('.')) name else "$name.$extension" }

        val dir = File(context.externalCacheDir ?: context.cacheDir, "rom_launch")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, safeName)

        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(outFile, false).use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            outFile
        }.getOrNull()
    }

    private fun mimeTypeForGame(game: GameItem): String = mimeTypesForGame(game).first()

    private fun mimeTypesForGame(game: GameItem): List<String> = when (game.extension.lowercase(Locale.ROOT)) {
        "gba" -> listOf("application/x-gameboy-advance-rom", "application/x-gba-rom", "application/octet-stream")
        "zip" -> listOf("application/zip", "application/octet-stream")
        "7z" -> listOf("application/x-7z-compressed", "application/octet-stream")
        "iso" -> listOf("application/x-iso9660-image", "application/octet-stream")
        "cso" -> listOf("application/x-cso", "application/octet-stream")
        "pbp" -> listOf("application/x-pbp", "application/octet-stream")
        "chd" -> listOf("application/x-chd", "application/octet-stream")
        else -> listOf("application/octet-stream")
    }.distinct()

    private fun String.isMyBoyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.fastemulator.gba" || value.contains("fastemulator") || value.contains("myboy")
    }

    private fun String.isPspFamilyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value.contains("ppsspp") ||
            value == "com.emultech.rocketpsp" ||
            value == "com.emultech.enjoypsp" ||
            value == "kr.co.iefriends.mypsp" ||
            value.contains("rocketpsp") ||
            value.contains("enjoypsp") ||
            value.contains("mypsp")
    }

    private fun String.isGbaFamilyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return isMyBoyPackage() ||
            value.contains("pizzaboy") ||
            value.contains("johnemulators") && value.contains("gba") ||
            value.contains("johngba") ||
            value == "com.explusalpha.gbaemu" ||
            value.contains("gbaemu") ||
            value.contains("nostalgiaemulators") && value.contains("gba") ||
            value.contains("nostalgia") && value.contains("gba") ||
            value.contains("mgba")
    }

    private fun String.isRetroArchPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.retroarch" || value.startsWith("com.retroarch.") || value.contains("retroarch")
    }

    private fun openEmulatorOnly(emulatorPackage: String, message: String? = null) {
        val fallback = context.packageManager.getLaunchIntentForPackage(emulatorPackage)
        if (fallback != null) {
            context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(
                context,
                message ?: "已打开模拟器；如果没有直接进入游戏，需要继续适配该模拟器的专用启动参数",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "无法打开所选模拟器", Toast.LENGTH_SHORT).show()
        }
    }

    fun openHomeSettings() {
        val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(homeIntent)
        } catch (_: ActivityNotFoundException) {
            val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(defaultAppsIntent) }
                .onFailure { Toast.makeText(context, "请在系统设置里手动选择默认桌面 App", Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.bond.md3elauncher.fileprovider"
    }
}
