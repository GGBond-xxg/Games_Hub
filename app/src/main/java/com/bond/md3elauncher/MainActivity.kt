package com.bond.md3elauncher

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import com.bond.md3elauncher.i18n.I18n
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.data.ItemOverride
import com.bond.md3elauncher.data.LandscapeMode
import com.bond.md3elauncher.data.LauncherStore
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.data.SafeMarginSettings
import com.bond.md3elauncher.data.ThemeMode
import com.bond.md3elauncher.io.RomScanner
import com.bond.md3elauncher.system.AndroidAppRepository
import com.bond.md3elauncher.system.ExternalLauncher
import com.bond.md3elauncher.emulator.InternalEmulators
import com.bond.md3elauncher.emulator.gba.InternalGbaActivity
import com.bond.md3elauncher.emulator.fc.InternalFcActivity
import com.bond.md3elauncher.ui.LauncherApp
import com.bond.md3elauncher.ui.GameHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var store: LauncherStore
    private lateinit var scanner: RomScanner
    private lateinit var androidApps: AndroidAppRepository
    private lateinit var externalLauncher: ExternalLauncher

    private var platforms by mutableStateOf<List<PlatformConfig>>(emptyList())
    private var games by mutableStateOf<List<GameItem>>(emptyList())
    private var favorites by mutableStateOf<Set<String>>(emptySet())
    private var recent by mutableStateOf<List<String>>(emptyList())
    private var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var androidGames by mutableStateOf<Set<String>>(emptySet())
    private var itemOverrides by mutableStateOf<Map<String, ItemOverride>>(emptyMap())
    private var landscapeMode by mutableStateOf(LandscapeMode.AUTO)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var useDynamicColor by mutableStateOf(true)
    private var safeMargins by mutableStateOf(SafeMarginSettings())
    private var scraperSettings by mutableStateOf(ScraperSettings())
    private var tabOrder by mutableStateOf<List<String>>(emptyList())
    private var itemOrders by mutableStateOf<Map<String, List<String>>>(emptyMap())
    private var languageMode by mutableStateOf(I18n.LANG_SYSTEM)
    private var isDefaultHome by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)
    private var showHomePrompt by mutableStateOf(false)
    private var pendingFolderPlatformId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastGbaRelayRequestId: Long = -1L
    private var lastFcRelayRequestId: Long = -1L

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val platformId = pendingFolderPlatformId ?: return@registerForActivityResult
        pendingFolderPlatformId = null
        if (uri == null) return@registerForActivityResult

        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        updatePlatform(platformId) { it.copy(folderUri = uri.toString()) }
        platforms.firstOrNull { it.id == platformId }?.let { scanPlatform(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        store = LauncherStore(this)
        val savedThemeMode = store.loadThemeMode()
        applyApplicationNightMode(savedThemeMode)

        super.onCreate(savedInstanceState)
        configureStableWindow()
        enterImmersiveMode()
        scanner = RomScanner(this)
        androidApps = AndroidAppRepository(this)
        externalLauncher = ExternalLauncher(this)

        landscapeMode = store.loadLandscapeMode()
        themeMode = savedThemeMode
        useDynamicColor = store.loadUseDynamicColor()
        safeMargins = store.loadSafeMargins()
        scraperSettings = store.loadScraperSettings()
        tabOrder = store.loadTabOrder()
        itemOrders = store.loadItemOrders()
        languageMode = store.loadLanguageMode()
        I18n.setLanguageOverride(this, languageMode)
        applyLandscapeMode(landscapeMode)
        platforms = store.loadPlatforms()
        games = store.loadGames()
        favorites = store.loadFavorites()
        recent = store.loadRecent()
        itemOverrides = store.loadItemOverrides()
        installedApps = androidApps.loadLaunchableApps()
        androidGames = store.loadAndroidGames()
        showHomePrompt = store.shouldShowHomePrompt()
        isDefaultHome = isDefaultHomeLauncher()

        setContent {
            GameHubTheme(themeMode = themeMode, useDynamicColor = useDynamicColor) {
                LauncherApp(
                    platforms = platforms,
                    games = games,
                    favorites = favorites,
                    recentIds = recent,
                    installedApps = installedApps,
                    itemOverrides = itemOverrides,
                    androidGames = androidGames,
                    landscapeMode = landscapeMode,
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    safeMargins = safeMargins,
                    scraperSettings = scraperSettings,
                    tabOrder = tabOrder,
                    itemOrders = itemOrders,
                    languageMode = languageMode,
                    isScanning = isScanning,
                    showHomePrompt = showHomePrompt,
                    onPickFolder = { platform ->
                        pendingFolderPlatformId = platform.id
                        folderPicker.launch(null)
                    },
                    onSelectEmulator = { platform, app ->
                        updatePlatform(platform.id) {
                            it.copy(emulatorPackage = app.packageName, emulatorName = app.label)
                        }
                    },
                    onClearEmulator = { platform ->
                        updatePlatform(platform.id) {
                            it.copy(emulatorPackage = null, emulatorName = null)
                        }
                    },
                    onUseInternalEmulator = { platform ->
                        when (platform.kind) {
                            PlatformKind.GBA -> updatePlatform(platform.id) {
                                it.copy(
                                    emulatorPackage = InternalEmulators.GBA_PACKAGE,
                                    emulatorName = InternalEmulators.GBA_NAME
                                )
                            }
                            PlatformKind.GB -> updatePlatform(platform.id) {
                                it.copy(
                                    emulatorPackage = InternalEmulators.GB_PACKAGE,
                                    emulatorName = InternalEmulators.GB_NAME
                                )
                            }
                            PlatformKind.NES -> updatePlatform(platform.id) {
                                it.copy(
                                    emulatorPackage = InternalEmulators.FC_PACKAGE,
                                    emulatorName = InternalEmulators.FC_NAME
                                )
                            }
                            else -> Unit
                        }
                    },
                    onScanPlatform = { platform -> scanPlatform(platform) },
                    onRescanAll = { scanAllPlatforms() },
                    onLaunchGame = { game ->
                        platforms.firstOrNull { it.id == game.platformId }?.let { platform ->
                            when {
                                InternalEmulators.usesInternalGbaCore(platform) -> launchInternalGba(game, platform.kind)
                                InternalEmulators.usesInternalFc(platform) -> launchInternalFc(game)
                                else -> externalLauncher.launchGame(game, platform)
                            }
                            store.pushRecent(game.id)
                            recent = store.loadRecent()
                        }
                    },
                    onToggleFavorite = { game ->
                        store.setFavorite(game.id, game.id !in favorites)
                        favorites = store.loadFavorites()
                    },
                    onToggleAndroidFavorite = { app ->
                        val key = "app:${app.packageName}"
                        store.setFavorite(key, key !in favorites)
                        favorites = store.loadFavorites()
                    },
                    onToggleAndroidGame = { app ->
                        val key = "app:${app.packageName}"
                        store.setAndroidGame(key, key !in androidGames)
                        androidGames = store.loadAndroidGames()
                    },
                    onSaveItemOverride = { key, title, imageUriString ->
                        saveItemOverride(key = key, title = title, imageUriString = imageUriString)
                    },
                    onLaunchAndroidApp = { app -> externalLauncher.launchAndroidApp(app.packageName) },
                    onOpenHomeSettings = { externalLauncher.openHomeSettings() },
                    onSetLandscapeMode = { mode ->
                        landscapeMode = mode
                        store.saveLandscapeMode(mode)
                        applyLandscapeMode(mode)
                    },
                    onSetThemeMode = { mode ->
                        themeMode = mode
                        store.saveThemeMode(mode)
                        applyApplicationNightMode(mode)
                    },
                    onSetDynamicColor = { enabled ->
                        useDynamicColor = enabled
                        store.saveUseDynamicColor(enabled)
                    },
                    onSetSafeMargins = { settings ->
                        val clean = settings.clamped()
                        safeMargins = clean
                        store.saveSafeMargins(clean)
                    },
                    onSaveScraperSettings = { settings ->
                        scraperSettings = settings
                        store.saveScraperSettings(settings)
                    },
                    onSaveTabOrder = { order ->
                        tabOrder = order
                        store.saveTabOrder(order)
                    },
                    onSaveItemOrder = { scope, order ->
                        store.saveItemOrder(scope, order)
                        itemOrders = store.loadItemOrders()
                    },
                    onSetLanguageMode = { mode ->
                        languageMode = mode
                        store.saveLanguageMode(mode)
                        I18n.setLanguageOverride(this, mode)
                    },
                    isDefaultHome = isDefaultHome,
                    onExitApp = { finish() },
                    onDismissHomePrompt = {
                        store.markHomePromptDone()
                        showHomePrompt = false
                    }
                )
            }
        }

        handleGbaColdRestartRelay(intent)
        handleFcColdRestartRelay(intent)
    }

    private fun applyApplicationNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nightMode = when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            }
            runCatching {
                (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.setApplicationNightMode(nightMode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGbaColdRestartRelay(intent)
        handleFcColdRestartRelay(intent)
    }

    private fun handleGbaColdRestartRelay(source: Intent?) {
        if (source?.action != ACTION_GBA_COLD_RESTART_RELAY) return
        val requestId = source.getLongExtra(EXTRA_GBA_RELAY_REQUEST_ID, System.currentTimeMillis())
        if (requestId == lastGbaRelayRequestId) return
        lastGbaRelayRequestId = requestId

        val launch = Intent(this, InternalGbaActivity::class.java).apply {
            putExtras(source)
            action = null
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        // v48：重启请求由主进程接管。等待旧 :internal_gba 进程完成自杀后，再打开新的 GBA Activity，
        // 避免 v47 中“新 Activity 也在同一进程里被一起 kill，关闭后游戏直接退出”的问题。
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                startActivity(launch)
                overridePendingTransition(0, 0)
            }
        }, 900L)
    }


    private fun handleFcColdRestartRelay(source: Intent?) {
        if (source?.action != ACTION_FC_COLD_RESTART_RELAY) return
        val requestId = source.getLongExtra(EXTRA_FC_RELAY_REQUEST_ID, System.currentTimeMillis())
        if (requestId == lastFcRelayRequestId) return
        lastFcRelayRequestId = requestId

        val launch = Intent(this, InternalFcActivity::class.java).apply {
            putExtras(source)
            action = null
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                startActivity(launch)
                overridePendingTransition(0, 0)
            }
        }, 900L)
    }

    private fun launchInternalGba(game: GameItem, platformKind: PlatformKind = PlatformKind.GBA) {
        val platformLabel = if (platformKind == PlatformKind.GB) "GB/GBC" else "GBA"
        val intent = Intent(this, InternalGbaActivity::class.java).apply {
            putExtra(InternalGbaActivity.EXTRA_ROM_URI, game.uri)
            putExtra(InternalGbaActivity.EXTRA_FILE_NAME, game.fileName)
            putExtra(InternalGbaActivity.EXTRA_TITLE, game.title)
            putExtra(InternalGbaActivity.EXTRA_PLATFORM_LABEL, platformLabel)
        }
        startActivity(intent)
    }

    private fun launchInternalFc(game: GameItem) {
        val intent = Intent(this, InternalFcActivity::class.java).apply {
            putExtra(InternalFcActivity.EXTRA_ROM_URI, game.uri)
            putExtra(InternalFcActivity.EXTRA_FILE_NAME, game.fileName)
            putExtra(InternalFcActivity.EXTRA_TITLE, game.title)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = isDefaultHomeLauncher()
        configureStableWindow()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun configureStableWindow() {
        runCatching {
            // Do not resize the launcher when the keyboard or transient system bars appear.
            // This keeps the bottom key hints and list layout from jumping while search/edit fields are focused.
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
        }
    }

    private fun enterImmersiveMode() {
        runCatching {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    private fun isDefaultHomeLauncher(): Boolean {
        return runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        }.getOrDefault(false)
    }

    private fun applyLandscapeMode(mode: LandscapeMode) {
        requestedOrientation = when (mode) {
            LandscapeMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            LandscapeMode.LEFT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            LandscapeMode.RIGHT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    private fun updatePlatform(platformId: String, mapper: (PlatformConfig) -> PlatformConfig) {
        platforms = platforms.map { if (it.id == platformId) mapper(it) else it }
        store.savePlatforms(platforms)
    }

    private fun scanPlatform(platform: PlatformConfig) {
        if (platform.folderUri.isNullOrBlank()) return
        lifecycleScope.launch {
            isScanning = true
            runCatching {
                val scanned = withContext(Dispatchers.IO) { scanner.scan(platform) }
                val nextGames = games.filterNot { it.platformId == platform.id } + scanned
                games = nextGames.sortedWith(compareBy<GameItem> { it.platformTitle }.thenBy { it.title })
                store.saveGames(games)
                updatePlatform(platform.id) {
                    it.copy(gameCount = scanned.size, lastScanAt = System.currentTimeMillis())
                }
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, I18n.t(this@MainActivity, "toast.scan_failed", "扫描失败：{error}", "error" to (error.message ?: I18n.t(this@MainActivity, "common.unknown_error", "未知错误"))), Toast.LENGTH_LONG).show()
            }
            isScanning = false
        }
    }

    private fun scanAllPlatforms() {
        lifecycleScope.launch {
            isScanning = true
            runCatching {
                val scanTargets = platforms.filter { !it.folderUri.isNullOrBlank() }
                val scannedPairs = withContext(Dispatchers.IO) {
                    scanTargets.associate { it.id to scanner.scan(it) }
                }
                val untouched = games.filterNot { it.platformId in scannedPairs.keys }
                games = (untouched + scannedPairs.values.flatten())
                    .sortedWith(compareBy<GameItem> { it.platformTitle }.thenBy { it.title })
                store.saveGames(games)
                val now = System.currentTimeMillis()
                platforms = platforms.map { p ->
                    scannedPairs[p.id]?.let { p.copy(gameCount = it.size, lastScanAt = now) } ?: p
                }
                store.savePlatforms(platforms)
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, I18n.t(this@MainActivity, "toast.rescan_failed", "重新扫描失败：{error}", "error" to (error.message ?: I18n.t(this@MainActivity, "common.unknown_error", "未知错误"))), Toast.LENGTH_LONG).show()
            }
            isScanning = false
        }
    }

    private fun saveItemOverride(key: String, title: String, imageUriString: String?) {
        lifecycleScope.launch {
            val old = itemOverrides[key]
            val nextImagePath = withContext(Dispatchers.IO) {
                when {
                    imageUriString == "__REMOVE__" -> null
                    !imageUriString.isNullOrBlank() -> saveOverrideImage(key, Uri.parse(imageUriString))
                    else -> old?.imagePath
                }
            }
            store.saveItemOverride(
                ItemOverride(
                    key = key,
                    title = title.trim().takeIf { it.isNotBlank() },
                    imagePath = nextImagePath
                )
            )
            itemOverrides = store.loadItemOverrides()
        }
    }

    private fun saveOverrideImage(key: String, uri: Uri): String? {
        return runCatching {
            val bitmap = if (uri.scheme == "file") {
                BitmapFactory.decodeFile(uri.path)
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } ?: return@runCatching null

            val maxSide = 384f
            val scale = min(maxSide / bitmap.width.coerceAtLeast(1), maxSide / bitmap.height.coerceAtLeast(1)).coerceAtMost(1f)
            val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }

            val dir = File(filesDir, "custom_icons")
            if (!dir.exists()) dir.mkdirs()
            val safeName = key.map { ch ->
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_'
            }.joinToString("").take(96)
            val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            outFile.absolutePath
        }.getOrNull()
    }
    companion object {
        const val ACTION_GBA_COLD_RESTART_RELAY = "com.bond.md3elauncher.action.GBA_COLD_RESTART_RELAY"
        const val EXTRA_GBA_RELAY_REQUEST_ID = "gba_relay_request_id"
        const val ACTION_FC_COLD_RESTART_RELAY = "com.bond.md3elauncher.action.FC_COLD_RESTART_RELAY"
        const val EXTRA_FC_RELAY_REQUEST_ID = "fc_relay_request_id"
    }

}
