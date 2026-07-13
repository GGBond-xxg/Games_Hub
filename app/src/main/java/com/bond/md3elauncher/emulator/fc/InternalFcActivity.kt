package com.bond.md3elauncher.emulator.fc

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bond.md3elauncher.MainActivity
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

private val FC_COMMON_MAPPERS = setOf(
    0, 1, 2, 3, 4, 7, 9, 10, 11, 13, 15, 16, 18, 19, 21, 23, 24, 25, 26,
    32, 33, 34, 64, 66, 68, 69, 71, 73, 74, 75, 78, 79, 85, 87, 88, 89, 90,
    118, 119
)

private data class FcRomHeaderInfo(
    val mapper: Int,
    val prgKb: Int,
    val chrKb: Int,
    val isNes2: Boolean,
    val warning: String?,
    val isRiskyHack: Boolean
)

private data class FcCoreChoice(
    val file: File,
    val displayName: String,
    val notice: String? = null
)

class InternalFcActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private var controlsView: FcTouchControlsView? = null
    private var loadingText: TextView? = null
    internal var gameStorage: FcGameStorage? = null
    private var autoStateRestored = false
    internal var fastForwardSpeed = 1
    private var pausedForMenu = false
    private var suppressLifecycleSaveRam = false
    private var preparedRomHeaderInfo: FcRomHeaderInfo? = null
    private var preparedRomStorageSeed: String = ""
    private var firstFrameRendered = false

    private val hardwarePressedKeys = mutableSetOf<Int>()
    private val handledLongKeys = mutableSetOf<Int>()
    private val firedShortcutActions = mutableSetOf<ControllerShortcutAction>()
    private val turboHandler = Handler(Looper.getMainLooper())
    private val turboTasks = mutableMapOf<Int, Runnable>()
    private val turboTargets = mutableMapOf<Int, Int>()
    private val turboDownTargets = mutableSetOf<Int>()

    internal val settingsPrefs: SharedPreferences by lazy {
        getSharedPreferences("internal_fc_settings", Context.MODE_PRIVATE)
    }

    internal var touchControlsAlpha: Float = DEFAULT_TOUCH_CONTROLS_ALPHA
        set(value) {
            val fixed = value.coerceIn(0f, 1f)
            field = fixed
            settingsPrefs.edit().putFloat(PREF_TOUCH_CONTROLS_ALPHA, fixed).apply()
            controlsView?.invalidate()
        }

    internal var hardwareControlsAlpha: Float = DEFAULT_HARDWARE_CONTROLS_ALPHA
        set(value) {
            val fixed = value.coerceIn(0f, 1f)
            field = fixed
            settingsPrefs.edit().putFloat(PREF_HARDWARE_CONTROLS_ALPHA, fixed).apply()
            controlsView?.invalidate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        touchControlsAlpha = settingsPrefs.getFloat(PREF_TOUCH_CONTROLS_ALPHA, DEFAULT_TOUCH_CONTROLS_ALPHA).coerceIn(0f, 1f)
        hardwareControlsAlpha = settingsPrefs.getFloat(PREF_HARDWARE_CONTROLS_ALPHA, DEFAULT_HARDWARE_CONTROLS_ALPHA).coerceIn(0f, 1f)

        showLoading("正在启动内置 FC/NES 模拟器...")
        configureWindow()

        val romUri = intent.getStringExtra(EXTRA_ROM_URI)?.let(Uri::parse)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { "game.nes" }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { fileName }

        if (romUri == null) {
            finishWithMessage("没有收到 FC/NES ROM")
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { prepareRomFile(romUri, fileName) }
            }
            val romFile = result.getOrElse {
                finishWithMessage("读取 ROM 失败：${it.message ?: "未知错误"}")
                return@launch
            }
            startRetroView(romFile, title)
        }
    }

    override fun onResume() {
        super.onResume()
        configureWindow()
    }

    override fun onPause() {
        persistSaveRamIfSafe("onPause")
        stopAllTurbo()
        controlsView?.releaseAll()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureWindow()
    }

    override fun onDestroy() {
        persistSaveRamIfSafe("onDestroy")
        stopAllTurbo()
        controlsView?.releaseAll()
        controlsView = null
        val viewToDestroy = retroView
        retroView = null
        if (suppressLifecycleSaveRam || isFinishing) {
            Log.w(TAG, "skip retroView.onDestroy suppress=$suppressLifecycleSaveRam finishing=$isFinishing")
        } else {
            runCatching { viewToDestroy?.onDestroy() }
                .onFailure { Log.w(TAG, "retroView.onDestroy failed", it) }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (event.action == KeyEvent.ACTION_DOWN) {
            hardwarePressedKeys += keyCode
        } else if (event.action == KeyEvent.ACTION_UP) {
            hardwarePressedKeys -= keyCode
            handledLongKeys -= keyCode
        }

        if (handleConfigurableHardwareShortcut(event)) {
            return true
        }

        controlsView?.handleHardwareMenuKey(event)?.let { consumed ->
            if (consumed) return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            // Fixed hardware shortcuts are intentionally handled by handleConfigurableHardwareShortcut().
            // Keys not bound to shortcuts continue to fall through to normal NES input below.
        }

        val view = retroView
        if (view != null && isGameKey(keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                controlsView?.markHardwareControllerUsed()
            }
            runCatching { view.sendKeyEvent(event.action, keyCode, 0) }
            return true
        }

        return super.dispatchKeyEvent(event)
    }


    private fun handleConfigurableHardwareShortcut(event: KeyEvent): Boolean {
        val settings = ControllerShortcutSettings.load(this)
        if (controlsView?.isMenuOpen() == true) {
            return handleConfiguredMenuToggleShortcut(event, settings)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            val released = ControllerShortcutSettings.releasedActions(settings, hardwarePressedKeys, firedShortcutActions)
            if (released.isEmpty()) return false
            var consumed = false
            released.forEach { action ->
                firedShortcutActions -= action
                when (action) {
                    ControllerShortcutAction.TURBO_A -> {
                        stopTurbo(SHORTCUT_TURBO_A_SOURCE)
                        consumed = true
                    }
                    ControllerShortcutAction.TURBO_B -> {
                        stopTurbo(SHORTCUT_TURBO_B_SOURCE)
                        consumed = true
                    }
                    else -> {
                        if (event.keyCode in settings.keysFor(action)) consumed = true
                    }
                }
            }
            return consumed
        }

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val action = ControllerShortcutSettings.findTriggeredAction(
            settings = settings,
            pressedKeys = hardwarePressedKeys,
            currentKey = event.keyCode,
            alreadyFired = firedShortcutActions
        ) ?: return false

        firedShortcutActions += action
        controlsView?.markHardwareControllerUsed()
        when (action) {
            ControllerShortcutAction.QUICK_SAVE -> saveQuickState()
            ControllerShortcutAction.QUICK_LOAD -> loadQuickState(hideMenuAfterLoad = false)
            ControllerShortcutAction.FAST_FORWARD -> cycleFastForwardSpeed()
            ControllerShortcutAction.OPEN_MENU -> controlsView?.openMenuFromShortcut()
            ControllerShortcutAction.EXIT_GAME -> {
                releaseAllGameInputs()
                exitGame()
            }
            ControllerShortcutAction.TURBO_A -> startTurbo(SHORTCUT_TURBO_A_SOURCE, KeyEvent.KEYCODE_BUTTON_A)
            ControllerShortcutAction.TURBO_B -> startTurbo(SHORTCUT_TURBO_B_SOURCE, KeyEvent.KEYCODE_BUTTON_B)
        }
        return true
    }

    private fun handleConfiguredMenuToggleShortcut(event: KeyEvent, settings: ControllerShortcutSettings): Boolean {
        val menuKeys = settings.keysFor(ControllerShortcutAction.OPEN_MENU)
        if (menuKeys.isEmpty()) return false

        if (event.action == KeyEvent.ACTION_UP) {
            if (ControllerShortcutAction.OPEN_MENU in firedShortcutActions && event.keyCode in menuKeys) {
                firedShortcutActions -= ControllerShortcutAction.OPEN_MENU
                return true
            }
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val shouldToggle = event.keyCode in menuKeys && menuKeys.all { it in hardwarePressedKeys }
        if (!shouldToggle || ControllerShortcutAction.OPEN_MENU in firedShortcutActions) return false

        firedShortcutActions += ControllerShortcutAction.OPEN_MENU
        controlsView?.toggleMenu()
        return true
    }

    private fun startRetroView(romFile: File, title: String) {
        val coreChoice = chooseFcCore()
        if (!coreChoice.file.exists()) {
            finishWithMessage("内置 FC/NES 核心不存在：${coreChoice.file.absolutePath}")
            return
        }

        val systemDir = File(filesDir, "internal_fc/system").apply { mkdirs() }
        val savesDir = File(filesDir, "internal_fc/saves").apply { mkdirs() }
        val storage = buildGameStorage(romFile, title).also {
            it.root.mkdirs()
            gameStorage = it
        }

        val data = GLRetroViewData(applicationContext).apply {
            coreFilePath = coreChoice.file.absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = systemDir.absolutePath
            savesDirectory = savesDir.absolutePath
            saveRAMState = storage.saveRamFile.takeIf { it.exists() && it.length() > 0L }?.readBytes()
            variables = fcVariables()
            preferLowLatencyAudio = true
            skipDuplicateFrames = true
        }

        val view = GLRetroView(this, data).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        retroView = view
        lifecycle.addObserver(view)

        val controls = FcTouchControlsView(this).also { controlsView = it }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            addView(
                controls,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        setContentView(frame)
        configureWindow()
        view.requestFocus()
        firstFrameRendered = false
        startAutoRestoreWatcher(view, storage)
        startLaunchStallWatcher(title, coreChoice.displayName)
        // v0.1.71：启动时不再弹出 Mapper / core 风险提示，避免汉化 ROM 打开后连续 Toast 干扰。
        // 兼容性信息只写日志；真正的操作结果（存档/读档/重启）仍然用 Toast 提示。
        Log.d(TAG, "Internal FC/NES launch title=$title core=${coreChoice.displayName} header=$preparedRomHeaderInfo notice=${coreChoice.notice}")
        if (!shouldAutoRestoreInitialState()) {
            Log.d(TAG, "FC/NES clean boot: skip initial auto state restore title=$title header=$preparedRomHeaderInfo")
        }
    }

    private fun chooseFcCore(): FcCoreChoice {
        // v0.1.73：实测 Nestopia 可以同时启动普通 ROM 和 Mapper 115 汉化/改版 ROM。
        // 为了避免 Mesen native 闪退和 FCEUmm 黑屏问题，内置 FC/NES 固定只使用 Nestopia。
        val nativeDir = File(applicationInfo.nativeLibraryDir)
        val chosenFile = File(nativeDir, NESTOPIA_CORE_FILE_NAME)
        Log.d(TAG, "FC/NES core selected=Nestopia only file=${chosenFile.absolutePath} header=$preparedRomHeaderInfo")
        return FcCoreChoice(chosenFile, "Nestopia")
    }

    private fun fcVariables(): Array<Variable> = emptyArray()

    private fun prepareRomFile(uri: Uri, fileName: String): File {
        val safeName = sanitizeFileName(fileName).ifBlank { "game.nes" }
        val dir = File(cacheDir, "internal_fc/roms").apply { mkdirs() }
        val lower = safeName.lowercase(Locale.ROOT)

        if (lower.endsWith(".7z")) {
            error("内置 FC/NES 暂不支持直接读取 7z，请解压为 .nes，或继续使用 Nes.emu / RetroArch 外部模拟器")
        }
        if (lower.endsWith(".zip")) {
            extractFcFromZip(uri, dir, safeName)?.let { extracted ->
                preparedRomHeaderInfo = analyzeFcRomHeader(extracted)
                return extracted
            }
            error("ZIP 中没有找到可用的 .nes / .fds / .unf / .unif ROM")
        }

        // v0.1.65：不要把中文/特殊字符文件名直接传给 libretro core。
        // 部分 FC/NES core 在 Android native 层读取非 ASCII 路径时会失败，表现为原版能开、汉化/改版无法启动。
        // 这里统一复制到 ASCII 缓存名，再把该路径交给内置 Nestopia core。
        val extension = fcRomExtension(lower) ?: ".nes"
        preparedRomStorageSeed = originalRomStorageName(safeName, extension)
        val outFile = File(dir, asciiRomCacheName("rom", "${uri}|${safeName}", extension))
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开 ROM 输入流" }
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        preparedRomHeaderInfo = analyzeFcRomHeader(outFile)
        return outFile
    }

    private fun extractFcFromZip(uri: Uri, dir: File, archiveName: String): File? {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开 ZIP 输入流" }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val entryName = sanitizeFileName(entry.name.substringAfterLast('/')).ifBlank {
                        archiveName.substringBeforeLast('.', archiveName) + ".nes"
                    }
                    val lower = entryName.lowercase(Locale.ROOT)
                    if (!isFcRomName(lower)) continue
                    val extension = fcRomExtension(lower) ?: ".nes"
                    preparedRomStorageSeed = originalRomStorageName(entryName, extension)
                    val outFile = File(dir, asciiRomCacheName("zip", "${archiveName}|${entryName}", extension))
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                    return outFile
                }
            }
        }
        return null
    }



    private fun analyzeFcRomHeader(file: File): FcRomHeaderInfo? {
        return runCatching {
            if (!file.name.lowercase(Locale.ROOT).endsWith(".nes")) return@runCatching null
            val header = ByteArray(16)
            file.inputStream().use { input ->
                if (input.read(header) < 16) {
                    return@runCatching FcRomHeaderInfo(
                        mapper = -1,
                        prgKb = 0,
                        chrKb = 0,
                        isNes2 = false,
                        warning = "FC/NES：ROM 头信息不完整，内置 Nestopia 可能无法启动；可继续用 Nes.emu 外部模拟器。",
                        isRiskyHack = true
                    )
                }
            }
            if (header[0] != 0x4E.toByte() || header[1] != 0x45.toByte() || header[2] != 0x53.toByte() || header[3] != 0x1A.toByte()) {
                return@runCatching FcRomHeaderInfo(
                    mapper = -1,
                    prgKb = 0,
                    chrKb = 0,
                    isNes2 = false,
                    warning = "FC/NES：不是标准 iNES/NES2 ROM 头，内置 Nestopia 可能无法启动；可继续用 Nes.emu 外部模拟器。",
                    isRiskyHack = true
                )
            }
            val flags6 = header[6].toInt() and 0xFF
            val flags7 = header[7].toInt() and 0xFF
            val flags8 = header[8].toInt() and 0xFF
            val isNes2 = (flags7 and 0x0C) == 0x08
            val mapper = ((flags6 ushr 4) or (flags7 and 0xF0)) or if (isNes2) ((flags8 and 0x0F) shl 8) else 0
            val prgKb = (header[4].toInt() and 0xFF) * 16
            val chrKb = (header[5].toInt() and 0xFF) * 8
            val isRiskyHack = mapper == 115 || (mapper >= 0 && mapper !in FC_COMMON_MAPPERS) || isNes2 || file.length() > 2L * 1024L * 1024L
            val warning = when {
                mapper == 115 -> "FC/NES：检测到 Mapper 115 汉化/改版 ROM。已改为干净启动并使用 Nestopia core。"
                isNes2 && mapper >= 256 -> "FC/NES：检测到 NES2.0 高 Mapper($mapper) 改版 ROM。已使用 Nestopia core 干净启动。"
                mapper >= 0 && mapper !in FC_COMMON_MAPPERS -> "FC/NES：检测到非普通 Mapper($mapper) ROM。已干净启动；如果无法启动，建议用 Nes.emu，或后续加入更兼容 NES core。"
                file.length() > 2L * 1024L * 1024L -> "FC/NES：检测到大容量改版 ROM。已干净启动；如果无法启动，建议继续用 Nes.emu。"
                else -> null
            }
            Log.d(TAG, "NES header mapper=$mapper nes2=$isNes2 prg=${prgKb}KB chr=${chrKb}KB size=${file.length()} risky=$isRiskyHack")
            FcRomHeaderInfo(mapper = mapper, prgKb = prgKb, chrKb = chrKb, isNes2 = isNes2, warning = warning, isRiskyHack = isRiskyHack)
        }.getOrNull()
    }

    private fun startAutoRestoreWatcher(view: GLRetroView, storage: FcGameStorage) {
        autoStateRestored = false
        val shouldAutoRestore = shouldAutoRestoreInitialState()
        lifecycleScope.launch {
            view.getGLRetroEvents().collect { event ->
                if (event == GLRetroView.GLRetroEvents.FrameRendered) {
                    firstFrameRendered = true
                    if (!autoStateRestored) {
                        autoStateRestored = true
                        if (shouldAutoRestore) {
                            restoreInitialState(view, storage)
                        } else {
                            Log.d(TAG, "skip initial auto restore for FC/NES clean boot storage=${storage.root.name} header=$preparedRomHeaderInfo")
                        }
                    }
                }
            }
        }
    }

    private fun shouldAutoRestoreInitialState(): Boolean {
        // FC/NES 不再默认自动读快捷存档。
        // 这次日志里 Mapper 115 已成功进入 core，但紧接着自动读取旧快照，
        // 如果旧快照来自不兼容/黑屏状态，就会每次启动都被恢复回坏状态。
        // 需要读档时从游戏内菜单手动读取，避免影响汉化/改版 ROM 的首次启动判断。
        if (!intent.getBooleanExtra(EXTRA_AUTO_RESTORE_STATE, false)) return false
        val header = preparedRomHeaderInfo
        if (header?.isRiskyHack == true) return false
        return true
    }

    private fun startLaunchStallWatcher(title: String, coreName: String) {
        lifecycleScope.launch {
            delay(4500L)
            if (firstFrameRendered || isFinishing || isDestroyed) return@launch
            val header = preparedRomHeaderInfo
            val mapperText = header?.takeIf { it.mapper >= 0 }?.let { "Mapper ${it.mapper}" } ?: "未知 Mapper"
            // v0.1.71：只记录黑屏/无首帧信息，不再反复弹窗。
            Log.w(TAG, "no frame rendered for FC/NES title=$title core=$coreName mapper=$mapperText header=$header")
        }
    }

    private suspend fun restoreInitialState(view: GLRetroView, storage: FcGameStorage) {
        val candidates = listOf(
            "快捷存档" to storage.quickStateFile,
            "存档 1" to storage.slotStateFile(1)
        ).filter { it.second.exists() && it.second.length() > 0L }

        for ((label, file) in candidates) {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes == null || bytes.isEmpty()) continue
            val ok = unserializeStateWithBootRetries(view, bytes, label)
            if (ok) {
                Toast.makeText(this, "已自动读取 $label", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    private suspend fun unserializeStateWithBootRetries(
        view: GLRetroView,
        bytes: ByteArray,
        label: String
    ): Boolean {
        val retryDelays = listOf(0L, 360L, 760L, 1200L)
        retryDelays.forEachIndexed { index, waitMs ->
            if (waitMs > 0L) delay(waitMs)
            val ok = runCatching { view.unserializeState(bytes) }
                .onFailure { Log.w(TAG, "restore $label attempt=${index + 1} failed", it) }
                .getOrDefault(false)
            Log.d(TAG, "restore $label attempt=${index + 1}/${retryDelays.size} ok=$ok bytes=${bytes.size}")
            if (ok) return true
        }
        return false
    }

    internal fun saveQuickState() {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        saveStateToFile(storage.quickStateFile, "快捷存档")
    }

    internal fun loadQuickState(hideMenuAfterLoad: Boolean) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        loadStateFromFile(storage.quickStateFile, "快捷存档", hideMenuAfterLoad)
    }

    internal fun saveSlotState(slot: Int) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        saveStateToFile(storage.slotStateFile(slot), "存档 $slot")
    }

    internal fun loadSlotState(slot: Int, hideMenuAfterLoad: Boolean = true) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        loadStateFromFile(storage.slotStateFile(slot), "存档 $slot", hideMenuAfterLoad)
    }

    internal fun deleteSlotState(slot: Int) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        deleteStateFile(storage.slotStateFile(slot), "存档 $slot")
    }

    internal fun deleteQuickState() {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        deleteStateFile(storage.quickStateFile, "快捷存档")
    }

    private fun deleteStateFile(file: File, label: String) {
        if (!file.exists()) {
            showToast("$label 已经是空的")
            return
        }
        val ok = runCatching { file.delete() }.getOrDefault(false)
        showToast(if (ok) "$label 已删除" else "$label 删除失败")
        controlsView?.invalidate()
    }

    private fun saveStateToFile(file: File, label: String) {
        val view = retroView ?: return showToast("模拟器还没准备好")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    val state = view.serializeState()
                    require(state.isNotEmpty()) { "状态数据为空" }
                    file.writeBytes(state)
                }
            }
            result.onSuccess {
                controlsView?.invalidate()
                showToast("$label 保存成功")
            }.onFailure {
                showToast("保存 $label 失败：${it.message ?: "未知错误"}")
            }
        }
    }

    private fun loadStateFromFile(file: File, label: String, hideMenuAfterLoad: Boolean) {
        val view = retroView ?: return showToast("模拟器还没准备好")
        if (!file.exists() || file.length() <= 0L) {
            showToast("没有 $label")
            return
        }
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes == null || bytes.isEmpty()) {
                showToast("读取 $label 失败")
                return@launch
            }
            val ok = runCatching { view.unserializeState(bytes) }.getOrDefault(false)
            if (ok) {
                showToast("已读取 $label")
                if (hideMenuAfterLoad) controlsView?.hideMenu()
            } else {
                showToast("读取 $label 失败")
            }
        }
    }

    private fun persistSaveRamIfSafe(reason: String) {
        if (suppressLifecycleSaveRam || isFinishing || isDestroyed) {
            Log.d(TAG, "skip SRAM persist reason=$reason suppress=$suppressLifecycleSaveRam finishing=$isFinishing destroyed=$isDestroyed")
            return
        }
        // 和当前 GBA 稳定断点保持一致：暂不在生命周期里主动 serializeSRAM，避免部分 core 在 Surface 销毁附近 native 崩溃。
        Log.d(TAG, "skip SRAM persist reason=$reason disabled; use quick state for now")
    }

    internal fun softRestartGameNoExit() {
        val view = retroView ?: return showToast("模拟器还没准备好")
        stopAllTurbo()
        controlsView?.releaseAll()
        controlsView?.hideMenu()
        runCatching {
            view.queueEvent {
                runCatching { LibretroDroid.reset() }
                    .onFailure { Log.e(TAG, "soft reset FC/NES core failed", it) }
            }
        }.onSuccess {
            showToast("已重启当前 FC/NES 游戏")
        }.onFailure {
            Log.e(TAG, "queue soft reset FC/NES failed", it)
            showToast("重启游戏失败")
        }
    }

    internal fun restartGameFresh() {
        showToast("正在重启 FC/NES 游戏...")
        suppressLifecycleSaveRam = true
        stopAllTurbo()
        controlsView?.releaseAll()
        controlsView = null

        val relay = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_FC_COLD_RESTART_RELAY
            putExtras(intent)
            putExtra(MainActivity.EXTRA_FC_RELAY_REQUEST_ID, System.currentTimeMillis())
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }

        runCatching {
            startActivity(relay)
            overridePendingTransition(0, 0)
        }.onFailure {
            Log.e(TAG, "relay internal fc restart failed", it)
        }

        finish()
        overridePendingTransition(0, 0)
        turboHandler.postDelayed({
            Log.w(TAG, "kill old internal_fc process after relay")
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 320L)
    }

    private fun buildGameStorage(romFile: File, title: String): FcGameStorage {
        val displayName = sanitizeFileName(title.substringBeforeLast('.', title)).ifBlank { "FC_NES" }
        val digestSource = "${preparedRomStorageSeed.ifBlank { romFile.name }}:${romFile.length()}"
        val key = sha1(digestSource).take(16) + "_" + displayName.take(36)
        val root = File(filesDir, "internal_fc/game_data/$key")
        return FcGameStorage(key, displayName, root)
    }

    private fun sha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun stateTime(file: File): String {
        if (!file.exists()) return "空"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    private fun fcRomExtension(lowerName: String): String? =
        listOf(".nes", ".fds", ".unf", ".unif").firstOrNull { lowerName.endsWith(it) }

    private fun isFcRomName(lowerName: String): Boolean = fcRomExtension(lowerName) != null

    private fun asciiRomCacheName(prefix: String, seed: String, extension: String): String =
        "${prefix}_${sha1(seed).take(16)}${extension.lowercase(Locale.ROOT)}"

    private fun originalRomStorageName(name: String, extension: String): String =
        if (fcRomExtension(name.lowercase(Locale.ROOT)) != null) name else "${name}${extension}"

    private fun sanitizeFileName(value: String): String =
        value.map { ch -> if (ch.isLetterOrDigit() || ch in listOf('.', '_', '-', ' ', '(', ')', '[', ']')) ch else '_' }
            .joinToString("")
            .take(180)
            .trim()

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    internal fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    internal fun exitGame() {
        suppressLifecycleSaveRam = true
        finish()
    }

    internal fun pauseRetroForMenu() {
        if (pausedForMenu) return
        stopAllTurbo()
        controlsView?.releaseAll()
        releaseAllGameInputs()
        pausedForMenu = true
        runCatching { retroView?.frameSpeed = 0 }
    }

    internal fun resumeRetroAfterMenu() {
        if (!pausedForMenu) return
        pausedForMenu = false
        runCatching { retroView?.frameSpeed = fastForwardSpeed.coerceAtLeast(1) }
        configureWindow()
    }

    internal fun releaseAllGameInputs() {
        gameKeyCodes.forEach { key -> sendVirtualKey(KeyEvent.ACTION_UP, key) }
        turboDownTargets.toList().forEach { key -> sendVirtualKey(KeyEvent.ACTION_UP, key) }
        turboDownTargets.clear()
    }

    private fun showLoading(message: String) {
        loadingText = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        setContentView(loadingText)
    }

    private fun configureWindow() {
        runCatching { WindowCompat.setDecorFitsSystemWindows(window, false) }
        runCatching { window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN) }

        val decor = runCatching { window.decorView }.getOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                decor.windowInsetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                decor.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    private val gameKeyCodes: Set<Int> = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE
    )

    private fun isGameKey(keyCode: Int): Boolean = keyCode in gameKeyCodes

    internal fun sendVirtualKey(action: Int, keyCode: Int) {
        if (keyCode < 0) return
        runCatching { retroView?.sendKeyEvent(action, keyCode, 0) }
    }

    internal fun startTurbo(sourceKey: Int, targetKey: Int) {
        if (turboTasks.containsKey(sourceKey)) return
        turboTargets[sourceKey] = targetKey
        val task = object : Runnable {
            private var down = false
            override fun run() {
                if (!turboTasks.containsKey(sourceKey)) return
                if (down) {
                    sendVirtualKey(KeyEvent.ACTION_UP, targetKey)
                    turboDownTargets -= targetKey
                } else {
                    sendVirtualKey(KeyEvent.ACTION_DOWN, targetKey)
                    turboDownTargets += targetKey
                }
                down = !down
                turboHandler.postDelayed(this, TURBO_INTERVAL_MS)
            }
        }
        turboTasks[sourceKey] = task
        turboHandler.post(task)
    }

    internal fun stopTurbo(sourceKey: Int) {
        val task = turboTasks.remove(sourceKey)
        val target = turboTargets.remove(sourceKey)
        if (task != null) turboHandler.removeCallbacks(task)
        if (target != null && turboDownTargets.remove(target)) {
            sendVirtualKey(KeyEvent.ACTION_UP, target)
        }
    }

    private fun stopAllTurbo() {
        turboTasks.keys.toList().forEach { stopTurbo(it) }
    }

    internal fun isTurboActive(sourceKey: Int): Boolean = turboTasks.containsKey(sourceKey)

    internal fun cycleFastForwardSpeed() {
        fastForwardSpeed = when (fastForwardSpeed) {
            1 -> 2
            2 -> 4
            else -> 1
        }
        runCatching { retroView?.frameSpeed = fastForwardSpeed }
        showToast("快进 ${fastForwardSpeed}x")
        controlsView?.invalidate()
    }

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTO_RESTORE_STATE = "auto_restore_state"
        private const val NESTOPIA_CORE_FILE_NAME = "libnestopia_libretro_android.so"
        private const val TAG = "MD3E_FC"
        private const val PREF_TOUCH_CONTROLS_ALPHA = "touch_controls_alpha"
        private const val PREF_HARDWARE_CONTROLS_ALPHA = "hardware_controls_alpha"
        private const val DEFAULT_TOUCH_CONTROLS_ALPHA = 0.70f
        private const val DEFAULT_HARDWARE_CONTROLS_ALPHA = 0.0f
        private const val TURBO_INTERVAL_MS = 70L
        private const val SHORTCUT_TURBO_A_SOURCE = -320001
        private const val SHORTCUT_TURBO_B_SOURCE = -320002
    }
}
