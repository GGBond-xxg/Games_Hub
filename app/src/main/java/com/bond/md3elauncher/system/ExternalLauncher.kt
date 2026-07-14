package com.bond.md3elauncher.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import com.bond.md3elauncher.i18n.I18n
import androidx.core.content.FileProvider
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.fc.FcExternalEmulatorProfiles
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExternalLauncher(private val context: Context) {
    fun launchAndroidApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, I18n.t(context, "toast.app_not_found", "找不到这个 App"), Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchGame(game: GameItem, platform: PlatformConfig) {
        val emulatorPackage = platform.emulatorPackage
        if (emulatorPackage.isNullOrBlank()) {
            Toast.makeText(context, I18n.t(context, "toast.select_emulator_first", "请先给 {platform} 选择模拟器 App", "platform" to platform.kind.title), Toast.LENGTH_SHORT).show()
            return
        }

        val launched = when {
            platform.kind == PlatformKind.GBA && emulatorPackage.isMyBoyPackage() -> launchGbaWithMyBoy(game, emulatorPackage)
            platform.kind == PlatformKind.GB && emulatorPackage.isMyOldBoyPackage() -> launchGbWithMyOldBoy(game, emulatorPackage)
            emulatorPackage.isRetroArchPackage() -> launchWithRetroArch(game, platform.kind, emulatorPackage)
            platform.kind == PlatformKind.PSP && emulatorPackage.isPspFamilyPackage() -> launchWithPspFamily(game, emulatorPackage)
            (platform.kind == PlatformKind.GBA || platform.kind == PlatformKind.GB) && emulatorPackage.isGbaFamilyPackage() -> launchWithGbaFamily(game, emulatorPackage, platform.kind)
            platform.kind == PlatformKind.NES && emulatorPackage.isNesFamilyPackage() -> launchWithNesFamily(game, emulatorPackage)
            platform.kind == PlatformKind.SFC && emulatorPackage.isSfcFamilyPackage() -> launchWithSfcFamily(game, emulatorPackage)
            else -> launchWithGenericView(
                game = game,
                emulatorPackage = emulatorPackage,
                includeCacheUris = platform.kind == PlatformKind.GBA || platform.kind == PlatformKind.GB || platform.kind == PlatformKind.NES || platform.kind == PlatformKind.SFC,
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

    private fun launchWithGbaFamily(game: GameItem, emulatorPackage: String, platformKind: PlatformKind = PlatformKind.GBA): Boolean {
        // Most GB/GBA emulators accept either a granted content:// uri or a temporary file uri.
        return launchWithGenericView(
            game = game,
            emulatorPackage = emulatorPackage,
            includeCacheUris = true,
            extraBuilder = { uri -> addCommonRomExtras(uri, game, platformKind) }
        )
    }


    private fun launchGbWithMyOldBoy(game: GameItem, emulatorPackage: String): Boolean {
        // My OldBoy! does not behave like My Boy!: package-level ACTION_VIEW often opens
        // the ROM browser instead of the game. Prefer known / queried emulator activities,
        // and only show a clear fallback message if the installed build does not expose one.
        val originalUri = Uri.parse(game.uri)
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
        val uriCandidates = listOfNotNull(originalUri, cachedContentUri, cachedFileUri).distinctBy { it.toString() }
        val launcherComponent = context.packageManager.getLaunchIntentForPackage(emulatorPackage)?.component

        val explicitCandidates = buildList {
            uriCandidates.forEach { uri ->
                myOldBoyKnownComponents(emulatorPackage).forEach { component ->
                    myOldBoyMimeTypes(game).forEach { mime ->
                        add(
                            Intent(Intent.ACTION_VIEW).apply {
                                this.component = component
                                setDataAndType(uri, mime)
                                addMyOldBoyExtras(uri, game)
                                addRomFlags(uri)
                            }
                        )
                    }
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            this.component = component
                            data = uri
                            addMyOldBoyExtras(uri, game)
                            addRomFlags(uri)
                        }
                    )
                }
            }
        }.distinctBy { listOf(it.component?.className.orEmpty(), it.dataString.orEmpty(), it.type.orEmpty()).joinToString("|") }

        explicitCandidates.forEach { intent -> if (tryStartIntent(intent)) return true }

        val queryBases = buildList {
            uriCandidates.forEach { uri ->
                myOldBoyMimeTypes(game).forEach { mime ->
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            setPackage(emulatorPackage)
                            addMyOldBoyExtras(uri, game)
                            addRomFlags(uri)
                        }
                    )
                }
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        setPackage(emulatorPackage)
                        addMyOldBoyExtras(uri, game)
                        addRomFlags(uri)
                    }
                )
            }
        }

        val exportedViewIntents = queryBases
            .flatMap { queryExplicitRomIntents(it, emulatorPackage) }
            .filter { intent ->
                val component = intent.component ?: return@filter false
                val className = component.className.lowercase(Locale.ROOT)
                val isLauncher = launcherComponent?.className == component.className
                !isLauncher && (className.contains("emulator") || className.contains("game") || className.contains("play"))
            }
            .distinctBy { intent ->
                listOf(intent.component?.className.orEmpty(), intent.dataString.orEmpty(), intent.type.orEmpty()).joinToString("|")
            }

        exportedViewIntents.forEach { intent -> if (tryStartIntent(intent)) return true }

        openEmulatorOnly(
            emulatorPackage,
            I18n.t(
                context,
                "toast.my_oldboy_direct_open_failed",
                "My OldBoy opened, but this installed build did not expose a stable direct ROM launch entry. Use the built-in GB/GBC emulator for one-click launch."
            )
        )
        return true
    }

    private fun myOldBoyKnownComponents(emulatorPackage: String): List<ComponentName> {
        val packageBased = listOf(
            "$emulatorPackage.EmulatorActivity",
            "$emulatorPackage.GameActivity",
            "$emulatorPackage.PlayActivity"
        )
        val fastEmulatorBased = listOf(
            "com.fastemulator.gbc.EmulatorActivity",
            "com.fastemulator.gbc.GameActivity",
            "com.fastemulator.gbcfree.EmulatorActivity",
            "com.fastemulator.gbcfree.GameActivity"
        )
        return (packageBased + fastEmulatorBased)
            .map { ComponentName(emulatorPackage, it) }
            .distinctBy { it.className }
    }

    private fun myOldBoyMimeTypes(game: GameItem): List<String> = linkedSetOf(
        mimeTypeForGame(game),
        "application/x-gameboy-rom",
        "application/x-gb-rom",
        "application/x-gameboy-color-rom",
        "application/x-gbc-rom",
        "application/octet-stream"
    ).toList()

    private fun Intent.addMyOldBoyExtras(uri: Uri, game: GameItem) {
        val value = uri.toString()
        val rawPath = readableFilePath(uri).orEmpty().ifBlank { uri.path.orEmpty() }
        val launchValue = rawPath.ifBlank { value }
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, value)
        putExtra("uri", value)
        putExtra("Uri", value)
        putExtra("URI", value)
        putExtra("rom", launchValue)
        putExtra("ROM", launchValue)
        putExtra("Rom", launchValue)
        putExtra("romUri", value)
        putExtra("rom_uri", value)
        putExtra("ROM_URI", value)
        putExtra("path", launchValue)
        putExtra("file", launchValue)
        putExtra("filepath", launchValue)
        putExtra("file_path", launchValue)
        putExtra("filename", game.fileName)
        putExtra("fileName", game.fileName)
        putExtra("game", game.title)
        putExtra("title", game.title)
        if (rawPath.isNotBlank()) {
            putExtra("rawPath", rawPath)
            putExtra("romPath", rawPath)
            putExtra("rom_path", rawPath)
            putExtra("absolutePath", rawPath)
        }
    }

    private fun launchWithNesFamily(game: GameItem, emulatorPackage: String): Boolean {
        // FC/NES ROMs are usually small; provide cached content:// and file:// fallbacks for older emulators.
        return when {
            emulatorPackage.isNesEmuPackage() -> launchWithNesEmu(game, emulatorPackage)
            emulatorPackage.isJohnNessPackage() -> launchWithJohnNess(game, emulatorPackage)
            else -> launchWithGenericView(
                game = game,
                emulatorPackage = emulatorPackage,
                includeCacheUris = true,
                extraBuilder = { uri -> addCommonRomExtras(uri, game, PlatformKind.NES) }
            )
        }
    }

    private fun launchWithSfcFamily(game: GameItem, emulatorPackage: String): Boolean {
        // SFC/SNES ROMs are small; cached file/content fallbacks improve compatibility with older emulators.
        return launchWithGenericView(
            game = game,
            emulatorPackage = emulatorPackage,
            includeCacheUris = true,
            extraBuilder = { uri -> addCommonRomExtras(uri, game, PlatformKind.SFC) }
        )
    }

    private fun launchWithNesEmu(game: GameItem, emulatorPackage: String): Boolean {
        val component = ComponentName(emulatorPackage, "com.imagine.BaseActivity")
        val candidates = buildList {
            romUriCandidates(game, includeCacheUris = true).forEach { uri ->
                mimeTypesForGame(game).forEach { mime ->
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            this.component = component
                            setDataAndType(uri, mime)
                            addCommonRomExtras(uri, game, PlatformKind.NES)
                            addRomFlags(uri)
                        }
                    )
                }
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        this.component = component
                        data = uri
                        addCommonRomExtras(uri, game, PlatformKind.NES)
                        addRomFlags(uri)
                    }
                )
            }
        }
        candidates.forEach { intent -> if (tryStartIntent(intent)) return true }
        return launchWithGenericView(
            game = game,
            emulatorPackage = emulatorPackage,
            includeCacheUris = true,
            extraBuilder = { uri -> addCommonRomExtras(uri, game, PlatformKind.NES) }
        )
    }

    private fun launchWithJohnNess(game: GameItem, emulatorPackage: String): Boolean {
        val uriCandidates = romUriCandidates(game, includeCacheUris = true)
        val launcherComponent = context.packageManager.getLaunchIntentForPackage(emulatorPackage)?.component
        val explicitActivityNames = listOf(
            // These are best-effort candidates across different John emulator builds.
            // Launcher/RomList activities are intentionally not used here: they open John NESS itself,
            // but do not count as one-click game launch.
            "com.johnemulators.activity.EmulatorActivity",
            "com.johnemulators.common.EmulatorActivity",
            "com.johnemulators.johnness.EmulatorActivity",
            "com.johnemulators.johnnes.EmulatorActivity",
            "com.johnemulators.activity.GameActivity",
            "com.johnemulators.common.GameActivity",
            "com.johnemulators.johnness.GameActivity",
            "com.johnemulators.johnnes.GameActivity",
            "com.johnemulators.activity.EmuActivity",
            "com.johnemulators.common.EmuActivity",
            "com.johnemulators.johnness.NesActivity",
            "com.johnemulators.johnness.NesEmulatorActivity",
            "com.johnemulators.johnnes.NesActivity",
            "com.johnemulators.johnnes.NesEmulatorActivity"
        ).map { ComponentName(emulatorPackage, it) }.distinctBy { it.className }

        val packageViewIntents = buildList {
            uriCandidates.forEach { uri ->
                mimeTypesForGame(game).forEach { mime ->
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            setPackage(emulatorPackage)
                            addCommonRomExtras(uri, game, PlatformKind.NES)
                            addJohnEmulatorExtras(uri, game)
                            addRomFlags(uri)
                        }
                    )
                }
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                        setPackage(emulatorPackage)
                        addCommonRomExtras(uri, game, PlatformKind.NES)
                        addJohnEmulatorExtras(uri, game)
                        addRomFlags(uri)
                    }
                )
            }
        }

        // Prefer activities that the target app actually exposes for ACTION_VIEW.
        // Filter out the normal launcher screen, otherwise John NESS only opens its own ROM list.
        val exportedViewIntents = packageViewIntents
            .flatMap { queryExplicitRomIntents(it, emulatorPackage) }
            .filter { intent ->
                val component = intent.component ?: return@filter false
                launcherComponent == null || component.className != launcherComponent.className
            }
            .distinctBy { intent ->
                listOf(
                    intent.component?.className.orEmpty(),
                    intent.action.orEmpty(),
                    intent.dataString.orEmpty(),
                    intent.type.orEmpty()
                ).joinToString("|")
            }

        exportedViewIntents.forEach { intent -> if (tryStartIntent(intent)) return true }

        val explicitCandidates = buildList {
            uriCandidates.forEach { uri ->
                explicitActivityNames.forEach { component ->
                    mimeTypesForGame(game).forEach { mime ->
                        add(
                            Intent(Intent.ACTION_VIEW).apply {
                                this.component = component
                                setDataAndType(uri, mime)
                                addCommonRomExtras(uri, game, PlatformKind.NES)
                                addJohnEmulatorExtras(uri, game)
                                addRomFlags(uri)
                            }
                        )
                    }
                    add(
                        Intent(Intent.ACTION_VIEW).apply {
                            this.component = component
                            data = uri
                            addCommonRomExtras(uri, game, PlatformKind.NES)
                            addJohnEmulatorExtras(uri, game)
                            addRomFlags(uri)
                        }
                    )
                }
            }
        }
        explicitCandidates.forEach { intent -> if (tryStartIntent(intent)) return true }

        Toast.makeText(
            context,
            "John NESS 当前版本未开放稳定的外部 ROM 直启入口，建议 FC/NES 一键启动改用 Nes.emu 或 RetroArch。",
            Toast.LENGTH_LONG
        ).show()
        // Return true so launchGame does not fall back to opening John NESS's ROM list and look like a failed direct launch.
        return true
    }

    private fun Intent.addCommonRomExtras(uri: Uri, game: GameItem, platformKind: PlatformKind) {
        val value = uri.toString()
        val pathValue = readableFilePath(uri).orEmpty().ifBlank { uri.path.orEmpty() }
        val pathOrUri = pathValue.ifBlank { value }
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, value)
        putExtra("uri", value)
        putExtra("Uri", value)
        putExtra("URI", value)
        putExtra("rom", value)
        putExtra("ROM", value)
        putExtra("romUri", value)
        putExtra("rom_uri", value)
        putExtra("path", pathOrUri)
        putExtra("file", pathOrUri)
        putExtra("filepath", pathOrUri)
        putExtra("file_path", pathOrUri)
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

    private fun Intent.addJohnEmulatorExtras(uri: Uri, game: GameItem) {
        val value = uri.toString()
        val rawPath = readableFilePath(uri).orEmpty().ifBlank { uri.path.orEmpty() }
        val launchValue = rawPath.ifBlank { value }
        val fileName = game.fileName
        putExtra("ROM", launchValue)
        putExtra("rom", launchValue)
        putExtra("Rom", launchValue)
        putExtra("ROM_PATH", launchValue)
        putExtra("romPath", launchValue)
        putExtra("rom_path", launchValue)
        putExtra("GAME", launchValue)
        putExtra("game", launchValue)
        putExtra("GAME_PATH", launchValue)
        putExtra("GAMEPATH", launchValue)
        putExtra("gamePath", launchValue)
        putExtra("path", launchValue)
        putExtra("file", launchValue)
        putExtra("ROM_URI", value)
        putExtra("romUri", value)
        putExtra("rom_uri", value)
        putExtra("filename", fileName)
        putExtra("fileName", fileName)
        putExtra("title", game.title)
        if (rawPath.isNotBlank()) {
            putExtra("ROM_FILE", rawPath)
            putExtra("romFile", rawPath)
            putExtra("rom_file", rawPath)
            putExtra("GAME_FILE", rawPath)
            putExtra("gameFile", rawPath)
            putExtra("game_file", rawPath)
            putExtra("absolutePath", rawPath)
        }
    }

    private fun launchWithRetroArch(game: GameItem, platformKind: PlatformKind, emulatorPackage: String): Boolean {
        val cachedFile = copyRomToLaunchCache(game) ?: return launchWithGenericView(game, emulatorPackage, includeCacheUris = false)
        val coreNames = when (platformKind) {
            PlatformKind.PSP -> listOf("ppsspp_libretro_android.so")
            PlatformKind.GBA -> listOf("mgba_libretro_android.so", "gpsp_libretro_android.so", "vba_next_libretro_android.so", "vba_m_libretro_android.so")
            PlatformKind.GB -> listOf("gambatte_libretro_android.so", "sameboy_libretro_android.so", "mgba_libretro_android.so")
            PlatformKind.SFC -> listOf("snes9x_libretro_android.so", "bsnes_libretro_android.so", "mesen-s_libretro_android.so")
            PlatformKind.NES -> FcExternalEmulatorProfiles.retroArchCoreNames
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
        val originalFileUri = readableFilePath(originalUri)?.let { path ->
            runCatching {
                StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                Uri.fromFile(File(path))
            }.getOrNull()
        }
        if (!includeCacheUris) return listOfNotNull(originalUri, originalFileUri).distinctBy { it.toString() }

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
        return listOfNotNull(originalUri, originalFileUri, cachedContentUri, cachedFileUri).distinctBy { it.toString() }
    }

    private fun readableFilePath(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.path?.takeIf { it.isNotBlank() }
        if (!uri.scheme.equals("content", ignoreCase = true)) return null
        if (uri.authority != "com.android.externalstorage.documents") return null

        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: documentIdFromEncodedPath(uri)
            ?: return null

        val parts = docId.split(':', limit = 2)
        if (parts.isEmpty()) return null
        val volume = parts[0].lowercase(Locale.ROOT)
        val relativePath = parts.getOrNull(1).orEmpty().trimStart('/')
        val basePath = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            "home" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            else -> "/storage/${parts[0]}"
        }
        return if (relativePath.isBlank()) basePath else "$basePath/$relativePath"
    }

    private fun documentIdFromEncodedPath(uri: Uri): String? {
        val encodedPath = uri.encodedPath ?: return null
        val markers = listOf("/document/", "/tree/")
        markers.forEach { marker ->
            val index = encodedPath.lastIndexOf(marker)
            if (index >= 0) {
                return Uri.decode(encodedPath.substring(index + marker.length)).takeIf { it.isNotBlank() }
            }
        }
        return null
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
        "gb" -> listOf("application/x-gameboy-rom", "application/x-gb-rom", "application/octet-stream")
        "gbc" -> listOf("application/x-gameboy-color-rom", "application/x-gbc-rom", "application/octet-stream")
        "sgb" -> listOf("application/x-super-gameboy-rom", "application/octet-stream")
        "sfc", "smc" -> listOf("application/x-snes-rom", "application/x-super-nintendo-rom", "application/x-sfc-rom", "application/octet-stream")
        "swc", "fig", "bs", "st" -> listOf("application/x-snes-rom", "application/octet-stream")
        "zip" -> listOf("application/zip", "application/octet-stream")
        "7z" -> listOf("application/x-7z-compressed", "application/octet-stream")
        "iso" -> listOf("application/x-iso9660-image", "application/octet-stream")
        "cso" -> listOf("application/x-cso", "application/octet-stream")
        "pbp" -> listOf("application/x-pbp", "application/octet-stream")
        "chd" -> listOf("application/x-chd", "application/octet-stream")
        "nes" -> listOf(
            "application/x-nes-rom",
            "application/x-nintendo-nes-rom",
            "application/x-nes",
            "application/x-famicom-rom",
            "application/octet-stream"
        )
        "fds" -> listOf("application/x-fds-disk", "application/x-famicom-disk-system", "application/octet-stream")
        "unf", "unif" -> listOf("application/x-unif-rom", "application/octet-stream")
        else -> listOf("application/octet-stream")
    }.distinct()


    private fun String.isMyOldBoyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.fastemulator.gbc" ||
            value == "com.fastemulator.gbcfree" ||
            value.contains("myoldboy") ||
            value.contains("oldboy") ||
            value.contains("fastemulator") && value.contains("gbc")
    }

    private fun String.isMyBoyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.fastemulator.gba" || value.contains("fastemulator") || value.contains("myboy") || value.contains("myoldboy") || value.contains("oldboy")
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
            value.contains("oldboy") ||
            value.contains("gbc") ||
            value.contains("gambatte") ||
            value.contains("sameboy") ||
            value.contains("johnemulators") && value.contains("gba") ||
            value.contains("johngba") ||
            value == "com.explusalpha.gbaemu" ||
            value == "com.explusalpha.gbcemu" ||
            value.contains("gbaemu") ||
            value.contains("gbcemu") ||
            value.contains("nostalgiaemulators") && value.contains("gba") ||
            value.contains("nostalgia") && value.contains("gba") ||
            value.contains("mgba")
    }

    private fun String.isNesFamilyPackage(): Boolean =
        FcExternalEmulatorProfiles.matchesPackage(this)

    private fun String.isSfcFamilyPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value.contains("snes9x") ||
            value.contains("snes") ||
            value.contains("sfc") ||
            value.contains("superretro") ||
            value == "com.explusalpha.snes9xplus"
    }

    private fun String.isNesEmuPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.explusalpha.nesemu" || value.contains("nes.emu") || value.contains("nesemu")
    }

    private fun String.isJohnNessPackage(): Boolean {
        val value = lowercase(Locale.ROOT)
        return value == "com.johnemulators.johnness" ||
            value == "com.johnemulators.johnnesslite" ||
            value == "com.johnemulators.johnnes" ||
            value == "com.johnemulators.johnneslite" ||
            value.contains("johnness") ||
            value.contains("johnnes") ||
            (value.contains("johnemulators") && value.contains("nes"))
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
                message ?: I18n.t(context, "toast.external_opened", "已打开模拟器；如果没有直接进入游戏，需要继续适配该模拟器的专用启动参数"),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, I18n.t(context, "toast.open_emulator_failed", "无法打开所选模拟器"), Toast.LENGTH_SHORT).show()
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
                .onFailure { Toast.makeText(context, I18n.t(context, "toast.choose_home_manually", "请在系统设置里手动选择默认桌面 App"), Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.bond.md3elauncher.fileprovider"
    }
}
