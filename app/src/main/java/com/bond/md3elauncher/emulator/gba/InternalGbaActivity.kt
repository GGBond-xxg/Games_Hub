package com.bond.md3elauncher.emulator.gba

import com.bond.md3elauncher.MainActivity
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
    private var gameStorage: GameStorage? = null
    private var autoStateRestored = false
    private var fastForwardSpeed = 1
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
    private val turboHandler = Handler(Looper.getMainLooper())
    private val turboTasks = mutableMapOf<Int, Runnable>()
    private val turboTargets = mutableMapOf<Int, Int>()
    private val turboDownTargets = mutableSetOf<Int>()

    private val settingsPrefs: SharedPreferences by lazy {
        getSharedPreferences("internal_gba_settings", Context.MODE_PRIVATE)
    }

    private var touchControlsAlpha: Float = DEFAULT_TOUCH_CONTROLS_ALPHA
        set(value) {
            val fixed = value.coerceIn(0f, 1f)
            field = fixed
            settingsPrefs.edit().putFloat(PREF_TOUCH_CONTROLS_ALPHA, fixed).apply()
            controlsView?.invalidate()
        }

    private var hardwareControlsAlpha: Float = DEFAULT_HARDWARE_CONTROLS_ALPHA
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

        showLoading("正在启动内置 GBA 模拟器...")
        configureWindow()

        val romUri = intent.getStringExtra(EXTRA_ROM_URI)?.let(Uri::parse)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { "game.gba" }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { fileName }

        if (romUri == null) {
            finishWithMessage("没有收到 GBA ROM")
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

        if (handleGlobalHardwareShortcuts(event)) {
            return true
        }

        controlsView?.handleHardwareMenuKey(event)?.let { consumed ->
            if (consumed) return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    if (event.repeatCount == 0) cycleFastForwardSpeed()
                    controlsView?.markHardwareControllerUsed()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    controlsView?.markHardwareControllerUsed()
                    startTurbo(keyCode, KeyEvent.KEYCODE_BUTTON_A)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    controlsView?.markHardwareControllerUsed()
                    startTurbo(keyCode, KeyEvent.KEYCODE_BUTTON_B)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    controlsView?.markHardwareControllerUsed()
                    if (event.repeatCount > 0 && handledLongKeys.add(keyCode)) {
                        saveQuickState()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    controlsView?.markHardwareControllerUsed()
                    if (event.repeatCount > 0 && handledLongKeys.add(keyCode)) {
                        loadQuickState(hideMenuAfterLoad = false)
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_START -> {
                    controlsView?.markHardwareControllerUsed()
                }
                else -> Unit
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_X -> {
                    stopTurbo(keyCode)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    stopTurbo(keyCode)
                    return true
                }
            }
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

    private fun handleGlobalHardwareShortcuts(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (controlsView?.isMenuOpen() == true) return false

        val selectHeld = hardwarePressedKeys.contains(KeyEvent.KEYCODE_BUTTON_SELECT)
        val xHeld = hardwarePressedKeys.contains(KeyEvent.KEYCODE_BUTTON_X)
        if (selectHeld && xHeld) {
            exitGame()
            return true
        }
        return false
    }

    private fun startRetroView(romFile: File, title: String) {
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

        val controls = GbaTouchControlsView().also { controlsView = it }
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
        Toast.makeText(this, "内置 GBA：$title", Toast.LENGTH_SHORT).show()
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

        val outFile = File(dir, safeName.ensureGbaExtension())
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
                    if (!lower.endsWith(".gba") && !lower.endsWith(".agb") && !lower.endsWith(".bin")) {
                        continue
                    }
                    val outFile = File(dir, entryName.ensureGbaExtension())
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
                    // v47：关闭穿墙这类 ROM/指令 patch 码时，必须冷重启 :internal_gba 进程。
                    // 冷重启后优先恢复开启作弊前捕获的干净状态，避免把已 patch 的 dirty state 读回来。
                    if (intent.getBooleanExtra(EXTRA_RESTORE_CHEAT_CLEAN_STATE_ON_BOOT, false)) {
                        restoreCheatCleanState(view, storage)
                    } else if (!skipAutoStateRestore) {
                        restoreInitialState(view, storage)
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

    private fun saveQuickState() {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        saveStateToFile(storage.quickStateFile, "快捷存档")
    }

    private fun loadQuickState(hideMenuAfterLoad: Boolean) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        loadStateFromFile(storage.quickStateFile, "快捷存档", hideMenuAfterLoad)
    }

    private fun deleteQuickState() {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        deleteStateFile(storage.quickStateFile, "快捷存档")
    }

    private fun saveSlotState(slot: Int) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        saveStateToFile(storage.slotStateFile(slot), "存档 $slot")
    }

    private fun loadSlotState(slot: Int, hideMenuAfterLoad: Boolean = true) {
        val storage = gameStorage ?: return showToast("游戏存档目录未准备好")
        loadStateFromFile(storage.slotStateFile(slot), "存档 $slot", hideMenuAfterLoad)
    }

    private fun deleteSlotState(slot: Int) {
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

    private fun restartGameFresh() {
        coldRestartGbaProcess(
            message = "正在冷重启游戏...",
            restoreCleanState = false,
            applyCheatsAfterBoot = false
        )
    }

    private fun coldRestartGbaProcess(
        message: String,
        restoreCleanState: Boolean,
        applyCheatsAfterBoot: Boolean
    ) {
        Log.w(TAG, "cold restart gba process restoreClean=$restoreCleanState applyCheats=$applyCheatsAfterBoot")
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

    private fun stateTime(file: File): String {
        if (!file.exists()) return "空"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    private fun String.ensureGbaExtension(): String {
        val lower = lowercase(Locale.ROOT)
        return if (lower.endsWith(".gba") || lower.endsWith(".agb") || lower.endsWith(".bin")) this else "${this}.gba"
    }

    private fun sanitizeFileName(value: String): String =
        value.map { ch -> if (ch.isLetterOrDigit() || ch in listOf('.', '_', '-', ' ', '(', ')', '[', ']')) ch else '_' }
            .joinToString("")
            .take(180)
            .trim()

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun exitGame() {
        suppressLifecycleSaveRam = true
        finish()
    }

    private fun pauseRetroForMenu() {
        if (pausedForMenu) return
        stopAllTurbo()
        controlsView?.releaseAll()
        releaseAllGameInputs()
        pausedForMenu = true
        runCatching { retroView?.frameSpeed = 0 }
    }

    private fun resumeRetroAfterMenu() {
        if (!pausedForMenu) return
        pausedForMenu = false
        runCatching { retroView?.frameSpeed = fastForwardSpeed.coerceAtLeast(1) }
        configureWindow()
        if (pendingCheatReapplyOnResume) {
            pendingCheatReapplyOnResume = false
            scheduleCheatReapplyAfterResume()
        }
    }

    private fun releaseAllGameInputs() {
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

    private fun sendVirtualKey(action: Int, keyCode: Int) {
        if (keyCode < 0) return
        runCatching { retroView?.sendKeyEvent(action, keyCode, 0) }
    }

    private fun startTurbo(sourceKey: Int, targetKey: Int) {
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

    private fun stopTurbo(sourceKey: Int) {
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

    private fun cycleFastForwardSpeed() {
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
            val activeLines = active.flatMap { cheatCodeLines(it.code) }.filter { it.isNotBlank() }
            val mergedCode = activeLines.joinToString("+").trim('+')
            Log.d(
                TAG,
                "queue cheats active=${active.size} lines=${activeLines.size} force=$force softReset=$softReset code=$mergedCode"
            )

            // mGBA/libretro 会把传入的 + 或空白重新转换成「XXXXXXXX XXXXXXXX」格式，
            // RetroArch 通常也是把同一个金手指的多行代码合成一个 + 分隔字符串下发。
            // 因此这里不再每一行 setCheat 一次，而是把当前开启的所有行合并成一个 cheat set。
            // 例如 My Boy! 里的：
            // 509197D3 542975F4
            // 78DA95DF 44018CB4
            // 会下发为：509197D3+542975F4+78DA95DF+44018CB4。
            view.queueEvent {
                runCatching {
                    LibretroDroid.resetCheat()
                    if (mergedCode.isNotBlank()) {
                        LibretroDroid.setCheat(0, true, mergedCode)
                        Log.d(TAG, "set cheat merged[0]=$mergedCode")
                    }
                    // v44：不再通过 LibretroDroid.reset() 触发软重置。
                    // 真正的即时开关会由后续 native mGBA CheatManager 处理。
                    Log.d(TAG, "cheats applied on emu thread active=${active.size} lines=${activeLines.size} softResetIgnored=$softReset")
                }.onFailure { Log.e(TAG, "apply cheats on emu thread failed", it) }
            }

            appliedCheatCodes.clear()
            if (mergedCode.isNotBlank()) appliedCheatCodes[0] = mergedCode
            appliedCheatSlots = if (mergedCode.isBlank()) 0 else 1
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
                message = if (active.isEmpty()) "已关闭金手指，正在重启当前游戏" else "正在重启游戏并应用剩余金手指",
                restoreCleanState = true,
                applyCheatsAfterBoot = active.isNotEmpty()
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
                storage.quickStateFile.writeBytes(state)
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

    private fun normalizeCheatCode(value: String): String =
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

    private fun loadCustomCheats(): MutableList<CustomCheat> {
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

    private fun showAddCustomCheatDialog() {
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

    private fun toggleCustomCheat(index: Int) {
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

    private fun deleteCustomCheat(index: Int) {
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

    private enum class MenuPage { MAIN, SAVE, LOAD, DELETE_SAVE, VIRTUAL_KEYS, VIRTUAL_ALPHA, VIRTUAL_EDITOR, CHEATS, CUSTOM_CHEATS, CUSTOM_CHEAT_DELETE, RESET_CONFIRM }

    private enum class SlotListMode { SAVE, LOAD, DELETE }

    private data class GameStorage(
        val key: String,
        val displayName: String,
        val root: File
    ) {
        val saveRamFile: File get() = File(root, "save_ram.srm")
        val quickStateFile: File get() = File(root, "quick.state")
        val cheatReloadStateFile: File get() = File(root, "cheat_reload.state")
        val cheatCleanStateFile: File get() = File(root, "cheat_clean_before_enable.state")
        fun slotStateFile(slot: Int): File = File(root, "slot_$slot.state")
    }

    private data class TouchButton(
        val id: String,
        val label: String,
        val keyCodes: Set<Int>,
        val rect: RectF,
        val circle: Boolean = false
    ) {
        val keyCode: Int get() = keyCodes.firstOrNull() ?: -999999
    }

    private data class MenuRow(
        val index: Int,
        val rect: RectF
    )

    private data class CustomTouchButton(
        val id: String,
        val label: String,
        val circle: Boolean,
        val keyCodes: Set<Int>
    )

    private inner class GbaTouchControlsView : View(this@InternalGbaActivity) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * resources.displayMetrics.density
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT
        }

        private val buttons = mutableListOf<TouchButton>()
        private val dpadRects = linkedMapOf<Int, RectF>()
        private val pointerKeys = mutableMapOf<Int, Set<Int>>()
        private val pressedKeys = mutableSetOf<Int>()
        private val menuRows = mutableListOf<MenuRow>()
        private val dpadOuter = RectF()
        private val dpadCenter = RectF()
        private val leftStickOuter = RectF()
        private val leftStickCenter = RectF()
        private val rightStickOuter = RectF()
        private val rightStickCenter = RectF()
        private val menuIconRect = RectF()
        private val menuPanelRect = RectF()
        private val customDeleteButtonRect = RectF()
        private val cheatAddButtonRect = RectF()
        private val cheatEnableButtonRect = RectF()
        private val cheatResetButtonRect = RectF()
        private val cheatDeleteButtonRect = RectF()
        private val stateSaveButtonRect = RectF()
        private val stateDeleteButtonRect = RectF()
        private val stateLoadButtonRect = RectF()
        private var dpadDeadZone = 0f
        private var largeTextSize = 18f
        private var smallTextSize = 13f
        private var menuVisible = false
        private var menuPage = MenuPage.MAIN
        private var editorMode = false
        private var editorPanelOpen = false
        private var editorBallPointerId: Int? = null
        private var editorBallMoved = false
        private var editorBallLastX = 0f
        private var editorBallLastY = 0f
        private val editorBallRect = RectF()
        private val editorPanelRect = RectF()
        private val editorPanelCloseRect = RectF()
        private val editorPanelRows = mutableListOf<MenuRow>()
        private var menuIndex = 0
        private var saveSlotIndex = 0
        private var loadSlotIndex = 0
        private var deleteSlotIndex = 0
        private var cheatIndex = 0
        private var customCheatIndex = 0
        private var deleteCustomCheatIndex = 0
        private var virtualSettingsIndex = 0
        private var editorIndex = 0
        private var hardwareMode = false
        private var selectedEditId: String? = null
        private var editingPointerId: Int? = null
        private var lastEditX = 0f
        private var lastEditY = 0f
        private val customButtons = mutableListOf<CustomTouchButton>()
        private val layoutOverrides = mutableMapOf<String, RectF>()

        init {
            customButtons.addAll(loadCustomButtons())
            layoutOverrides.putAll(loadLayoutOverrides())
            isClickable = true
            isFocusable = false
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildLayout(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val shouldDrawControls = editorMode || !menuVisible || menuPage == MenuPage.VIRTUAL_KEYS || menuPage == MenuPage.VIRTUAL_ALPHA
            if (shouldDrawControls) {
                val alpha = controlsAlphaForCurrentMode()
                if (alpha > 0f) {
                    drawDpad(canvas, alpha)
                    drawAnalogStick(canvas, leftStickOuter, leftStickCenter, "L", alpha)
                    drawAnalogStick(canvas, rightStickOuter, rightStickCenter, "R", alpha)
                    buttons.forEach { button ->
                        val pressed = when {
                            button.keyCodes.contains(VIRTUAL_FAST_FORWARD) -> fastForwardSpeed > 1
                            button.keyCodes.contains(VIRTUAL_MENU) -> menuVisible
                            button.keyCodes.contains(VIRTUAL_TURBO_A) -> turboTasks.containsKey(VIRTUAL_TURBO_A)
                            button.keyCodes.contains(VIRTUAL_TURBO_B) -> turboTasks.containsKey(VIRTUAL_TURBO_B)
                            else -> button.keyCodes.any { pressedKeys.contains(it) }
                        }
                        drawButton(canvas, button, pressed, alpha)
                    }
                    drawMenuIcon(canvas, alpha)
                    if (editorMode) drawEditSelection(canvas)
                }
            }

            if (editorMode) {
                drawEditorFloatingUi(canvas)
            } else if (menuVisible) {
                drawMenuPanel(canvas)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (hardwareMode && event.actionMasked == MotionEvent.ACTION_DOWN) {
                hardwareMode = false
                invalidate()
            }

            if (editorMode) {
                return handleEditorModeTouch(event)
            }

            if (handleMenuTouch(event)) {
                return true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> updatePointer(event, event.actionIndex)
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) updatePointer(event, i)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> releasePointer(event, event.actionIndex, cancelled = false)
                MotionEvent.ACTION_CANCEL -> {
                    pointerKeys.clear()
                    syncPressedKeys()
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDetachedFromWindow() {
            releaseAll()
            super.onDetachedFromWindow()
        }

        fun releaseAll() {
            pointerKeys.clear()
            syncPressedKeys()
        }

        fun markHardwareControllerUsed() {
            if (!hardwareMode) {
                releaseAll()
                hardwareMode = true
                invalidate()
            }
        }

        fun hideMenu() {
            menuVisible = false
            menuPage = MenuPage.MAIN
            menuIndex = 0
            releaseAll()
            this@InternalGbaActivity.resumeRetroAfterMenu()
            invalidate()
        }

        fun isMenuOpen(): Boolean = menuVisible

        fun handleHardwareMenuKey(event: KeyEvent): Boolean {
            val keyCode = event.keyCode
            val isMenuKey = keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
                keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL

            if (event.action == KeyEvent.ACTION_DOWN) markHardwareControllerUsed()

            if (editorMode) {
                return handleEditorHardwareKey(event)
            }

            if (!menuVisible && isMenuKey) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    openMenu()
                }
                return true
            }

            if (!menuVisible) return false

            if (event.action == KeyEvent.ACTION_UP) return true
            if (event.action != KeyEvent.ACTION_DOWN) return true

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> moveSelection(-1)
                KeyEvent.KEYCODE_DPAD_DOWN -> moveSelection(1)
                KeyEvent.KEYCODE_DPAD_LEFT -> adjustCurrentPage(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> adjustCurrentPage(1)
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SPACE -> confirmSelection()
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK -> backFromMenu()
                KeyEvent.KEYCODE_BUTTON_X -> deleteSelectionIfAvailable()
                KeyEvent.KEYCODE_BUTTON_Y -> loadSelectionIfAvailable()
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_THUMBL -> if (event.repeatCount == 0) hideMenu()
                else -> return true
            }
            invalidate()
            return true
        }

        private fun handleEditorHardwareKey(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_UP) return true
            if (event.action != KeyEvent.ACTION_DOWN) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (editorPanelOpen) editorIndex = (editorIndex - 1 + VIRTUAL_EDITOR_ITEMS.size) % VIRTUAL_EDITOR_ITEMS.size
                    invalidate()
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (editorPanelOpen) editorIndex = (editorIndex + 1) % VIRTUAL_EDITOR_ITEMS.size
                    invalidate()
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (editorPanelOpen) editorIndex = (editorIndex - 1 + VIRTUAL_EDITOR_ITEMS.size) % VIRTUAL_EDITOR_ITEMS.size else resizeSelectedEditable(-1)
                    invalidate()
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (editorPanelOpen) editorIndex = (editorIndex + 1) % VIRTUAL_EDITOR_ITEMS.size else resizeSelectedEditable(1)
                    invalidate()
                }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    if (editorPanelOpen) confirmEditorPanelSelection() else editorPanelOpen = true
                    invalidate()
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> confirmReturnFromVirtualEditor()
                KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    if (event.repeatCount == 0) editorPanelOpen = !editorPanelOpen
                    invalidate()
                }
                else -> Unit
            }
            return true
        }

        private fun openMenu() {
            menuVisible = true
            menuPage = MenuPage.MAIN
            menuIndex = 0
            releaseAll()
            this@InternalGbaActivity.pauseRetroForMenu()
            invalidate()
        }

        private fun startVirtualEditor() {
            menuVisible = false
            menuPage = MenuPage.MAIN
            editorMode = true
            editorPanelOpen = false
            editorIndex = 0
            selectedEditId = null
            editingPointerId = null
            releaseAll()
            ensureEditorBall()
            this@InternalGbaActivity.pauseRetroForMenu()
            invalidate()
        }

        private fun ensureEditorBall() {
            if (editorBallRect.width() > 0f && editorBallRect.height() > 0f) return
            val dp = resources.displayMetrics.density
            val size = 52f * dp
            val x = (width.toFloat() - size - 18f * dp).coerceAtLeast(18f * dp)
            val y = (height.toFloat() * 0.30f).coerceAtLeast(18f * dp)
            editorBallRect.set(x, y, x + size, y + size)
        }

        private fun exitVirtualEditor(save: Boolean) {
            if (save) {
                saveLayoutOverrides(width.toFloat(), height.toFloat())
                showToast("虚拟键布局已保存")
            } else {
                customButtons.clear()
                customButtons.addAll(loadCustomButtons())
                layoutOverrides.clear()
                layoutOverrides.putAll(loadLayoutOverrides())
                rebuildLayout(width.toFloat(), height.toFloat())
            }
            editorMode = false
            editorPanelOpen = false
            editorBallPointerId = null
            selectedEditId = null
            editingPointerId = null
            menuVisible = false
            menuPage = MenuPage.MAIN
            releaseAll()
            this@InternalGbaActivity.resumeRetroAfterMenu()
            invalidate()
        }

        private fun confirmReturnFromVirtualEditor() {
            AlertDialog.Builder(this@InternalGbaActivity)
                .setTitle("返回游戏？")
                .setMessage("是否放弃本次未保存的虚拟键位置和大小修改，并返回游戏？")
                .setNegativeButton("不是", null)
                .setPositiveButton("是") { _, _ -> exitVirtualEditor(save = false) }
                .show()
        }

        private fun handleMenuTouch(event: MotionEvent): Boolean {
            if (editorMode) return false
            val x = event.getX(event.actionIndex.coerceAtMost(event.pointerCount - 1))
            val y = event.getY(event.actionIndex.coerceAtMost(event.pointerCount - 1))
            if (event.actionMasked == MotionEvent.ACTION_DOWN && menuIconRect.contains(x, y)) {
                if (menuVisible) hideMenu() else openMenu()
                return true
            }
            if (!menuVisible) return false
            if (menuPage == MenuPage.VIRTUAL_EDITOR && (editingPointerId != null || !menuPanelRect.contains(x, y))) {
                return handleEditorTouch(event)
            }
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
            if (!menuPanelRect.contains(x, y)) {
                hideMenu()
                return true
            }
            if (menuPage == MenuPage.SAVE) {
                when {
                    stateSaveButtonRect.contains(x, y) -> {
                        saveSelectedState()
                        invalidate()
                        return true
                    }
                    stateDeleteButtonRect.contains(x, y) -> {
                        deleteSelectedState()
                        invalidate()
                        return true
                    }
                    stateLoadButtonRect.contains(x, y) -> {
                        loadSelectedState()
                        invalidate()
                        return true
                    }
                }
            }
            if (menuPage == MenuPage.CHEATS) {
                val custom = loadCustomCheats()
                if (cheatAddButtonRect.contains(x, y)) {
                    showAddCustomCheatDialog()
                    invalidate()
                    return true
                }
                if (custom.isNotEmpty() && cheatEnableButtonRect.contains(x, y)) {
                    toggleCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                    invalidate()
                    return true
                }
                if (custom.isNotEmpty() && cheatDeleteButtonRect.contains(x, y)) {
                    deleteCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                    invalidate()
                    return true
                }
            }
            if (menuPage == MenuPage.CUSTOM_CHEATS && customDeleteButtonRect.contains(x, y)) {
                enterPage(MenuPage.CUSTOM_CHEAT_DELETE)
                invalidate()
                return true
            }
            val row = menuRows.firstOrNull { it.rect.contains(x, y) }
            if (row != null) {
                setCurrentIndex(row.index)
                if (menuPage != MenuPage.CHEATS && menuPage != MenuPage.SAVE) confirmSelection()
                invalidate()
            }
            return true
        }

        private fun handleEditorModeTouch(event: MotionEvent): Boolean {
            ensureEditorBall()
            val action = event.actionMasked
            val index = event.actionIndex.coerceAtMost(event.pointerCount - 1)
            val x = event.getX(index)
            val y = event.getY(index)
            val pointerId = event.getPointerId(index)

            if (handleEditorFloatingTouch(event, pointerId, x, y)) return true
            return handleEditorTouch(event)
        }

        private fun handleEditorFloatingTouch(event: MotionEvent, pointerId: Int, x: Float, y: Float): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (editorPanelOpen) {
                        if (editorPanelCloseRect.contains(x, y)) {
                            editorPanelOpen = false
                            invalidate()
                            return true
                        }
                        val row = editorPanelRows.firstOrNull { it.rect.contains(x, y) }
                        if (row != null) {
                            editorIndex = row.index
                            confirmEditorPanelSelection()
                            invalidate()
                            return true
                        }
                        if (editorPanelRect.contains(x, y)) return true
                    }
                    if (editorBallRect.contains(x, y)) {
                        editorBallPointerId = pointerId
                        editorBallMoved = false
                        editorBallLastX = x
                        editorBallLastY = y
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val active = editorBallPointerId ?: return false
                    val activeIndex = event.findPointerIndex(active)
                    if (activeIndex >= 0) {
                        val nx = event.getX(activeIndex)
                        val ny = event.getY(activeIndex)
                        val dx = nx - editorBallLastX
                        val dy = ny - editorBallLastY
                        if (kotlin.math.abs(dx) > 1f || kotlin.math.abs(dy) > 1f) {
                            editorBallMoved = true
                            editorBallRect.offset(dx, dy)
                            clampRect(editorBallRect)
                            editorBallLastX = nx
                            editorBallLastY = ny
                            invalidate()
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pointerId == editorBallPointerId || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        if (!editorBallMoved && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                            editorPanelOpen = !editorPanelOpen
                        }
                        editorBallPointerId = null
                        editorBallMoved = false
                        invalidate()
                        return true
                    }
                }
            }
            return false
        }

        private fun confirmEditorPanelSelection() {
            when (editorIndex.coerceIn(0, VIRTUAL_EDITOR_ITEMS.size - 1)) {
                0 -> showAddCustomButtonDialog()
                1 -> resizeSelectedEditable(1)
                2 -> resizeSelectedEditable(-1)
                3 -> exitVirtualEditor(save = true)
                4 -> resetVirtualKeyLayout()
                5 -> confirmReturnFromVirtualEditor()
            }
        }

        private fun controlsAlphaForCurrentMode(): Float = when {
            editorMode -> max(hardwareControlsAlpha, 0.72f)
            menuVisible && menuPage == MenuPage.VIRTUAL_EDITOR -> max(hardwareControlsAlpha, 0.55f)
            menuVisible && (menuPage == MenuPage.VIRTUAL_KEYS || menuPage == MenuPage.VIRTUAL_ALPHA) -> hardwareControlsAlpha
            hardwareMode -> hardwareControlsAlpha
            else -> touchControlsAlpha
        }.coerceIn(0f, 1f)

        private fun currentCount(): Int = when (menuPage) {
            MenuPage.MAIN -> MAIN_MENU_ITEMS.size
            MenuPage.SAVE -> MAX_STATE_SLOTS + 1
            MenuPage.LOAD -> MAX_STATE_SLOTS
            MenuPage.DELETE_SAVE -> MAX_STATE_SLOTS + 1
            MenuPage.VIRTUAL_KEYS -> VIRTUAL_KEY_MENU_ITEMS.size
            MenuPage.VIRTUAL_ALPHA -> 1
            MenuPage.VIRTUAL_EDITOR -> VIRTUAL_EDITOR_ITEMS.size
            MenuPage.CHEATS -> loadCustomCheats().size
            MenuPage.CUSTOM_CHEATS -> loadCustomCheats().size + 1
            MenuPage.CUSTOM_CHEAT_DELETE -> max(1, loadCustomCheats().size)
            MenuPage.RESET_CONFIRM -> 2
        }

        private fun currentIndex(): Int = when (menuPage) {
            MenuPage.MAIN -> menuIndex
            MenuPage.SAVE -> saveSlotIndex
            MenuPage.LOAD -> loadSlotIndex
            MenuPage.DELETE_SAVE -> deleteSlotIndex
            MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex
            MenuPage.VIRTUAL_ALPHA -> 0
            MenuPage.VIRTUAL_EDITOR -> editorIndex
            MenuPage.CHEATS -> cheatIndex
            MenuPage.CUSTOM_CHEATS -> customCheatIndex
            MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex
            MenuPage.RESET_CONFIRM -> menuIndex.coerceIn(0, 1)
        }

        private fun setCurrentIndex(index: Int) {
            val fixed = index.coerceIn(0, max(0, currentCount() - 1))
            when (menuPage) {
                MenuPage.MAIN -> menuIndex = fixed
                MenuPage.SAVE -> saveSlotIndex = fixed
                MenuPage.LOAD -> loadSlotIndex = fixed
                MenuPage.DELETE_SAVE -> deleteSlotIndex = fixed
                MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex = fixed
                MenuPage.VIRTUAL_ALPHA -> Unit
                MenuPage.VIRTUAL_EDITOR -> editorIndex = fixed
                MenuPage.CHEATS -> cheatIndex = fixed
                MenuPage.CUSTOM_CHEATS -> customCheatIndex = fixed
                MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex = fixed
                MenuPage.RESET_CONFIRM -> menuIndex = fixed
            }
        }

        private fun moveSelection(delta: Int) {
            val count = currentCount()
            if (count <= 0) return
            setCurrentIndex((currentIndex() + delta + count) % count)
        }

        private fun adjustCurrentPage(delta: Int) {
            when (menuPage) {
                MenuPage.VIRTUAL_ALPHA -> {
                    hardwareControlsAlpha = (hardwareControlsAlpha + delta * 0.05f).coerceIn(0f, 1f)
                    showToast("虚拟按键透明度 ${(hardwareControlsAlpha * 100).roundToInt()}%")
                }
                MenuPage.VIRTUAL_EDITOR -> resizeSelectedEditable(delta)
                MenuPage.VIRTUAL_KEYS,
                MenuPage.SAVE,
                MenuPage.LOAD,
                MenuPage.DELETE_SAVE,
                MenuPage.CHEATS,
                MenuPage.CUSTOM_CHEATS,
                MenuPage.CUSTOM_CHEAT_DELETE,
                MenuPage.RESET_CONFIRM,
                MenuPage.MAIN -> moveSelection(delta)
            }
        }

        private fun confirmSelection() {
            when (menuPage) {
                MenuPage.MAIN -> when (menuIndex) {
                    0 -> enterPage(MenuPage.SAVE)
                    1 -> enterPage(MenuPage.VIRTUAL_KEYS)
                    2 -> enterPage(MenuPage.CHEATS)
                    3 -> enterPage(MenuPage.RESET_CONFIRM)
                    4 -> this@InternalGbaActivity.exitGame()
                }
                MenuPage.SAVE -> saveSelectedState()
                MenuPage.LOAD -> {
                    val file = gameStorage?.slotStateFile(loadSlotIndex + 1)
                    if (file != null && file.exists() && file.length() > 0L) {
                        this@InternalGbaActivity.loadSlotState(loadSlotIndex + 1, hideMenuAfterLoad = true)
                    } else {
                        showToast("存档 ${loadSlotIndex + 1} 为空")
                    }
                }
                MenuPage.DELETE_SAVE -> deleteLegacyDeleteState()
                MenuPage.VIRTUAL_KEYS -> when (virtualSettingsIndex) {
                    0 -> enterPage(MenuPage.VIRTUAL_ALPHA)
                    1 -> startVirtualEditor()
                }
                MenuPage.VIRTUAL_ALPHA -> Unit
                MenuPage.VIRTUAL_EDITOR -> when (editorIndex) {
                    0 -> showAddCustomButtonDialog()
                    1 -> resizeSelectedEditable(1)
                    2 -> resizeSelectedEditable(-1)
                    3 -> { saveLayoutOverrides(width.toFloat(), height.toFloat()); showToast("虚拟键布局已保存") }
                    4 -> resetVirtualKeyLayout()
                    5 -> confirmReturnFromVirtualEditor()
                }
                MenuPage.CHEATS -> {
                    val custom = loadCustomCheats()
                    if (custom.isEmpty()) {
                        showToast("先在右上角添加自定义作弊码")
                    } else {
                        toggleCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                    }
                }
                MenuPage.CUSTOM_CHEATS -> {
                    when (customCheatIndex) {
                        0 -> showAddCustomCheatDialog()
                        else -> toggleCustomCheat(customCheatIndex - 1)
                    }
                }
                MenuPage.CUSTOM_CHEAT_DELETE -> {
                    val custom = loadCustomCheats()
                    if (custom.isEmpty()) {
                        showToast("没有自定义作弊码")
                    } else {
                        deleteCustomCheat(deleteCustomCheatIndex.coerceIn(0, custom.lastIndex))
                    }
                }
                MenuPage.RESET_CONFIRM -> {
                    if (menuIndex == 0) this@InternalGbaActivity.restartGameFresh() else backFromMenu()
                }
            }
        }

        private fun enterPage(page: MenuPage) {
            menuPage = page
            menuIndex = 0
            when (page) {
                MenuPage.SAVE -> saveSlotIndex = 0
                MenuPage.LOAD -> loadSlotIndex = 0
                MenuPage.DELETE_SAVE -> deleteSlotIndex = 0
                MenuPage.CHEATS -> cheatIndex = 0
                MenuPage.CUSTOM_CHEATS -> customCheatIndex = 0
                MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex = 0
                MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex = 0
                MenuPage.VIRTUAL_EDITOR -> editorIndex = 0
                else -> Unit
            }
            invalidate()
        }

        private fun backFromMenu() {
            when (menuPage) {
                MenuPage.MAIN -> hideMenu()
                MenuPage.VIRTUAL_ALPHA, MenuPage.VIRTUAL_EDITOR -> {
                    menuPage = MenuPage.VIRTUAL_KEYS
                    virtualSettingsIndex = 0
                    invalidate()
                }
                MenuPage.CUSTOM_CHEAT_DELETE -> {
                    menuPage = MenuPage.CUSTOM_CHEATS
                    customCheatIndex = 0
                    invalidate()
                }
                MenuPage.CUSTOM_CHEATS -> {
                    menuPage = MenuPage.CHEATS
                    cheatIndex = 0
                    invalidate()
                }
                else -> {
                    menuPage = MenuPage.MAIN
                    menuIndex = 0
                    invalidate()
                }
            }
        }

        private fun deleteSelectionIfAvailable() {
            when (menuPage) {
                MenuPage.SAVE -> deleteSelectedState()
                MenuPage.DELETE_SAVE -> deleteLegacyDeleteState()
                MenuPage.CHEATS -> {
                    val custom = loadCustomCheats()
                    if (custom.isNotEmpty()) deleteCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                }
                MenuPage.CUSTOM_CHEAT_DELETE -> {
                    val custom = loadCustomCheats()
                    if (custom.isNotEmpty()) deleteCustomCheat(deleteCustomCheatIndex.coerceIn(0, custom.lastIndex))
                }
                else -> Unit
            }
        }

        private fun saveSelectedState() {
            if (saveSlotIndex >= MAX_STATE_SLOTS) {
                this@InternalGbaActivity.saveQuickState()
            } else {
                this@InternalGbaActivity.saveSlotState(saveSlotIndex + 1)
            }
        }

        private fun loadSelectedState() {
            if (saveSlotIndex >= MAX_STATE_SLOTS) {
                this@InternalGbaActivity.loadQuickState(hideMenuAfterLoad = true)
                return
            }
            val slot = saveSlotIndex + 1
            val file = gameStorage?.slotStateFile(slot)
            if (file != null && file.exists() && file.length() > 0L) {
                this@InternalGbaActivity.loadSlotState(slot, hideMenuAfterLoad = true)
            } else {
                showToast("存档 $slot 为空")
            }
        }

        private fun deleteSelectedState() {
            if (saveSlotIndex >= MAX_STATE_SLOTS) {
                this@InternalGbaActivity.deleteQuickState()
            } else {
                this@InternalGbaActivity.deleteSlotState(saveSlotIndex + 1)
            }
        }

        private fun deleteLegacyDeleteState() {
            if (deleteSlotIndex == 0) {
                this@InternalGbaActivity.deleteQuickState()
            } else {
                this@InternalGbaActivity.deleteSlotState(deleteSlotIndex)
            }
        }

        private fun loadSelectionIfAvailable() {
            when (menuPage) {
                MenuPage.SAVE -> loadSelectedState()
                MenuPage.LOAD -> {
                    val slot = loadSlotIndex + 1
                    val file = gameStorage?.slotStateFile(slot)
                    if (file != null && file.exists() && file.length() > 0L) {
                        this@InternalGbaActivity.loadSlotState(slot, hideMenuAfterLoad = true)
                    } else {
                        showToast("存档 $slot 为空")
                    }
                }
                MenuPage.CHEATS -> {
                    showToast("已取消应用重载；A 可即时开关，native 引擎开发中")
                }
                else -> Unit
            }
        }

        private fun updatePointer(event: MotionEvent, index: Int) {
            val pointerId = event.getPointerId(index)
            pointerKeys[pointerId] = keysFor(event.getX(index), event.getY(index))
            syncPressedKeys()
        }

        private fun releasePointer(event: MotionEvent, index: Int, cancelled: Boolean) {
            val pointerId = event.getPointerId(index)
            val oldKeys = pointerKeys.remove(pointerId).orEmpty()
            if (!cancelled) {
                when {
                    oldKeys.contains(VIRTUAL_MENU) -> openMenu()
                    oldKeys.contains(VIRTUAL_EXIT) -> this@InternalGbaActivity.exitGame()
                    oldKeys.contains(VIRTUAL_FAST_FORWARD) -> this@InternalGbaActivity.cycleFastForwardSpeed()
                    oldKeys.contains(VIRTUAL_QUICK_SAVE) -> this@InternalGbaActivity.saveQuickState()
                    oldKeys.contains(VIRTUAL_QUICK_LOAD) -> this@InternalGbaActivity.loadQuickState(hideMenuAfterLoad = false)
                }
            }
            syncPressedKeys()
        }

        private fun syncPressedKeys() {
            val flattened = pointerKeys.values.asSequence().flatten().toList()

            val wantsTurboA = flattened.contains(VIRTUAL_TURBO_A)
            val wantsTurboB = flattened.contains(VIRTUAL_TURBO_B)
            if (wantsTurboA) startTurbo(VIRTUAL_TURBO_A, KeyEvent.KEYCODE_BUTTON_A) else stopTurbo(VIRTUAL_TURBO_A)
            if (wantsTurboB) startTurbo(VIRTUAL_TURBO_B, KeyEvent.KEYCODE_BUTTON_B) else stopTurbo(VIRTUAL_TURBO_B)

            val wantsVirtualExit = flattened.contains(KeyEvent.KEYCODE_BUTTON_SELECT) && flattened.contains(VIRTUAL_TURBO_A)
            if (wantsVirtualExit) {
                releaseAllGameInputs()
                pointerKeys.clear()
                exitGame()
                return
            }

            val nextPressed = flattened.asSequence().filter { it >= 0 }.toSet()
            val toRelease = pressedKeys - nextPressed
            val toPress = nextPressed - pressedKeys
            toRelease.forEach { key -> sendVirtualKey(KeyEvent.ACTION_UP, key) }
            toPress.forEach { key -> sendVirtualKey(KeyEvent.ACTION_DOWN, key) }
            pressedKeys.clear()
            pressedKeys.addAll(nextPressed)
            invalidate()
        }

        private fun keysFor(x: Float, y: Float): Set<Int> {
            val hitButtons = buttons.filter { button ->
                if (button.circle) {
                    val dx = x - button.rect.centerX()
                    val dy = y - button.rect.centerY()
                    dx * dx + dy * dy <= button.rect.width() * button.rect.width() / 4f
                } else button.rect.contains(x, y)
            }
            if (hitButtons.isNotEmpty()) return hitButtons.flatMap { it.keyCodes }.toSet()

            val aButton = buttons.firstOrNull { it.keyCode == KeyEvent.KEYCODE_BUTTON_A }
            val bButton = buttons.firstOrNull { it.keyCode == KeyEvent.KEYCODE_BUTTON_B }
            if (aButton != null && bButton != null) {
                val centerX = (aButton.rect.centerX() + bButton.rect.centerX()) / 2f
                val centerY = (aButton.rect.centerY() + bButton.rect.centerY()) / 2f
                val dx = x - centerX
                val dy = y - centerY
                val compositeRadius = aButton.rect.width() * 0.62f
                if (dx * dx + dy * dy <= compositeRadius * compositeRadius) {
                    return setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
                }
            }

            if (rightStickOuter.contains(x, y)) {
                return directionalKeys(x, y, rightStickCenter, rightStickOuter.width() * 0.12f)
            }

            if (dpadOuter.contains(x, y)) {
                return directionalKeys(x, y, dpadCenter, dpadDeadZone)
            }

            if (leftStickOuter.contains(x, y)) {
                return directionalKeys(x, y, leftStickCenter, leftStickOuter.width() * 0.12f)
            }
            return emptySet()
        }

        private fun directionalKeys(x: Float, y: Float, center: RectF, deadZone: Float): Set<Int> {
            val keys = mutableSetOf<Int>()
            val dx = x - center.centerX()
            val dy = y - center.centerY()
            if (dx < -deadZone) keys += KeyEvent.KEYCODE_DPAD_LEFT
            if (dx > deadZone) keys += KeyEvent.KEYCODE_DPAD_RIGHT
            if (dy < -deadZone) keys += KeyEvent.KEYCODE_DPAD_UP
            if (dy > deadZone) keys += KeyEvent.KEYCODE_DPAD_DOWN
            return keys
        }

        private fun loadCustomButtons(): List<CustomTouchButton> {
            val raw = settingsPrefs.getString(PREF_CUSTOM_TOUCH_BUTTONS, "").orEmpty()
            if (raw.isBlank()) return emptyList()
            return raw.lineSequence().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 4) return@mapNotNull null
                val keys = parseTouchMethod(parts[3])
                if (keys.isEmpty()) return@mapNotNull null
                CustomTouchButton(parts[0], parts[1], parts[2] == "circle", keys)
            }.toList()
        }

        private fun saveCustomButtons() {
            val raw = customButtons.joinToString("\n") { item ->
                val style = if (item.circle) "circle" else "pill"
                val method = item.keyCodes.joinToString("+")
                listOf(item.id, item.label, style, method).joinToString("|")
            }
            settingsPrefs.edit().putString(PREF_CUSTOM_TOUCH_BUTTONS, raw).apply()
        }

        private fun loadLayoutOverrides(): MutableMap<String, RectF> {
            val result = mutableMapOf<String, RectF>()
            val raw = settingsPrefs.getString(PREF_TOUCH_LAYOUT_RECTS, "").orEmpty()
            raw.lineSequence().forEach { line ->
                val parts = line.split("|")
                if (parts.size == 5) {
                    val id = parts[0]
                    val l = parts[1].toFloatOrNull()
                    val t = parts[2].toFloatOrNull()
                    val r = parts[3].toFloatOrNull()
                    val b = parts[4].toFloatOrNull()
                    if (l != null && t != null && r != null && b != null) result[id] = RectF(l, t, r, b)
                }
            }
            return result
        }

        private fun saveLayoutOverrides(viewW: Float, viewH: Float) {
            if (viewW <= 0f || viewH <= 0f) return
            saveCustomButtons()
            fun norm(rect: RectF): RectF = RectF(rect.left / viewW, rect.top / viewH, rect.right / viewW, rect.bottom / viewH)
            layoutOverrides["dpad"] = norm(dpadOuter)
            layoutOverrides["left_stick"] = norm(leftStickOuter)
            layoutOverrides["right_stick"] = norm(rightStickOuter)
            layoutOverrides["menu_icon"] = norm(menuIconRect)
            buttons.forEach { button -> layoutOverrides[button.id] = norm(button.rect) }
            val raw = layoutOverrides.entries.joinToString("\n") { (id, rect) ->
                "$id|${rect.left}|${rect.top}|${rect.right}|${rect.bottom}"
            }
            settingsPrefs.edit().putString(PREF_TOUCH_LAYOUT_RECTS, raw).apply()
        }

        private fun applyLayoutOverrides(viewW: Float, viewH: Float) {
            fun applyToRect(id: String, rect: RectF) {
                val saved = layoutOverrides[id] ?: return
                rect.set(saved.left * viewW, saved.top * viewH, saved.right * viewW, saved.bottom * viewH)
            }
            applyToRect("dpad", dpadOuter)
            rebuildDpadRectsFromOuter()
            applyToRect("left_stick", leftStickOuter)
            rebuildStickCenter(leftStickOuter, leftStickCenter)
            applyToRect("right_stick", rightStickOuter)
            rebuildStickCenter(rightStickOuter, rightStickCenter)
            applyToRect("menu_icon", menuIconRect)
            buttons.forEach { applyToRect(it.id, it.rect) }
        }

        private fun rebuildDpadRectsFromOuter() {
            val cellW = dpadOuter.width() / 3f
            val cellH = dpadOuter.height() / 3f
            val cx = dpadOuter.centerX()
            val cy = dpadOuter.centerY()
            dpadCenter.set(cx - cellW / 2f, cy - cellH / 2f, cx + cellW / 2f, cy + cellH / 2f)
            dpadDeadZone = min(cellW, cellH) * 0.26f
            dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(cx - cellW / 2f, dpadOuter.top, cx + cellW / 2f, dpadOuter.top + cellH)
            dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(cx - cellW / 2f, dpadOuter.bottom - cellH, cx + cellW / 2f, dpadOuter.bottom)
            dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(dpadOuter.left, cy - cellH / 2f, dpadOuter.left + cellW, cy + cellH / 2f)
            dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(dpadOuter.right - cellW, cy - cellH / 2f, dpadOuter.right, cy + cellH / 2f)
        }

        private fun rebuildStickCenter(outer: RectF, center: RectF) {
            val r = min(outer.width(), outer.height()) * 0.35f
            center.set(outer.centerX() - r / 2f, outer.centerY() - r / 2f, outer.centerX() + r / 2f, outer.centerY() + r / 2f)
        }

        private fun findEditableIdAt(x: Float, y: Float): String? {
            if (menuIconRect.contains(x, y)) return "menu_icon"
            buttons.asReversed().firstOrNull { button ->
                if (button.circle) {
                    val dx = x - button.rect.centerX()
                    val dy = y - button.rect.centerY()
                    dx * dx + dy * dy <= button.rect.width() * button.rect.width() / 4f
                } else button.rect.contains(x, y)
            }?.let { return it.id }
            if (rightStickOuter.contains(x, y)) return "right_stick"
            if (leftStickOuter.contains(x, y)) return "left_stick"
            if (dpadOuter.contains(x, y)) return "dpad"
            return null
        }

        private fun editableRect(id: String?): RectF? = when (id) {
            "dpad" -> dpadOuter
            "left_stick" -> leftStickOuter
            "right_stick" -> rightStickOuter
            "menu_icon" -> menuIconRect
            null -> null
            else -> buttons.firstOrNull { it.id == id }?.rect
        }

        private fun moveSelectedEditable(dx: Float, dy: Float) {
            val id = selectedEditId ?: return
            val rect = editableRect(id) ?: return
            rect.offset(dx, dy)
            clampRect(rect)
            if (id == "dpad") rebuildDpadRectsFromOuter()
            if (id == "left_stick") rebuildStickCenter(leftStickOuter, leftStickCenter)
            if (id == "right_stick") rebuildStickCenter(rightStickOuter, rightStickCenter)
            invalidate()
        }

        private fun resizeSelectedEditable(delta: Int) {
            val id = selectedEditId ?: return showToast("先在屏幕上选择一个虚拟键")
            val rect = editableRect(id) ?: return
            val scale = if (delta > 0) 1.08f else 0.92f
            val cx = rect.centerX()
            val cy = rect.centerY()
            val minSize = 28f * resources.displayMetrics.density
            // Do not cap the maximum size here. Some handheld layouts need much larger
            // touch targets, so only enforce a minimum size and let the user resize freely.
            val w = max(minSize, rect.width() * scale)
            val h = max(minSize, rect.height() * scale)
            rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            clampRect(rect)
            if (id == "dpad") rebuildDpadRectsFromOuter()
            if (id == "left_stick") rebuildStickCenter(leftStickOuter, leftStickCenter)
            if (id == "right_stick") rebuildStickCenter(rightStickOuter, rightStickCenter)
            invalidate()
        }

        private fun clampRect(rect: RectF) {
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            if (rect.left < 0f) rect.offset(-rect.left, 0f)
            if (rect.top < 0f) rect.offset(0f, -rect.top)
            if (rect.right > w) rect.offset(w - rect.right, 0f)
            if (rect.bottom > h) rect.offset(0f, h - rect.bottom)
        }

        private fun handleEditorTouch(event: MotionEvent): Boolean {
            val action = event.actionMasked
            val index = event.actionIndex.coerceAtMost(event.pointerCount - 1)
            val pointerId = event.getPointerId(index)
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    selectedEditId = findEditableIdAt(x, y)
                    editingPointerId = pointerId
                    lastEditX = x
                    lastEditY = y
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val active = editingPointerId ?: return true
                    val activeIndex = event.findPointerIndex(active)
                    if (activeIndex >= 0) {
                        val x = event.getX(activeIndex)
                        val y = event.getY(activeIndex)
                        moveSelectedEditable(x - lastEditX, y - lastEditY)
                        lastEditX = x
                        lastEditY = y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pointerId == editingPointerId || action == MotionEvent.ACTION_CANCEL) {
                        editingPointerId = null
                    }
                }
            }
            return true
        }

        private fun drawEditSelection(canvas: Canvas) {
            val alpha = controlsAlphaForCurrentMode().coerceAtLeast(0.35f)
            val selected = selectedEditId
            fun drawRect(id: String, rect: RectF, circle: Boolean = false) {
                val isSelected = id == selected
                strokePaint.strokeWidth = if (isSelected) 3.5f * resources.displayMetrics.density else 1.5f * resources.displayMetrics.density
                strokePaint.color = if (isSelected) Color.argb((240 * alpha).roundToInt(), 255, 214, 90) else Color.argb((110 * alpha).roundToInt(), 255, 255, 255)
                if (circle) canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, strokePaint)
                else canvas.drawRoundRect(rect, min(rect.width(), rect.height()) / 6f, min(rect.width(), rect.height()) / 6f, strokePaint)
            }
            drawRect("dpad", dpadOuter)
            drawRect("left_stick", leftStickOuter, circle = true)
            drawRect("right_stick", rightStickOuter, circle = true)
            drawRect("menu_icon", menuIconRect)
            buttons.forEach { drawRect(it.id, it.rect, it.circle) }
            strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
        }

        private fun resetVirtualKeyLayout() {
            layoutOverrides.clear()
            if (!editorMode) settingsPrefs.edit().remove(PREF_TOUCH_LAYOUT_RECTS).apply()
            selectedEditId = null
            rebuildLayout(width.toFloat(), height.toFloat())
            showToast("已恢复默认虚拟键布局")
            invalidate()
        }

        private fun showAddCustomButtonDialog() {
            val dp = resources.displayMetrics.density
            val layout = LinearLayout(this@InternalGbaActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20f * dp).roundToInt(), (8f * dp).roundToInt(), (20f * dp).roundToInt(), 0)
            }
            val nameInput = EditText(this@InternalGbaActivity).apply {
                hint = "按键显示名称，例如：A+B"
                setSingleLine(true)
            }
            val styleInput = EditText(this@InternalGbaActivity).apply {
                hint = "样式：circle 或 pill"
                setText("circle")
                setSingleLine(true)
            }
            val methodInput = EditText(this@InternalGbaActivity).apply {
                hint = "方法：A+B / SELECT+X / 快存 / 快读 / 快进 / 退出"
                setSingleLine(true)
            }
            layout.addView(nameInput)
            layout.addView(styleInput)
            layout.addView(methodInput)
            AlertDialog.Builder(this@InternalGbaActivity)
                .setTitle("添加自定虚拟键")
                .setMessage("添加后会先放到屏幕中间，可在虚拟键编辑里拖动位置，左右键调整大小。")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加") { _, _ ->
                    val label = nameInput.text?.toString()?.trim().orEmpty().ifBlank { "自定" }
                    val style = styleInput.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    val keys = parseTouchMethod(methodInput.text?.toString().orEmpty())
                    if (keys.isEmpty()) {
                        showToast("方法无法识别")
                    } else {
                        val id = "custom_${System.currentTimeMillis()}"
                        val isCircle = style.contains("circle") || style.contains("圆")
                        customButtons += CustomTouchButton(id, label.take(8), isCircle, keys)
                        val w = if (isCircle) 56f * resources.displayMetrics.density else 82f * resources.displayMetrics.density
                        val h = if (isCircle) 56f * resources.displayMetrics.density else 42f * resources.displayMetrics.density
                        buttons += TouchButton(id, label.take(8), keys, RectF(width / 2f - w / 2f, height / 2f - h / 2f, width / 2f + w / 2f, height / 2f + h / 2f), isCircle)
                        selectedEditId = id
                        showToast("已添加自定键：$label")
                        invalidate()
                    }
                }
                .show()
        }

        private fun parseTouchMethod(raw: String): Set<Int> {
            if (raw.isBlank()) return emptySet()
            return raw.replace("＋", "+").replace(",", "+").split("+").mapNotNull { tokenRaw ->
                tokenRaw.trim().toIntOrNull() ?: when (tokenRaw.trim().uppercase(Locale.ROOT)) {
                    "A", "确认" -> KeyEvent.KEYCODE_BUTTON_A
                    "B", "取消" -> KeyEvent.KEYCODE_BUTTON_B
                    "X", "连发A", "连续确认" -> VIRTUAL_TURBO_A
                    "Y", "连发B", "连续取消" -> VIRTUAL_TURBO_B
                    "L", "L1" -> KeyEvent.KEYCODE_BUTTON_L1
                    "R", "R1" -> KeyEvent.KEYCODE_BUTTON_R1
                    "START", "开始" -> KeyEvent.KEYCODE_BUTTON_START
                    "SELECT", "选择" -> KeyEvent.KEYCODE_BUTTON_SELECT
                    "快存", "快捷保存", "SAVE" -> VIRTUAL_QUICK_SAVE
                    "快读", "快捷读取", "LOAD" -> VIRTUAL_QUICK_LOAD
                    "快进", "FF", "FAST" -> VIRTUAL_FAST_FORWARD
                    "菜单", "MENU" -> VIRTUAL_MENU
                    "退出", "EXIT" -> VIRTUAL_EXIT
                    else -> null
                }
            }.toSet()
        }


        private fun rebuildLayout(width: Float, height: Float) {
            buttons.clear()
            dpadRects.clear()
            menuRows.clear()
            val density = resources.displayMetrics.density
            fun dp(value: Float) = value * density
            fun pillButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, w: Float, h: Float) {
                buttons += TouchButton(id, label, setOf(keyCode), RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
            }

            val margin = max(dp(14f), min(width, height) * 0.035f)
            val primarySize = min(dp(126f), height * 0.29f).coerceAtLeast(min(dp(92f), height * 0.24f))
            val cell = primarySize / 3f
            val clusterCy = height - margin - primarySize / 2f
            val leftCx = margin + primarySize / 2f
            val rightCx = width - margin - primarySize / 2f

            val left = leftCx - primarySize / 2f
            val top = clusterCy - primarySize / 2f
            dpadDeadZone = cell * 0.26f
            dpadOuter.set(left, top, left + primarySize, top + primarySize)
            dpadCenter.set(leftCx - cell / 2f, clusterCy - cell / 2f, leftCx + cell / 2f, clusterCy + cell / 2f)
            dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(leftCx - cell / 2f, top, leftCx + cell / 2f, top + cell)
            dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(leftCx - cell / 2f, top + cell * 2f, leftCx + cell / 2f, top + primarySize)
            dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(left, clusterCy - cell / 2f, left + cell, clusterCy + cell / 2f)
            dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(left + cell * 2f, clusterCy - cell / 2f, left + primarySize, clusterCy + cell / 2f)

            val smallPillH = min(dp(32f), height * 0.065f).coerceAtLeast(dp(26f))
            val shoulderW = min(dp(108f), width * 0.15f)
            val shoulderY = margin + smallPillH / 2f
            pillButton("l1", "L", KeyEvent.KEYCODE_BUTTON_L1, margin + shoulderW / 2f, shoulderY, shoulderW, smallPillH)
            pillButton("r1", "R", KeyEvent.KEYCODE_BUTTON_R1, width - margin - shoulderW / 2f, shoulderY, shoulderW, smallPillH)

            val stickRadius = min(primarySize * 0.36f, dp(48f))
            val leftStickCx = leftCx + primarySize * 1.08f
            leftStickOuter.set(leftStickCx - stickRadius, clusterCy - stickRadius, leftStickCx + stickRadius, clusterCy + stickRadius)
            leftStickCenter.set(leftStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, leftStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)
            val rightStickCx = rightCx - primarySize * 1.12f
            rightStickOuter.set(rightStickCx - stickRadius, clusterCy - stickRadius, rightStickCx + stickRadius, clusterCy + stickRadius)
            rightStickCenter.set(rightStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, rightStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)

            val faceRadius = min(dp(30f), primarySize * 0.25f)
            buttons += circularButton("b", "B", KeyEvent.KEYCODE_BUTTON_B, rightCx - primarySize * 0.32f, clusterCy + primarySize * 0.18f, faceRadius)
            buttons += circularButton("a", "A", KeyEvent.KEYCODE_BUTTON_A, rightCx + primarySize * 0.26f, clusterCy - primarySize * 0.20f, faceRadius)
            buttons += circularButton("y", "Y", VIRTUAL_TURBO_B, rightCx - primarySize * 0.34f, clusterCy - primarySize * 0.36f, faceRadius * 0.88f)
            buttons += circularButton("x", "X", VIRTUAL_TURBO_A, rightCx + primarySize * 0.26f, clusterCy + primarySize * 0.36f, faceRadius * 0.88f)

            val bottomPillY = height - margin - smallPillH / 2f
            pillButton("start", "START", KeyEvent.KEYCODE_BUTTON_START, width / 2f + dp(62f), bottomPillY, dp(88f), smallPillH)
            pillButton("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, width / 2f - dp(62f), bottomPillY, dp(92f), smallPillH)
            pillButton("quick_save", "快存", VIRTUAL_QUICK_SAVE, margin + shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
            pillButton("quick_load", "快读", VIRTUAL_QUICK_LOAD, width - margin - shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
            pillButton("fast_forward", "快进", VIRTUAL_FAST_FORWARD, width - margin - shoulderW / 2f, shoulderY + (smallPillH + dp(8f)) * 2f, dp(76f), smallPillH)
            pillButton("exit", "退出", VIRTUAL_EXIT, width / 2f, shoulderY, dp(86f), smallPillH)
            customButtons.forEach { custom ->
                val w = if (custom.circle) dp(56f) else dp(82f)
                val h = if (custom.circle) dp(56f) else smallPillH
                buttons += TouchButton(custom.id, custom.label, custom.keyCodes, RectF(width / 2f - w / 2f, height / 2f - h / 2f, width / 2f + w / 2f, height / 2f + h / 2f), custom.circle)
            }
            val menuSize = dp(42f)
            val menuTop = margin + smallPillH * 2f + dp(20f)
            menuIconRect.set(margin, menuTop, margin + menuSize, menuTop + menuSize)
            applyLayoutOverrides(width, height)

            val panelW = min(dp(560f), width - margin * 2f)
            val panelH = min(dp(420f), height - margin * 1.2f)
            menuPanelRect.set((width - panelW) / 2f, (height - panelH) / 2f, (width + panelW) / 2f, (height + panelH) / 2f)

            largeTextSize = dp(18f)
            smallTextSize = dp(12f)
        }

        private fun circularButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, radius: Float): TouchButton =
            TouchButton(id, label, setOf(keyCode), RectF(cx - radius, cy - radius, cx + radius, cy + radius), circle = true)

        private fun drawDpad(canvas: Canvas, alpha: Float) {
            strokePaint.color = alphaColor(150, alpha)
            val corner = dpadOuter.width() / 10f
            dpadRects.values.forEach { rect ->
                fillPaint.color = alphaColor(40, alpha)
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
                canvas.drawRoundRect(rect, corner, corner, strokePaint)
            }
            fillPaint.color = alphaColor(28, alpha)
            canvas.drawRoundRect(dpadCenter, corner, corner, fillPaint)
            canvas.drawRoundRect(dpadCenter, corner, corner, strokePaint)
            textPaint.textSize = largeTextSize
            drawCenteredText(canvas, "▲", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_UP), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP), alpha)
            drawCenteredText(canvas, "▼", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_DOWN), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN), alpha)
            drawCenteredText(canvas, "◀", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_LEFT), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT), alpha)
            drawCenteredText(canvas, "▶", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_RIGHT), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT), alpha)
        }

        private fun drawAnalogStick(canvas: Canvas, outer: RectF, center: RectF, label: String, alpha: Float, active: Boolean = false) {
            if (outer.width() <= 0f || outer.height() <= 0f) return
            fillPaint.color = alphaColor(if (active) 90 else 34, alpha)
            strokePaint.color = alphaColor(if (active) 230 else 135, alpha)
            canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, fillPaint)
            canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, strokePaint)
            fillPaint.color = alphaColor(if (active) 120 else 48, alpha)
            canvas.drawCircle(center.centerX(), center.centerY(), center.width() / 2f, fillPaint)
            textPaint.textSize = smallTextSize
            drawCenteredText(canvas, label, outer, active, alpha)
        }

        private fun drawButton(canvas: Canvas, button: TouchButton, pressed: Boolean, alpha: Float) {
            fillPaint.color = alphaColor(if (pressed) 115 else 42, alpha)
            strokePaint.color = alphaColor(if (pressed) 230 else 150, alpha)
            if (button.circle) {
                canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, fillPaint)
                canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, strokePaint)
            } else {
                val radius = button.rect.height() / 2f
                canvas.drawRoundRect(button.rect, radius, radius, fillPaint)
                canvas.drawRoundRect(button.rect, radius, radius, strokePaint)
            }
            textPaint.textSize = if (button.label.length > 2) smallTextSize else largeTextSize
            drawCenteredText(canvas, button.label, button.rect, pressed, alpha)
        }

        private fun drawMenuIcon(canvas: Canvas, alpha: Float) {
            fillPaint.color = alphaColor(50, alpha)
            strokePaint.color = alphaColor(170, alpha)
            val radius = menuIconRect.height() / 2f
            canvas.drawRoundRect(menuIconRect, radius, radius, fillPaint)
            canvas.drawRoundRect(menuIconRect, radius, radius, strokePaint)
            textPaint.textSize = largeTextSize
            textPaint.color = alphaColor(255, alpha)
            val y = menuIconRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("☰", menuIconRect.centerX(), y, textPaint)
        }

        private fun drawEditorFloatingUi(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            ensureEditorBall()

            fillPaint.color = Color.argb(190, 20, 24, 32)
            strokePaint.color = Color.argb(220, 255, 255, 255)
            strokePaint.strokeWidth = 2f * dp
            canvas.drawCircle(editorBallRect.centerX(), editorBallRect.centerY(), editorBallRect.width() / 2f, fillPaint)
            canvas.drawCircle(editorBallRect.centerX(), editorBallRect.centerY(), editorBallRect.width() / 2f, strokePaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 14f * dp
            textPaint.color = Color.WHITE
            val ballY = editorBallRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText("编辑", editorBallRect.centerX(), ballY, textPaint)

            if (!editorPanelOpen) return

            val panelW = min(360f * dp, width.toFloat() - 28f * dp)
            val desiredPanelH = 96f * dp + VIRTUAL_EDITOR_ITEMS.size * 37f * dp + 18f * dp
            val panelH = min(desiredPanelH, height.toFloat() - 28f * dp)
            var left = editorBallRect.left - panelW - 10f * dp
            if (left < 14f * dp) left = editorBallRect.right + 10f * dp
            if (left + panelW > width - 14f * dp) left = width - 14f * dp - panelW
            var top = editorBallRect.top
            if (top + panelH > height - 14f * dp) top = height - 14f * dp - panelH
            if (top < 14f * dp) top = 14f * dp
            editorPanelRect.set(left, top, left + panelW, top + panelH)
            editorPanelCloseRect.set(editorPanelRect.right - 42f * dp, editorPanelRect.top + 8f * dp, editorPanelRect.right - 10f * dp, editorPanelRect.top + 40f * dp)

            fillPaint.color = Color.argb(235, 18, 22, 30)
            strokePaint.color = Color.argb(220, 255, 255, 255)
            canvas.drawRoundRect(editorPanelRect, 18f * dp, 18f * dp, fillPaint)
            canvas.drawRoundRect(editorPanelRect, 18f * dp, 18f * dp, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 16f * dp
            textPaint.color = Color.WHITE
            canvas.drawText("虚拟键编辑", editorPanelRect.left + 16f * dp, editorPanelRect.top + 32f * dp, textPaint)

            fillPaint.color = Color.argb(70, 255, 255, 255)
            strokePaint.color = Color.argb(165, 255, 255, 255)
            canvas.drawRoundRect(editorPanelCloseRect, 12f * dp, 12f * dp, fillPaint)
            canvas.drawRoundRect(editorPanelCloseRect, 12f * dp, 12f * dp, strokePaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 15f * dp
            textPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("×", editorPanelCloseRect.centerX(), editorPanelCloseRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 10.5f * dp
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.argb(210, 255, 255, 255)
            val selectedName = selectedEditId ?: "未选择"
            val selectedRect = editableRect(selectedEditId)
            val selectedSizeText = if (selectedRect != null) " · ${selectedRect.width().roundToInt()}×${selectedRect.height().roundToInt()}" else ""
            canvas.drawText("拖动任意虚拟键；当前：$selectedName$selectedSizeText", editorPanelRect.left + 16f * dp, editorPanelRect.top + 54f * dp, textPaint)
            canvas.drawText("可点放大/缩小，也可手柄左右调大小；点 × 收起面板。", editorPanelRect.left + 16f * dp, editorPanelRect.top + 72f * dp, textPaint)

            editorPanelRows.clear()
            val rowTop = editorPanelRect.top + 88f * dp
            val available = (editorPanelRect.bottom - rowTop - 12f * dp).coerceAtLeast(1f)
            val gap = min(6f * dp, max(3f * dp, available * 0.018f))
            val rowH = ((available - gap * (VIRTUAL_EDITOR_ITEMS.size - 1)) / VIRTUAL_EDITOR_ITEMS.size).coerceIn(30f * dp, 38f * dp)
            VIRTUAL_EDITOR_ITEMS.forEachIndexed { index, label ->
                val rect = RectF(editorPanelRect.left + 14f * dp, rowTop + index * (rowH + gap), editorPanelRect.right - 14f * dp, rowTop + index * (rowH + gap) + rowH)
                if (rect.bottom > editorPanelRect.bottom - 12f * dp) return@forEachIndexed
                editorPanelRows += MenuRow(index, rect)
                val selected = index == editorIndex
                fillPaint.color = if (selected) Color.argb(100, 255, 255, 255) else Color.argb(34, 255, 255, 255)
                strokePaint.color = if (selected) Color.argb(230, 255, 255, 255) else Color.argb(95, 255, 255, 255)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 12f * dp
                textPaint.color = Color.WHITE
                canvas.drawText(label, rect.left + 14f * dp, rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            }
        }

        private fun drawMenuPanel(canvas: Canvas) {
            fillPaint.color = Color.argb(232, 18, 22, 30)
            strokePaint.color = Color.argb(220, 255, 255, 255)
            strokePaint.strokeWidth = 1.5f * resources.displayMetrics.density
            canvas.drawRoundRect(menuPanelRect, 22f, 22f, fillPaint)
            canvas.drawRoundRect(menuPanelRect, 22f, 22f, strokePaint)

            menuRows.clear()
            val dp = resources.displayMetrics.density
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 18f * dp
            textPaint.color = Color.WHITE
            canvas.drawText(menuTitle(), menuPanelRect.left + 20f * dp, menuPanelRect.top + 36f * dp, textPaint)

            textPaint.textSize = 11.5f * dp
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.argb(210, 255, 255, 255)
            canvas.drawText(menuHint(), menuPanelRect.left + 20f * dp, menuPanelRect.top + 58f * dp, textPaint)

            when (menuPage) {
                MenuPage.MAIN -> drawList(canvas, MAIN_MENU_ITEMS, menuIndex, menuPanelRect.top + 64f * dp) { index, _ -> subtitleForMain(index) }
                MenuPage.SAVE -> drawSaveManager(canvas)
                MenuPage.LOAD -> drawSlotList(canvas, loadSlotIndex, mode = SlotListMode.LOAD)
                MenuPage.DELETE_SAVE -> drawSlotList(canvas, deleteSlotIndex, mode = SlotListMode.DELETE)
                MenuPage.VIRTUAL_KEYS -> drawVirtualSettings(canvas)
                MenuPage.VIRTUAL_ALPHA -> drawVirtualAlphaSettings(canvas)
                MenuPage.VIRTUAL_EDITOR -> drawVirtualEditor(canvas)
                MenuPage.CHEATS -> drawCheatList(canvas)
                MenuPage.CUSTOM_CHEATS -> drawCustomCheatList(canvas)
                MenuPage.CUSTOM_CHEAT_DELETE -> drawCustomCheatDeleteList(canvas)
                MenuPage.RESET_CONFIRM -> drawResetConfirm(canvas)
            }

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
        }

        private fun menuTitle(): String = when (menuPage) {
            MenuPage.MAIN -> "内置 GBA 菜单"
            MenuPage.SAVE -> "存档"
            MenuPage.LOAD -> "读档"
            MenuPage.DELETE_SAVE -> "删除存档"
            MenuPage.VIRTUAL_KEYS -> "虚拟按键设置"
            MenuPage.VIRTUAL_ALPHA -> "透明度设置"
            MenuPage.VIRTUAL_EDITOR -> "虚拟键编辑"
            MenuPage.CHEATS -> "作弊 / 金手指"
            MenuPage.CUSTOM_CHEATS -> "自定义作弊码"
            MenuPage.CUSTOM_CHEAT_DELETE -> "删除作弊码"
            MenuPage.RESET_CONFIRM -> "重置游戏"
        }

        private fun menuHint(): String = when (menuPage) {
            MenuPage.MAIN -> "上下选择，A 进入，B 返回。SELECT+X 退出。"
            MenuPage.SAVE -> "上下选中，A 存档，Y 读档，X 删除，B 返回。"
            MenuPage.LOAD -> "上下选择槽位，A 读取并关闭菜单，B 返回。"
            MenuPage.DELETE_SAVE -> "上下选择，A 删除，B 返回；可删除快捷存档。"
            MenuPage.VIRTUAL_KEYS -> "A 进入设置项，B 返回。"
            MenuPage.VIRTUAL_ALPHA -> "连接手柄时虚拟按键默认透明。左右调整透明度，B 返回。"
            MenuPage.VIRTUAL_EDITOR -> "触摸拖动按键；点放大/缩小或手柄左右调大小，保存后生效。"
            MenuPage.CHEATS -> "右上角添加；A 启用/关闭，X 删除，B 返回。native 引擎开发中。"
            MenuPage.CUSTOM_CHEATS -> "A 添加/开关，B 返回；右下角删除可触屏删除。"
            MenuPage.CUSTOM_CHEAT_DELETE -> "上下选择，A 删除，B 返回。"
            MenuPage.RESET_CONFIRM -> "重载 ROM，不读取快捷/普通存档和游戏内 SRAM。"
        }

        private fun subtitleForMain(index: Int): String = when (index) {
            0 -> "统一管理存档 / 读档 / 删除，含快捷存档"
            1 -> "透明度、位置、大小和自定义组合键"
            2 -> "自定义金手指代码管理"
            3 -> "重新开始，不读取当前存档"
            4 -> "退出到启动器"
            else -> ""
        }

        private fun drawList(canvas: Canvas, labels: List<String>, selected: Int, top: Float, subtitle: (Int, String) -> String = { _, _ -> "" }) {
            val dp = resources.displayMetrics.density
            if (labels.isEmpty()) return
            val left = menuPanelRect.left + 20f * dp
            val right = menuPanelRect.right - 20f * dp
            val bottomPadding = when (menuPage) {
                MenuPage.CHEATS -> 72f * dp
                MenuPage.SAVE -> 72f * dp
                else -> 12f * dp
            }
            val available = (menuPanelRect.bottom - bottomPadding - top).coerceAtLeast(1f)
            val minRowH = 32f * dp
            val gap = min(7f * dp, max(3f * dp, available * 0.018f))
            val maxVisible = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
            val visibleCount = min(labels.size, maxVisible)
            val startIndex = when {
                labels.size <= visibleCount -> 0
                selected <= visibleCount / 2 -> 0
                selected >= labels.size - (visibleCount - visibleCount / 2) -> labels.size - visibleCount
                else -> selected - visibleCount / 2
            }.coerceIn(0, max(0, labels.size - visibleCount))
            val rowH = ((available - gap * (visibleCount - 1)) / visibleCount).coerceIn(minRowH, 48f * dp)

            for (offset in 0 until visibleCount) {
                val index = startIndex + offset
                val label = labels[index]
                val y = top + offset * (rowH + gap)
                val rect = RectF(left, y, right, y + rowH)
                if (rect.bottom > menuPanelRect.bottom - bottomPadding / 2f) break
                menuRows += MenuRow(index, rect)
                val isSelected = index == selected
                fillPaint.color = if (isSelected) Color.argb(94, 255, 255, 255) else Color.argb(30, 255, 255, 255)
                strokePaint.color = if (isSelected) Color.argb(230, 255, 255, 255) else Color.argb(90, 255, 255, 255)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = min(14f * dp, rowH * 0.32f)
                textPaint.color = Color.WHITE
                val sub = subtitle(index, label)
                val mainY = if (sub.isBlank()) {
                    rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                } else {
                    rect.top + rowH * 0.40f
                }
                canvas.drawText(label, rect.left + 18f * dp, mainY, textPaint)
                if (sub.isNotBlank()) {
                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.textSize = min(10.5f * dp, rowH * 0.23f)
                    textPaint.color = Color.argb(185, 255, 255, 255)
                    canvas.drawText(sub, rect.left + 18f * dp, rect.top + rowH * 0.74f, textPaint)
                }
            }

            if (labels.size > visibleCount) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 10f * dp
                textPaint.color = Color.argb(175, 255, 255, 255)
                val hint = when {
                    startIndex == 0 -> "更多 ↓"
                    startIndex + visibleCount >= labels.size -> "↑ 更多"
                    else -> "↑ 更多 ↓"
                }
                canvas.drawText(hint, right - 8f * dp, menuPanelRect.bottom - 8f * dp, textPaint)
            }
        }

        private fun drawSaveManager(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            stateSaveButtonRect.setEmpty()
            stateDeleteButtonRect.setEmpty()
            stateLoadButtonRect.setEmpty()

            val entries = (1..MAX_STATE_SLOTS).map { slot ->
                val time = stateTime(gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
                "存档 $slot    $time"
            } + listOf("快捷存档    ${stateTime(gameStorage?.quickStateFile ?: File("/__md3e_missing_quick_state__"))}")

            val left = menuPanelRect.left + 20f * dp
            val right = menuPanelRect.right - 20f * dp
            val top = menuPanelRect.top + 68f * dp
            val actionH = 32f * dp
            val actionBottom = menuPanelRect.bottom - 14f * dp
            val listBottom = actionBottom - actionH - 16f * dp
            val available = (listBottom - top).coerceAtLeast(1f)
            val gap = 4f * dp
            val separatorH = 10f * dp
            val minRowH = 26f * dp
            val maxRowH = 34f * dp
            val selected = saveSlotIndex.coerceIn(0, entries.lastIndex)

            // 小屏或横屏时按当前选中项自动滚动，只绘制可见行，避免和底部 A/X/Y 按钮重叠。
            var visibleCount = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
            visibleCount = min(entries.size, visibleCount)
            var startIndex = when {
                entries.size <= visibleCount -> 0
                selected <= visibleCount / 2 -> 0
                selected >= entries.size - (visibleCount - visibleCount / 2) -> entries.size - visibleCount
                else -> selected - visibleCount / 2
            }.coerceIn(0, max(0, entries.size - visibleCount))

            var quickVisible = startIndex <= MAX_STATE_SLOTS && startIndex + visibleCount > MAX_STATE_SLOTS
            var rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
            fun saveListUsedHeight(count: Int, itemHeight: Float, showQuickSeparator: Boolean): Float {
                val separator = if (showQuickSeparator) separatorH else 0f
                return count * itemHeight + gap * (count - 1) + separator
            }

            while (visibleCount > 1 && top + saveListUsedHeight(visibleCount, rowH, quickVisible) > listBottom) {
                visibleCount -= 1
                startIndex = when {
                    entries.size <= visibleCount -> 0
                    selected <= visibleCount / 2 -> 0
                    selected >= entries.size - (visibleCount - visibleCount / 2) -> entries.size - visibleCount
                    else -> selected - visibleCount / 2
                }.coerceIn(0, max(0, entries.size - visibleCount))
                quickVisible = startIndex <= MAX_STATE_SLOTS && startIndex + visibleCount > MAX_STATE_SLOTS
                rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
            }

            var y = top
            for (offset in 0 until visibleCount) {
                val index = startIndex + offset
                if (index !in entries.indices) break
                if (index == MAX_STATE_SLOTS) {
                    val lineY = y + separatorH * 0.35f
                    strokePaint.strokeWidth = 1.1f * dp
                    strokePaint.color = Color.argb(130, 255, 255, 255)
                    canvas.drawLine(left + 8f * dp, lineY, right - 8f * dp, lineY, strokePaint)
                    y += separatorH
                }
                if (y + rowH > listBottom + 0.5f * dp) break

                val rect = RectF(left, y, right, y + rowH)
                menuRows += MenuRow(index, rect)
                val isSelected = index == selected
                fillPaint.color = if (isSelected) Color.argb(94, 255, 255, 255) else Color.argb(30, 255, 255, 255)
                strokePaint.color = if (isSelected) Color.argb(230, 255, 255, 255) else Color.argb(90, 255, 255, 255)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
                canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = min(13f * dp, rowH * 0.34f)
                textPaint.color = Color.WHITE
                canvas.drawText(entries[index], rect.left + 18f * dp, rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
                y += rowH + gap
            }

            if (entries.size > visibleCount) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 10f * dp
                textPaint.color = Color.argb(175, 255, 255, 255)
                val hint = when {
                    startIndex == 0 -> "更多 ↓"
                    startIndex + visibleCount >= entries.size -> "↑ 更多"
                    else -> "↑ 更多 ↓"
                }
                canvas.drawText(hint, right - 8f * dp, listBottom - 4f * dp, textPaint)
            }

            val selectedFile = if (selected >= MAX_STATE_SLOTS) {
                gameStorage?.quickStateFile
            } else {
                gameStorage?.slotStateFile(selected + 1)
            }
            val hasState = selectedFile?.let { it.exists() && it.length() > 0L } == true
            val actionLeft = menuPanelRect.left + 20f * dp
            val actionRight = menuPanelRect.right - 20f * dp
            val actionGap = 10f * dp
            val actionW = ((actionRight - actionLeft - actionGap * 2f) / 3f).coerceAtLeast(74f * dp)
            stateSaveButtonRect.set(actionLeft, actionBottom - actionH, actionLeft + actionW, actionBottom)
            stateDeleteButtonRect.set(stateSaveButtonRect.right + actionGap, actionBottom - actionH, stateSaveButtonRect.right + actionGap + actionW, actionBottom)
            stateLoadButtonRect.set(stateDeleteButtonRect.right + actionGap, actionBottom - actionH, actionRight, actionBottom)
            drawSmallMenuButton(canvas, stateSaveButtonRect, "A 存档", enabled = true)
            drawSmallMenuButton(canvas, stateDeleteButtonRect, "X 删除存档", enabled = hasState)
            drawSmallMenuButton(canvas, stateLoadButtonRect, "Y 读档", enabled = hasState)
        }

        private fun drawSlotList(canvas: Canvas, selected: Int, mode: SlotListMode) {
            val dp = resources.displayMetrics.density
            val labels = when (mode) {
                SlotListMode.DELETE -> listOf("快捷存档    ${stateTime(gameStorage?.quickStateFile ?: File("/__md3e_missing_quick_state__"))}") +
                    (1..MAX_STATE_SLOTS).map { slot ->
                        val time = stateTime(gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
                        "存档 $slot    $time"
                    }
                SlotListMode.SAVE,
                SlotListMode.LOAD -> (1..MAX_STATE_SLOTS).map { slot ->
                    val time = stateTime(gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
                    "存档 $slot    $time"
                }
            }
            drawList(canvas, labels, selected, menuPanelRect.top + 64f * dp) { index, _ ->
                when (mode) {
                    SlotListMode.SAVE -> "A 保存"
                    SlotListMode.LOAD -> "A 读取"
                    SlotListMode.DELETE -> if (index == 0) "A 删除快捷存档" else "A 删除存档 $index"
                }
            }
        }

        private fun drawVirtualSettings(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            drawList(canvas, VIRTUAL_KEY_MENU_ITEMS, virtualSettingsIndex, menuPanelRect.top + 72f * dp) { index, _ ->
                if (index == 0) "调整真实手柄连接时虚拟键透明度" else "拖动按键位置，左右调大小，可添加组合键"
            }
        }

        private fun drawVirtualAlphaSettings(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val left = menuPanelRect.left + 26f * dp
            val right = menuPanelRect.right - 26f * dp
            val top = menuPanelRect.top + 96f * dp
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 15f * dp
            textPaint.color = Color.WHITE
            canvas.drawText("真实手柄模式虚拟按键透明度：${(hardwareControlsAlpha * 100).roundToInt()}%", left, top, textPaint)

            val cy = top + 42f * dp
            strokePaint.strokeWidth = 5f * dp
            strokePaint.color = Color.argb(95, 255, 255, 255)
            canvas.drawLine(left, cy, right, cy, strokePaint)
            val thumbX = left + (right - left) * hardwareControlsAlpha
            strokePaint.color = Color.argb(225, 255, 255, 255)
            canvas.drawLine(left, cy, thumbX, cy, strokePaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(thumbX, cy, 10f * dp, fillPaint)
            strokePaint.strokeWidth = 2.5f * dp

            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 12f * dp
            textPaint.color = Color.argb(205, 255, 255, 255)
            canvas.drawText("← 变透明    → 变明显", left, cy + 36f * dp, textPaint)
            canvas.drawText("默认 0%，连接真实手柄后不挡屏幕；需要触屏辅助时调高。", left, cy + 58f * dp, textPaint)
        }

        private fun drawVirtualEditor(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val selectedName = selectedEditId ?: "未选择"
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 10.5f * dp
            textPaint.color = Color.argb(210, 255, 255, 255)
            canvas.drawText("当前：$selectedName；触摸拖动按键，左右键调整大小。", menuPanelRect.left + 20f * dp, menuPanelRect.top + 74f * dp, textPaint)
            drawList(canvas, VIRTUAL_EDITOR_ITEMS, editorIndex, menuPanelRect.top + 84f * dp) { index, _ ->
                when (index) {
                    0 -> "填写显示名称、样式和方法，添加到屏幕中间"
                    1 -> "放大当前选中的虚拟键"
                    2 -> "缩小当前选中的虚拟键"
                    3 -> "保存当前全部虚拟键位置和大小，并返回游戏"
                    4 -> "恢复默认按键位置"
                    else -> "放弃未保存修改并返回游戏"
                }
            }
        }

        private fun drawCheatList(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val custom = loadCustomCheats()
            val selected = if (custom.isEmpty()) 0 else cheatIndex.coerceIn(0, custom.lastIndex)

            val buttonW = 120f * dp
            val buttonH = 34f * dp
            cheatAddButtonRect.set(
                menuPanelRect.right - 20f * dp - buttonW,
                menuPanelRect.top + 15f * dp,
                menuPanelRect.right - 20f * dp,
                menuPanelRect.top + 15f * dp + buttonH
            )
            drawSmallMenuButton(canvas, cheatAddButtonRect, "自定义作弊码", enabled = true)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 10.5f * dp
            textPaint.color = Color.argb(172, 255, 255, 255)
            val noteX = menuPanelRect.left + 20f * dp
            canvas.drawText("说明：关闭作弊码需要重启当前游戏，关闭前会自动快速存档。", noteX, menuPanelRect.top + 55f * dp, textPaint)
            canvas.drawText("当前为稳定方案；无感关闭需要后续 native CheatManager。", noteX, menuPanelRect.top + 71f * dp, textPaint)

            if (custom.isEmpty()) {
                cheatEnableButtonRect.setEmpty()
                cheatResetButtonRect.setEmpty()
                cheatDeleteButtonRect.setEmpty()
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 15f * dp
                textPaint.color = Color.argb(220, 255, 255, 255)
                canvas.drawText("暂无作弊码", menuPanelRect.centerX(), menuPanelRect.centerY() + 2f * dp, textPaint)
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = 12f * dp
                textPaint.color = Color.argb(175, 255, 255, 255)
                canvas.drawText("点击右上角“自定义作弊码”添加", menuPanelRect.centerX(), menuPanelRect.centerY() + 28f * dp, textPaint)
            } else {
                val labels = custom.map { item -> "${item.name}：${if (item.enabled) "开" else "关"}" }
                drawList(canvas, labels, selected, menuPanelRect.top + 100f * dp) { index, _ ->
                    val item = custom.getOrNull(index)
                    if (item == null) "" else "${item.type} · ${cheatPreview(item.code)} 作弊码"
                }
            }

            val bottom = menuPanelRect.bottom - 16f * dp
            val actionH = 34f * dp
            val hasSelection = custom.isNotEmpty()
            val actionLeft = menuPanelRect.left + 20f * dp
            val actionRight = menuPanelRect.right - 20f * dp
            val actionGap = 12f * dp
            val actionW = ((actionRight - actionLeft - actionGap) / 2f).coerceAtLeast(96f * dp)
            cheatEnableButtonRect.set(actionLeft, bottom - actionH, actionLeft + actionW, bottom)
            cheatResetButtonRect.setEmpty()
            cheatDeleteButtonRect.set(cheatEnableButtonRect.right + actionGap, bottom - actionH, actionRight, bottom)
            val enabledLabel = custom.getOrNull(selected)?.enabled == true
            drawSmallMenuButton(canvas, cheatEnableButtonRect, if (enabledLabel) "A 关闭" else "A 启用", enabled = hasSelection)
            drawSmallMenuButton(canvas, cheatDeleteButtonRect, "X 删除", enabled = hasSelection)
        }

        private fun drawSmallMenuButton(canvas: Canvas, rect: RectF, label: String, enabled: Boolean) {
            val dp = resources.displayMetrics.density
            fillPaint.color = if (enabled) Color.argb(96, 255, 255, 255) else Color.argb(28, 255, 255, 255)
            strokePaint.color = if (enabled) Color.argb(225, 255, 255, 255) else Color.argb(80, 255, 255, 255)
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 11.5f * dp
            textPaint.color = if (enabled) Color.WHITE else Color.argb(125, 255, 255, 255)
            canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }

        private fun drawCustomCheatList(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val custom = loadCustomCheats()
            val labels = mutableListOf("添加作弊码")
            custom.forEach { item -> labels += "${item.name}：${if (item.enabled) "开" else "关"}" }
            drawList(canvas, labels, customCheatIndex.coerceIn(0, max(0, labels.size - 1)), menuPanelRect.top + 64f * dp) { index, _ ->
                when (index) {
                    0 -> "输入名称、类型、作弊码后保存"
                    else -> {
                        val item = custom.getOrNull(index - 1)
                        if (item == null) "" else "${item.type} · ${cheatPreview(item.code)} 作弊码"
                    }
                }
            }

            val buttonW = 96f * dp
            val buttonH = 34f * dp
            customDeleteButtonRect.set(
                menuPanelRect.right - 20f * dp - buttonW,
                menuPanelRect.bottom - 16f * dp - buttonH,
                menuPanelRect.right - 20f * dp,
                menuPanelRect.bottom - 16f * dp
            )
            fillPaint.color = Color.argb(92, 255, 255, 255)
            strokePaint.color = Color.argb(220, 255, 255, 255)
            canvas.drawRoundRect(customDeleteButtonRect, buttonH / 2f, buttonH / 2f, fillPaint)
            canvas.drawRoundRect(customDeleteButtonRect, buttonH / 2f, buttonH / 2f, strokePaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 12f * dp
            textPaint.color = Color.WHITE
            canvas.drawText("删除", customDeleteButtonRect.centerX(), customDeleteButtonRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }

        private fun drawCustomCheatDeleteList(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val custom = loadCustomCheats()
            val labels = if (custom.isEmpty()) listOf("没有自定义作弊码") else custom.map { "删除：${it.name}" }
            drawList(canvas, labels, deleteCustomCheatIndex.coerceIn(0, max(0, labels.size - 1)), menuPanelRect.top + 64f * dp) { index, _ ->
                val item = custom.getOrNull(index)
                if (item == null) "先添加作弊码" else "A 删除 · ${item.type} · ${cheatPreview(item.code)}"
            }
        }

        private fun cheatPreview(code: String): String {
            val normalized = normalizeCheatCode(code)
            return if (normalized.length <= 28) normalized else normalized.take(28) + "..."
        }

        fun refreshCustomCheatIndex() {
            val size = loadCustomCheats().size
            cheatIndex = cheatIndex.coerceIn(0, max(0, size - 1))
            customCheatIndex = customCheatIndex.coerceIn(0, size)
            deleteCustomCheatIndex = deleteCustomCheatIndex.coerceIn(0, max(0, size - 1))
        }

        private fun drawResetConfirm(canvas: Canvas) {
            val dp = resources.displayMetrics.density
            val labels = listOf("A：确认重载游戏", "B：取消返回")
            drawList(canvas, labels, menuIndex.coerceIn(0, 1), menuPanelRect.top + 92f * dp) { _, _ -> "" }
        }

        private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, pressed: Boolean, alpha: Float) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = if (pressed) Color.BLACK else alphaColor(255, alpha)
            val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, rect.centerX(), y, textPaint)
        }

        private fun alphaColor(alpha: Int, multiplier: Float): Int =
            Color.argb((alpha * multiplier).roundToInt().coerceIn(0, 255), 255, 255, 255)
    }

    private data class RuntimeCheat(val label: String, val code: String)

    private data class CustomCheat(val id: String, val name: String, val type: String, val code: String, val enabled: Boolean)

    private data class CheatItem(val label: String, val prefKey: String, val desc: String, val code: String)

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TITLE = "title"
        private const val EXTRA_SKIP_AUTO_STATE_RESTORE = "skip_auto_state_restore"
        private const val EXTRA_SKIP_SAVE_RAM_RESTORE = "skip_save_ram_restore"
        private const val EXTRA_CHEAT_RELOAD_STATE = "cheat_reload_state"
        private const val EXTRA_APPLY_CHEATS_AFTER_BOOT = "apply_cheats_after_boot"
        private const val EXTRA_RESTORE_CHEAT_CLEAN_STATE_ON_BOOT = "restore_cheat_clean_state_on_boot"
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
        private const val CHEAT_WALK_THROUGH_WALLS = "cheat_walk_through_walls"
        private const val CHEAT_INF_MONEY = "cheat_inf_money"
        private const val CHEAT_MASTER_BALL_PC = "cheat_master_ball_pc"
        private const val CHEAT_RARE_CANDY_PC = "cheat_rare_candy_pc"
        private const val CHEAT_INF_PP = "cheat_inf_pp"
        private const val CHEAT_EXP_BOOST = "cheat_exp_boost"
        private const val LEAF_GREEN_V10_MASTER_CODE = "0000BE99+000A+1003DAE6+0007"
        private val MAIN_MENU_ITEMS = listOf("存档", "虚拟按键设置", "作弊", "重置", "退出游戏")
        private val VIRTUAL_KEY_MENU_ITEMS = listOf("透明度设置", "虚拟键编辑")
        private val VIRTUAL_EDITOR_ITEMS = listOf("添加自定键", "放大当前键", "缩小当前键", "保存并返回游戏", "重置布局", "取消编辑 / 返回游戏")
        private val CHEAT_ITEMS = emptyList<CheatItem>()
    }
}
