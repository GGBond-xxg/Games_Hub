package com.bond.md3elauncher.emulator.gba

import com.bond.md3elauncher.MainActivity
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings
import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.system.exitProcess

class InternalGbaActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private var controlsView: GbaTouchControlsView? = null
    private var loadingText: TextView? = null
    internal var gameStorage: GameStorage? = null
    private var autoStateRestored = false
    internal var fastForwardSpeed = 1
    private var pausedForMenu = false
    private var skipAutoStateRestore = false
    private var skipSaveRamRestore = false
    private var appliedCheatSlots = 0
    private val appliedCheatCodes = mutableMapOf<Int, String>()
    private var appliedCheatSignature = ""
    private var pendingCheatReapplyOnResume = false
    private var cheatRuntimeRefreshInProgress = false
    private var preCheatStateCaptureInProgress = false
    private var suppressLifecycleSaveRam = false


    private val hardwarePressedKeys = mutableSetOf<Int>()
    private val handledLongKeys = mutableSetOf<Int>()
    private val firedShortcutActions = mutableSetOf<ControllerShortcutAction>()
    private val turboHandler = Handler(Looper.getMainLooper())
    private val turboTasks = mutableMapOf<Int, Runnable>()
    private val turboTargets = mutableMapOf<Int, Int>()
    private val turboDownTargets = mutableSetOf<Int>()

    internal val settingsPrefs: SharedPreferences by lazy {
        getSharedPreferences("internal_gba_settings", Context.MODE_PRIVATE)
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
        skipAutoStateRestore = intent.getBooleanExtra(EXTRA_SKIP_AUTO_STATE_RESTORE, false)
        skipSaveRamRestore = intent.getBooleanExtra(EXTRA_SKIP_SAVE_RAM_RESTORE, false)

        showLoading("正在启动内置 GBA / GB/GBC 模拟器...")
        configureWindow()

        val romUri = intent.getStringExtra(EXTRA_ROM_URI)?.let(Uri::parse)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { "game.gba" }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { fileName }
        val platformLabel = intent.getStringExtra(EXTRA_PLATFORM_LABEL).orEmpty().ifBlank {
            if (fileName.lowercase(Locale.ROOT).endsWith(".gb") || fileName.lowercase(Locale.ROOT).endsWith(".gbc") || fileName.lowercase(Locale.ROOT).endsWith(".sgb")) "GB/GBC" else "GBA"
        }

        if (romUri == null) {
            finishWithMessage("没有收到 $platformLabel ROM")
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
            startRetroView(romFile, title, platformLabel)
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
            // v44：退出/重载/关闭窗口时不再调用 LibretroDroid.destroy()。
            // 现有日志显示 Activity 交替销毁时 native destroy() 可能 SIGSEGV，
            // 真正的 My Boy! 方案会切到 native mGBA backend 后再接管生命周期。
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

        // 固定硬件快捷键已改为“设置 > 系统 > 手柄操作”里的通用配置。
        // 未绑定快捷键的手柄按键继续作为正常游戏输入下发给 core。

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
        controlsView?.hideMenu()
        return true
    }

    private fun startRetroView(romFile: File, title: String, platformLabel: String) {
        val coreFile = File(applicationInfo.nativeLibraryDir, CORE_FILE_NAME)
        if (!coreFile.exists()) {
            finishWithMessage("内置 GBA 核心不存在：${coreFile.absolutePath}")
            return
        }

        val systemDir = File(filesDir, "internal_gba/system").apply { mkdirs() }
        val savesDir = File(filesDir, "internal_gba/saves").apply { mkdirs() }
        val storage = buildGameStorage(romFile, title).also {
            it.root.mkdirs()
            gameStorage = it
        }

        val data = GLRetroViewData(applicationContext).apply {
            coreFilePath = coreFile.absolutePath
            gameFilePath = romFile.absolutePath
            systemDirectory = systemDir.absolutePath
            savesDirectory = savesDir.absolutePath
            saveRAMState = if (skipSaveRamRestore) null else storage.saveRamFile.takeIf { it.exists() && it.length() > 0L }?.readBytes()
            variables = gbaVariables()
            preferLowLatencyAudio = true
            skipDuplicateFrames = true
        }

        val view = GLRetroView(this, data).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        retroView = view
        lifecycle.addObserver(view)

        val controls = GbaTouchControlsView(this).also { controlsView = it }
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
        startAutoRestoreWatcher(view, storage)
        Toast.makeText(this, "内置 $platformLabel：$title", Toast.LENGTH_SHORT).show()
    }

    private fun gbaVariables(): Array<Variable> = arrayOf(
        Variable("mgba_sgb_borders", "OFF"),
        Variable("mgba_skip_bios", "ON"),
        Variable("mgba_use_bios", "OFF")
    )

    private fun prepareRomFile(uri: Uri, fileName: String): File {
        val safeName = sanitizeFileName(fileName).ifBlank { "game.gba" }
        val dir = File(cacheDir, "internal_gba/roms").apply { mkdirs() }

        if (safeName.lowercase(Locale.ROOT).endsWith(".zip")) {
            extractGbaFromZip(uri, dir, safeName)?.let { return it }
        }

        val outFile = File(dir, safeName.ensureGbaFamilyExtension())
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开 ROM 输入流" }
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile
    }

    private fun extractGbaFromZip(uri: Uri, dir: File, archiveName: String): File? {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开 ZIP 输入流" }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val entryName = sanitizeFileName(entry.name.substringAfterLast('/')).ifBlank {
                        archiveName.substringBeforeLast('.', archiveName) + ".gba"
                    }
                    val lower = entryName.lowercase(Locale.ROOT)
                    if (!isSupportedGbaFamilyRomName(lower)) {
                        continue
                    }
                    val outFile = File(dir, entryName.ensureGbaFamilyExtension())
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                    return outFile
                }
            }
        }
        return null
    }

    private fun startAutoRestoreWatcher(view: GLRetroView, storage: GameStorage) {
        autoStateRestored = false
        lifecycleScope.launch {
            view.getGLRetroEvents().collect { event ->
                if (!autoStateRestored && event == GLRetroView.GLRetroEvents.FrameRendered) {
                    autoStateRestored = true
                    // v55：关闭作弊码后的正确顺序固定为：
                    // 快速存档 -> 关闭作弊码 -> 冷重启当前 GBA 游戏 -> 快速读档。
                    // 之前 v51-v54 冷重启后优先读 cheat_clean_before_enable.state，
                    // 所以会回到开启作弊码前的位置，表现为“重启后没有读取最新快捷存档”。
                    val restoreQuickAfterCheatRestart = intent.getBooleanExtra(EXTRA_RESTORE_QUICK_STATE_AFTER_CHEAT_RESTART, false)
                    val restoreCleanAfterCheatRestart = intent.getBooleanExtra(EXTRA_RESTORE_CHEAT_CLEAN_STATE_ON_BOOT, false)
                    when {
                        restoreQuickAfterCheatRestart -> restoreQuickStateAfterCheatRestart(
                            view = view,
                            storage = storage,
                            fallbackToCleanState = restoreCleanAfterCheatRestart
                        )
                        restoreCleanAfterCheatRestart -> restoreCheatCleanState(view, storage)
                        !skipAutoStateRestore -> restoreInitialState(view, storage)
                    }
                    if (intent.getBooleanExtra(EXTRA_APPLY_CHEATS_AFTER_BOOT, false)) {
                        scheduleCheatApplyAfterBoot()
                    }
                }
            }
        }
    }

    private suspend fun restoreInitialState(view: GLRetroView, storage: GameStorage) {
        val candidates = listOf(
            "快捷存档" to storage.quickStateFile,
            "存档 1" to storage.slotStateFile(1)
        ).filter { it.second.exists() && it.second.length() > 0L }

        for ((label, file) in candidates) {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes == null || bytes.isEmpty()) continue
            val ok = runCatching { view.unserializeState(bytes) }.getOrDefault(false)
            if (ok) {
                Toast.makeText(this, "已自动读取 $label", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    private suspend fun restoreCheatCleanState(view: GLRetroView, storage: GameStorage) {
        val file = storage.cheatCleanStateFile
        val bytes = withContext(Dispatchers.IO) {
            runCatching { file.takeIf { it.exists() && it.length() > 0L }?.readBytes() }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "cold restart has no clean state to restore")
            Toast.makeText(this, "已冷重启并关闭金手指", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = runCatching { view.unserializeState(bytes) }.getOrDefault(false)
        Log.d(TAG, "cold restart restore clean state ok=$ok bytes=${bytes.size}")
        if (ok) {
            Toast.makeText(this, "已恢复到开启作弊前状态", Toast.LENGTH_SHORT).show()
            if (loadCustomCheats().none { it.enabled }) {
                withContext(Dispatchers.IO) { runCatching { file.delete() } }
            }
        } else {
            Toast.makeText(this, "已冷重启并关闭金手指", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun restoreQuickStateAfterCheatRestart(
        view: GLRetroView,
        storage: GameStorage,
        fallbackToCleanState: Boolean
    ) {
        val file = storage.quickStateFile
        val bytes = withContext(Dispatchers.IO) {
            runCatching { file.takeIf { it.exists() && it.length() > 0L }?.readBytes() }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "cheat restart quick load skipped: quick state missing")
            if (fallbackToCleanState) {
                restoreCheatCleanState(view, storage)
            } else {
                Toast.makeText(this, "已重启并关闭金手指，但没有快捷存档", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val ok = unserializeStateWithBootRetries(view, bytes, label = "cheat quick state")
        Log.d(TAG, "cheat restart quick load ok=$ok bytes=${bytes.size} mtime=${file.lastModified()}")
        if (ok) {
            Toast.makeText(this, "已快速读档并关闭金手指", Toast.LENGTH_SHORT).show()
            if (loadCustomCheats().none { it.enabled }) {
                withContext(Dispatchers.IO) { runCatching { storage.cheatCleanStateFile.delete() } }
            }
        } else if (fallbackToCleanState) {
            Log.w(TAG, "cheat restart quick load failed, fallback to clean state")
            restoreCheatCleanState(view, storage)
        } else {
            Toast.makeText(this, "已重启并关闭金手指，快捷读档失败", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun unserializeStateWithBootRetries(
        view: GLRetroView,
        bytes: ByteArray,
        label: String
    ): Boolean {
        val retryDelays = listOf(0L, 420L, 900L, 1500L)
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

    internal fun deleteQuickState() {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        deleteStateFile(storage.quickStateFile, "快捷存档")
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

    private fun saveStateToFile(file: File, label: String) {
        val view = retroView ?: return showToast("模拟器还没准备好")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    val state = view.serializeState()
                    require(state.isNotEmpty()) { "状态数据为空" }
                    file.writeBytes(state)
                    // 不再额外调用 serializeSRAM。当前 LibretroDroid 在 Activity 重载/Surface 销毁附近
                    // 调用 serializeSRAM 可能触发 native SIGSEGV，状态存档本身已足够恢复进度。
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

    private fun deleteStateFile(file: File, label: String) {
        if (!file.exists()) {
            showToast("没有 $label")
            return
        }
        val ok = runCatching { file.delete() }.getOrDefault(false)
        controlsView?.invalidate()
        showToast(if (ok) "已删除 $label" else "删除 $label 失败")
    }

    private fun persistSaveRamIfSafe(reason: String) {
        // v41：禁用自动 serializeSRAM。
        // 日志显示 crash 发生在 Surface 已被隐藏/销毁后，GLThread 里调用
        // libretrodroid::LibretroDroid::serializeSRAM() 触发 native SIGSEGV。
        // native 崩溃不能被 Kotlin runCatching 捕获，所以这里彻底跳过自动 SRAM 序列化，
        // 避免退出、重载、onPause/onDestroy 时闪退。即时存档 serializeState 仍然保留。
        if (suppressLifecycleSaveRam || isFinishing || isDestroyed) {
            Log.d(TAG, "skip SRAM persist reason=$reason suppress=$suppressLifecycleSaveRam finishing=$isFinishing destroyed=$isDestroyed")
            return
        }
        Log.d(TAG, "skip SRAM persist reason=$reason disabled to avoid native serializeSRAM crash")
    }


    internal fun softRestartGameNoExit() {
        val view = retroView ?: return showToast("模拟器还没准备好")
        stopAllTurbo()
        controlsView?.releaseAll()
        controlsView?.hideMenu()
        runCatching {
            view.queueEvent {
                runCatching { LibretroDroid.reset() }
                    .onFailure { Log.e(TAG, "soft reset GBA core failed", it) }
            }
        }.onSuccess {
            showToast("已重启当前 GBA 游戏")
        }.onFailure {
            Log.e(TAG, "queue soft reset GBA failed", it)
            showToast("重启游戏失败")
        }
    }

    internal fun restartGameFresh() {
        coldRestartGbaProcess(
            message = "正在冷重启游戏...",
            restoreCleanState = false,
            applyCheatsAfterBoot = false
        )
    }

    private fun coldRestartGbaProcess(
        message: String,
        restoreCleanState: Boolean,
        applyCheatsAfterBoot: Boolean,
        restoreQuickStateAfterCheatRestart: Boolean = false
    ) {
        Log.w(TAG, "cold restart gba process restoreClean=$restoreCleanState restoreQuick=$restoreQuickStateAfterCheatRestart applyCheats=$applyCheatsAfterBoot")
        showToast(message)
        suppressLifecycleSaveRam = true
        stopAllTurbo()
        controlsView?.releaseAll()
        controlsView = null

        // v48：不能在 :internal_gba 进程内先启动新的 InternalGbaActivity 再 kill 自己。
        // 新旧 InternalGbaActivity 同属 :internal_gba 进程，v47 会把刚创建的新游戏一起杀掉，表现为关闭后直接退回桌面/主界面。
        // 正确做法：先把重启请求交给主进程 MainActivity，由主进程延迟重新打开 GBA；随后当前 :internal_gba 自杀。
        val relay = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_GBA_COLD_RESTART_RELAY
            putExtras(intent)
            putExtra(EXTRA_SKIP_AUTO_STATE_RESTORE, true)
            putExtra(EXTRA_SKIP_SAVE_RAM_RESTORE, true)
            putExtra(EXTRA_RESTORE_CHEAT_CLEAN_STATE_ON_BOOT, restoreCleanState)
            putExtra(EXTRA_RESTORE_QUICK_STATE_AFTER_CHEAT_RESTART, restoreQuickStateAfterCheatRestart)
            putExtra(EXTRA_APPLY_CHEATS_AFTER_BOOT, applyCheatsAfterBoot)
            putExtra(MainActivity.EXTRA_GBA_RELAY_REQUEST_ID, System.currentTimeMillis())
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }

        runCatching {
            Log.w(TAG, "relay cold restart to main process")
            startActivity(relay)
            overridePendingTransition(0, 0)
        }.onFailure {
            Log.e(TAG, "relay cold restart failed", it)
        }

        finish()
        overridePendingTransition(0, 0)
        turboHandler.postDelayed({
            Log.w(TAG, "kill old internal_gba process after relay")
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 320L)
    }

    private fun buildGameStorage(romFile: File, title: String): GameStorage {
        val displayName = sanitizeFileName(title.substringBeforeLast('.', title)).ifBlank { "GBA" }
        val digestSource = "${romFile.name}:${romFile.length()}"
        val key = sha1(digestSource).take(16) + "_" + displayName.take(36)
        val root = File(filesDir, "internal_gba/game_data/$key")
        return GameStorage(key, displayName, root)
    }

    private fun sha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun stateTime(file: File): String {
        if (!file.exists()) return "空"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    private fun String.ensureGbaFamilyExtension(): String {
        val lower = lowercase(Locale.ROOT)
        return if (isSupportedGbaFamilyRomName(lower)) this else "${this}.gba"
    }

    private fun isSupportedGbaFamilyRomName(lowerName: String): Boolean =
        lowerName.endsWith(".gba") ||
            lowerName.endsWith(".agb") ||
            lowerName.endsWith(".gb") ||
            lowerName.endsWith(".gbc") ||
            lowerName.endsWith(".sgb") ||
            lowerName.endsWith(".bin")

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
        if (pendingCheatReapplyOnResume) {
            pendingCheatReapplyOnResume = false
            scheduleCheatReapplyAfterResume()
        }
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
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
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

    internal fun isTurboActive(sourceKey: Int): Boolean = turboTasks.containsKey(sourceKey)

    private fun stopAllTurbo() {
        turboTasks.keys.toList().forEach { stopTurbo(it) }
    }

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

    private fun applyConfiguredCheats(
        showMessage: Boolean,
        force: Boolean = false,
        softReset: Boolean = false
    ) {
        val view = retroView ?: run {
            if (showMessage) showToast("模拟器还没准备好")
            return
        }
        val active = buildActiveCheats()
        val signature = cheatSignature(active)

        if (!force && signature == appliedCheatSignature) {
            if (showMessage) showToast(if (active.isEmpty()) "没有开启的金手指" else "金手指已是最新")
            return
        }

        runCatching {
            val payloads = active.mapNotNull { cheat ->
                val lines = cheatCodeLines(cheat.code).filter { it.isNotBlank() }
                val code = lines.joinToString("+").trim('+')
                if (code.isBlank()) null else RuntimeCheatPayload(cheat.label, code, lines.size)
            }
            val totalLineCount = payloads.sumOf { it.lineCount }
            val logCode = payloads.joinToString(" || ") { "${it.label}:${it.code}" }
            Log.d(
                TAG,
                "queue cheats active=${payloads.size} lines=$totalLineCount force=$force softReset=$softReset slots=${payloads.size} code=$logCode"
            )

            // v56：多个作弊码不要再全部合并到同一个 setCheat(0)。
            // mGBA/libretro 对一个 slot 里塞多组 GameShark/CodeBreaker 码时，容易只让前一组生效；
            // 典型表现就是：先开穿墙再开闪光，穿墙正常但闪光不生效；退出重进后又正常。
            // 现在改为「一个自定义作弊码 = 一个 libretro cheat slot」，每个作弊码内部多行仍用 + 合并。
            // 例如：
            // slot 0 = 穿墙：509197D3+542975F4+78DA95DF+44018CB4
            // slot 1 = 闪光：XXXXXXXX+YYYYYYYY
            view.queueEvent {
                runCatching {
                    LibretroDroid.resetCheat()
                    payloads.forEachIndexed { slot, payload ->
                        LibretroDroid.setCheat(slot, true, payload.code)
                        Log.d(TAG, "set cheat slot[$slot] ${payload.label}=${payload.code}")
                    }
                    // v44+：不再通过 LibretroDroid.reset() 触发软重置。
                    // 关闭作弊码仍走稳定的快速存档 + 冷重启方案。
                    Log.d(TAG, "cheats applied on emu thread active=${payloads.size} lines=$totalLineCount slots=${payloads.size} softResetIgnored=$softReset")
                }.onFailure { Log.e(TAG, "apply cheats on emu thread failed", it) }
            }

            appliedCheatCodes.clear()
            payloads.forEachIndexed { slot, payload -> appliedCheatCodes[slot] = payload.code }
            appliedCheatSlots = payloads.size
            appliedCheatSignature = signature
            pendingCheatReapplyOnResume = false
        }.onSuccess {
            if (showMessage) {
                val activeLineCount = active.flatMap { cheatCodeLines(it.code) }.size
                val msg = when {
                    active.isEmpty() -> "已下发关闭金手指"
                    softReset -> "已下发 ${active.size} 个金手指 / ${activeLineCount} 行并软重置"
                    else -> "已下发 ${active.size} 个金手指 / ${activeLineCount} 行"
                }
                showToast(msg)
            }
        }.onFailure {
            if (showMessage) showToast("金手指应用失败：${it.message ?: "未知错误"}")
            Log.e(TAG, "queue cheats failed", it)
        }
    }

    private fun refreshCheatsWithCoreReset(showMessage: Boolean, reason: String) {
        val active = buildActiveCheats()
        val activeLines = active.flatMap { cheatCodeLines(it.code) }.filter { it.isNotBlank() }
        Log.d(TAG, "cold refresh request reason=$reason active=${active.size} lines=${activeLines.size}")

        if (cheatRuntimeRefreshInProgress) {
            if (showMessage) showToast("正在快速存档并重启游戏")
            return
        }
        cheatRuntimeRefreshInProgress = true

        // v51：不再继续做 native CheatManager。关闭/删除已开启作弊码时明确走稳定方案：
        // 先自动执行一次快捷存档，再冷重启当前 GBA 游戏，让穿墙这类已经 patch 到 core 的代码彻底失效。
        lifecycleScope.launch {
            val quickSaved = saveQuickStateBeforeCheatRestart()
            Log.d(TAG, "quick save before cheat restart result=$quickSaved reason=$reason")
            if (quickSaved) {
                showToast("已快速存档，正在重启游戏关闭金手指")
            } else if (showMessage) {
                showToast("快速存档失败，继续重启游戏关闭金手指")
            }

            // v47/v48：日志确认 resetCheat + soft reset + clean state restore 都执行成功，
            // 但穿墙码仍然保留；说明这类 GameShark/CodeBreaker 会在当前 native core 内留下 patch。
            // 因此关闭/删除作弊码时冷重启 :internal_gba，重新创建 mGBA core。
            coldRestartGbaProcess(
                message = if (active.isEmpty()) "已关闭金手指，正在重启当前游戏并快速读档" else "正在重启游戏、快速读档并应用剩余金手指",
                restoreCleanState = true,
                applyCheatsAfterBoot = active.isNotEmpty(),
                restoreQuickStateAfterCheatRestart = true
            )
        }
    }

    private suspend fun saveQuickStateBeforeCheatRestart(): Boolean {
        val view = retroView ?: run {
            Log.w(TAG, "quick save before cheat restart skipped: retroView null")
            return false
        }
        val storage = gameStorage ?: run {
            Log.w(TAG, "quick save before cheat restart skipped: storage null")
            return false
        }
        val state = serializeStateBestEffort(view, timeoutMs = 1500L)
        if (state == null || state.isEmpty()) {
            Log.w(TAG, "quick save before cheat restart skipped: state empty")
            return false
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                storage.quickStateFile.parentFile?.mkdirs()
                FileOutputStream(storage.quickStateFile).use { output ->
                    output.write(state)
                    output.fd.sync()
                }
            }.onFailure {
                Log.w(TAG, "quick save before cheat restart write failed", it)
            }.isSuccess
        }.also { ok ->
            if (ok) controlsView?.invalidate()
        }
    }

    private fun scheduleCheatApplyAfterBoot() {
        val activeCount = buildActiveCheats().size
        Log.d(TAG, "schedule cheat apply after boot active=$activeCount")
        showToast(if (activeCount > 0) "正在应用金手指..." else "正在关闭金手指...")
        listOf(650L, 1600L).forEachIndexed { index, delay ->
            turboHandler.postDelayed({
                if (!isFinishing && retroView != null) {
                    Log.d(TAG, "delayed cheat apply #$index active=${buildActiveCheats().size}")
                    applyConfiguredCheats(showMessage = index == 1, force = true, softReset = false)
                }
            }, delay)
        }
    }

    private fun scheduleCheatApplyThenRestore(view: GLRetroView, storage: GameStorage) {
        val activeCount = buildActiveCheats().size
        Log.d(TAG, "schedule cheat apply then restore active=$activeCount")
        showToast(if (activeCount > 0) "正在下发金手指并重置核心..." else "正在关闭金手指并重置核心...")

        // 第一步：先下发当前金手指，再软重置 core。穿墙这类代码在 mGBA 上通常需要 reset 后才挂上。
        turboHandler.postDelayed({
            if (!isFinishing && retroView != null) {
                Log.d(TAG, "boot cheat apply before restore active=${buildActiveCheats().size}")
                applyConfiguredCheats(showMessage = true, force = true, softReset = true)
            }
        }, 420L)

        // 第二步：等 reset 后核心跑起来，再恢复 Y 重载前的即时状态。
        turboHandler.postDelayed({
            if (!isFinishing && retroView != null) {
                lifecycleScope.launch {
                    Log.d(TAG, "restore state after cheat reset")
                    if (!skipAutoStateRestore) restoreInitialState(view, storage)
                    // 第三步：恢复状态后再补发一次，不再 reset，避免状态读档覆盖后丢失挂载。
                    turboHandler.postDelayed({
                        if (!isFinishing && retroView != null) {
                            Log.d(TAG, "reapply cheats after state restore active=${buildActiveCheats().size}")
                            applyConfiguredCheats(showMessage = false, force = true, softReset = false)
                        }
                    }, 450L)
                }
            }
        }, 1450L)
    }

    private fun runOnRetroThreadBlocking(view: GLRetroView, block: () -> Unit) {
        if (Thread.currentThread().name.startsWith("GLThread")) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        view.queueEvent {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        val completed = latch.await(1800L, TimeUnit.MILLISECONDS)
        if (!completed) error("模拟器线程没有及时响应，请返回游戏后再试")
        failure?.let { throw it }
    }

    private fun scheduleCheatReapplyAfterResume() {
        // 从菜单返回后，补发两次，避免打开菜单时 frameSpeed=0 导致核心还没跑帧。
        listOf(80L, 320L).forEach { delay ->
            turboHandler.postDelayed({
                if (!isFinishing && retroView != null) {
                    applyConfiguredCheats(showMessage = false, force = true, softReset = false)
                }
            }, delay)
        }
    }

    private fun applyCheatsWithSoftReset() {
        // v45：不再启动第二个 InternalGbaActivity。需要手动刷新时，
        // 在当前 core 内保存状态、清空作弊、软重置、恢复状态。
        refreshCheatsWithCoreReset(showMessage = true, reason = "manualRefresh")
    }

    private suspend fun serializeStateBestEffort(view: GLRetroView, timeoutMs: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            val result = arrayOfNulls<ByteArray>(1)
            val latch = CountDownLatch(1)
            val worker = Thread {
                runCatching { view.serializeState() }
                    .onSuccess { bytes -> if (bytes.isNotEmpty()) result[0] = bytes }
                    .onFailure { Log.w(TAG, "serialize state failed", it) }
                latch.countDown()
            }.apply { isDaemon = true }
            worker.start()
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                result[0]
            } else {
                Log.w(TAG, "serialize state timeout after ${timeoutMs}ms")
                null
            }
        }

    private fun clearRuntimeCheats(view: GLRetroView) {
        runCatching {
            view.queueEvent { runCatching { LibretroDroid.resetCheat() } }
        }
        appliedCheatCodes.clear()
        appliedCheatSlots = 0
        appliedCheatSignature = ""
    }

    private fun cheatSignature(items: List<RuntimeCheat>): String =
        items.joinToString("|") { "${it.label}=${cheatCodeLines(it.code).joinToString(",")}" }

    private fun restartGameForCheatReload(message: String) {
        // v44：禁用 Activity 重载方案，避免 LibretroDroid.destroy() native 崩溃。
        showToast(message)
        applyConfiguredCheats(showMessage = false, force = true, softReset = false)
        Log.w(TAG, "restartGameForCheatReload ignored; native cheat engine path is required")
    }

    private fun buildActiveCheats(): List<RuntimeCheat> {
        // 默认不再内置任何作弊码；只应用用户自己添加并开启的金手指。
        return loadCustomCheats()
            .filter { it.enabled }
            .mapNotNull { item ->
                val code = normalizeCheatCode(item.code)
                if (code.isBlank()) null else RuntimeCheat(item.name, code)
            }
    }

    private fun parseLegacyCustomCheatBlocks(value: String): List<String> = value
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .split(Regex("""\n\s*\n"""))
        .map { normalizeCheatCode(it) }
        .filter { it.isNotBlank() }

    internal fun normalizeCheatCode(value: String): String =
        tokenizeCheatCode(value).joinToString("+")

    private fun cheatCodeLines(value: String): List<String> {
        val tokens = tokenizeCheatCode(value)
        if (tokens.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val first = tokens[index]
            val second = tokens.getOrNull(index + 1)
            if (first.length == 8 && second != null && second.length in 4..8) {
                lines += "$first+$second"
                index += 2
            } else {
                lines += first
                index += 1
            }
        }
        return lines
    }

    private fun tokenizeCheatCode(value: String): List<String> {
        val tokens = mutableListOf<String>()
        value
            .replace('＋', '+')
            .replace('，', ' ')
            .replace(',', ' ')
            .replace(';', ' ')
            .lines()
            .forEach { rawLine ->
                val line = rawLine.substringBefore("//").substringBefore("#").trim()
                if (line.isBlank()) return@forEach
                line.split(Regex("""[+\s]+"""))
                    .filter { it.isNotBlank() }
                    .forEach { part ->
                        val clean = part
                            .filter { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' }
                            .uppercase(Locale.ROOT)
                        when {
                            clean.length == 12 -> {
                                tokens += clean.substring(0, 8)
                                tokens += clean.substring(8, 12)
                            }
                            clean.length == 16 -> {
                                tokens += clean.substring(0, 8)
                                tokens += clean.substring(8, 16)
                            }
                            clean.isNotBlank() -> tokens += clean
                        }
                    }
            }
        return tokens
    }

    internal fun loadCustomCheats(): MutableList<CustomCheat> {
        val raw = settingsPrefs.getString(PREF_CUSTOM_CHEATS, "").orEmpty().trim()
        if (raw.isBlank()) return mutableListOf()
        if (raw.startsWith("[")) {
            return runCatching {
                val array = org.json.JSONArray(raw)
                MutableList(array.length()) { index ->
                    val obj = array.getJSONObject(index)
                    CustomCheat(
                        id = obj.optString("id", "custom_$index"),
                        name = obj.optString("name", "自定义${index + 1}"),
                        type = obj.optString("type", "CodeBreaker/GameShark"),
                        code = obj.optString("code", ""),
                        enabled = obj.optBoolean("enabled", false)
                    )
                }
            }.getOrDefault(mutableListOf())
        }
        val legacy = parseLegacyCustomCheatBlocks(raw).mapIndexed { index, code ->
            CustomCheat("legacy_$index", "自定义${index + 1}", "CodeBreaker/GameShark", code, enabled = true)
        }.toMutableList()
        saveCustomCheats(legacy)
        return legacy
    }

    private fun saveCustomCheats(items: List<CustomCheat>) {
        val array = org.json.JSONArray()
        items.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("type", item.type)
            obj.put("code", item.code)
            obj.put("enabled", item.enabled)
            array.put(obj)
        }
        settingsPrefs.edit().putString(PREF_CUSTOM_CHEATS, array.toString()).apply()
    }

    internal fun showAddCustomCheatDialog() {
        val density = resources.displayMetrics.density
        val padding = (18f * density).roundToInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "作弊码名称，例如：经验"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val typeInput = EditText(this).apply {
            hint = "作弊码类型，例如：CodeBreaker / GameShark"
            inputType = InputType.TYPE_CLASS_TEXT
            setText("CodeBreaker/GameShark")
            setSingleLine(true)
        }
        val codeInput = EditText(this).apply {
            hint = "作弊码，例如：\nXXXXXXXX YYYY\nXXXXXXXX YYYY"
            minLines = 4
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        layout.addView(nameInput)
        layout.addView(typeInput)
        layout.addView(codeInput)
        AlertDialog.Builder(this)
            .setTitle("添加作弊码")
            .setView(layout)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty().ifBlank { "自定义作弊码" }
                val type = typeInput.text?.toString()?.trim().orEmpty().ifBlank { "CodeBreaker/GameShark" }
                val code = codeInput.text?.toString()?.trim().orEmpty()
                if (normalizeCheatCode(code).isBlank()) {
                    showToast("作弊码不能为空")
                    return@setPositiveButton
                }
                val list = loadCustomCheats()
                list += CustomCheat("custom_${System.currentTimeMillis()}", name, type, code, enabled = false)
                saveCustomCheats(list)
                controlsView?.refreshCustomCheatIndex()
                controlsView?.invalidate()
                showToast("已添加：$name")
            }
            .show()
    }

    private fun captureCleanStateBeforeFirstCheat(onDone: () -> Unit) {
        val view = retroView
        val storage = gameStorage
        if (view == null || storage == null) {
            onDone()
            return
        }
        if (preCheatStateCaptureInProgress) {
            onDone()
            return
        }
        preCheatStateCaptureInProgress = true
        lifecycleScope.launch {
            Log.d(TAG, "capture clean state before first cheat")
            val state = serializeStateBestEffort(view, timeoutMs = 1000L)
            if (state != null && state.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        storage.cheatCleanStateFile.parentFile?.mkdirs()
                        storage.cheatCleanStateFile.writeBytes(state)
                    }.onFailure { Log.w(TAG, "write clean cheat state failed", it) }
                }
                Log.d(TAG, "clean state captured bytes=${state.size}")
            } else {
                Log.w(TAG, "clean state capture skipped: state empty")
            }
            preCheatStateCaptureInProgress = false
            onDone()
        }
    }

    internal fun toggleCustomCheat(index: Int) {
        val list = loadCustomCheats()
        if (index !in list.indices) return
        val item = list[index]
        val wasAnyActive = list.any { it.enabled }
        val enabledNow = !item.enabled
        list[index] = item.copy(enabled = enabledNow)
        saveCustomCheats(list)
        appliedCheatSignature = ""
        if (enabledNow) {
            showToast("${item.name} 已开启，正在尝试即时下发")
            val applyAction = {
                applyConfiguredCheats(showMessage = false, force = true, softReset = false)
            }
            if (!wasAnyActive) {
                // 第一次开启前保存一个干净状态。后面关闭穿墙这类 ROM/指令 patch 码时，
                // 不能恢复关闭瞬间的状态，因为它已经被作弊码污染；要恢复这个干净状态。
                captureCleanStateBeforeFirstCheat(applyAction)
            } else {
                applyAction()
            }
        } else {
            showToast("${item.name} 已关闭，正在彻底清理")
            refreshCheatsWithCoreReset(showMessage = true, reason = "toggleOff:${item.name}")
        }
        controlsView?.invalidate()
    }

    internal fun deleteCustomCheat(index: Int) {
        val list = loadCustomCheats()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        saveCustomCheats(list)
        appliedCheatSignature = ""
        controlsView?.refreshCustomCheatIndex()
        controlsView?.invalidate()
        showToast("已删除：${removed.name}")
        if (removed.enabled) refreshCheatsWithCoreReset(showMessage = true, reason = "delete:${removed.name}")
    }

    private fun toggleCheat(key: String, label: String) {
        val enabled = !settingsPrefs.getBoolean(key, false)
        settingsPrefs.edit().putBoolean(key, enabled).apply()
        appliedCheatSignature = ""
        showToast("$label ${if (enabled) "已开启，正在尝试即时下发" else "已关闭，正在尝试即时下发"}")
        applyConfiguredCheats(showMessage = false, force = true, softReset = false)
        controlsView?.invalidate()
    }








    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLATFORM_LABEL = "platform_label"
        private const val EXTRA_SKIP_AUTO_STATE_RESTORE = "skip_auto_state_restore"
        private const val EXTRA_SKIP_SAVE_RAM_RESTORE = "skip_save_ram_restore"
        private const val EXTRA_CHEAT_RELOAD_STATE = "cheat_reload_state"
        private const val EXTRA_APPLY_CHEATS_AFTER_BOOT = "apply_cheats_after_boot"
        private const val EXTRA_RESTORE_CHEAT_CLEAN_STATE_ON_BOOT = "restore_cheat_clean_state_on_boot"
        private const val EXTRA_RESTORE_QUICK_STATE_AFTER_CHEAT_RESTART = "restore_quick_state_after_cheat_restart"
        private const val CORE_FILE_NAME = "libmgba_libretro_android.so"
        private const val TAG = "MD3E_GBA"
        private const val VIRTUAL_FAST_FORWARD = -100001
        private const val VIRTUAL_MENU = -100002
        private const val VIRTUAL_TURBO_A = -100003
        private const val VIRTUAL_TURBO_B = -100004
        private const val VIRTUAL_QUICK_SAVE = -100005
        private const val VIRTUAL_QUICK_LOAD = -100006
        private const val VIRTUAL_EXIT = -100007
        private const val PREF_TOUCH_CONTROLS_ALPHA = "touch_controls_alpha"
        private const val PREF_HARDWARE_CONTROLS_ALPHA = "hardware_controls_alpha"
        private const val PREF_CUSTOM_CHEATS = "custom_cheats"
        private const val PREF_CUSTOM_TOUCH_BUTTONS = "custom_touch_buttons"
        private const val PREF_TOUCH_LAYOUT_RECTS = "touch_layout_rects"
        private const val DEFAULT_TOUCH_CONTROLS_ALPHA = 0.72f
        private const val DEFAULT_HARDWARE_CONTROLS_ALPHA = 0.0f
        private const val MAX_STATE_SLOTS = 5
        private const val TURBO_INTERVAL_MS = 70L
        private const val SHORTCUT_TURBO_A_SOURCE = -310001
        private const val SHORTCUT_TURBO_B_SOURCE = -310002
        private const val CHEAT_WALK_THROUGH_WALLS = "cheat_walk_through_walls"
        private const val CHEAT_INF_MONEY = "cheat_inf_money"
        private const val CHEAT_MASTER_BALL_PC = "cheat_master_ball_pc"
        private const val CHEAT_RARE_CANDY_PC = "cheat_rare_candy_pc"
        private const val CHEAT_INF_PP = "cheat_inf_pp"
        private const val CHEAT_EXP_BOOST = "cheat_exp_boost"
        private const val LEAF_GREEN_V10_MASTER_CODE = "0000BE99+000A+1003DAE6+0007"
        private val MAIN_MENU_ITEMS = listOf("存档", "虚拟按键设置", "作弊", "重置", "重启游戏", "退出游戏")
        private val VIRTUAL_KEY_MENU_ITEMS = listOf("透明度设置", "虚拟键编辑")
        private val VIRTUAL_EDITOR_ITEMS = listOf("添加自定键", "放大当前键", "缩小当前键", "保存并返回游戏", "重置布局", "取消编辑 / 返回游戏")
        private val CHEAT_ITEMS = emptyList<CheatItem>()
    }
}
