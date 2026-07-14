package com.bond.md3elauncher.data

enum class PlatformKind(
    val title: String,
    val subtitle: String,
    val extensions: Set<String>
) {
    PSP(
        title = "PSP",
        subtitle = "PPSSPP / RetroArch / PSP 模拟器",
        extensions = setOf("iso", "cso", "pbp", "chd")
    ),
    SWITCH(
        title = "Switch",
        subtitle = "Switch 模拟器",
        extensions = setOf("nsp", "xci", "nsz", "nro")
    ),
    GBA(
        title = "GBA",
        subtitle = "内置 GBA / My Boy! / Pizza Boy / RetroArch",
        extensions = setOf("gba", "zip", "7z")
    ),
    GB(
        title = "GB/GBC",
        subtitle = "内置 GB/GBC / My OldBoy! / RetroArch",
        extensions = setOf("gb", "gbc", "sgb", "zip", "7z")
    ),
    SFC(
        title = "SFC/SNES",
        subtitle = "内置 SFC/SNES / Snes9x EX+ / RetroArch",
        extensions = setOf("sfc", "smc", "swc", "fig", "bs", "st", "zip", "7z")
    ),
    NES(
        title = "FC/NES",
        subtitle = "内置 FC/NES / Nes.emu / Nostalgia.NES / RetroArch",
        extensions = setOf("nes", "fds", "unf", "unif", "zip", "7z")
    );
}

data class PlatformConfig(
    val id: String,
    val kind: PlatformKind,
    val folderUri: String? = null,
    val emulatorPackage: String? = null,
    val emulatorName: String? = null,
    val gameCount: Int = 0,
    val lastScanAt: Long = 0L
)

data class GameItem(
    val id: String,
    val platformId: String,
    val platformTitle: String,
    val title: String,
    val fileName: String,
    val extension: String,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis(),
    val serial: String? = null,
    val coverPath: String? = null,
    val backgroundPath: String? = null
)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val isGame: Boolean = false,
    val isLikelyEmulator: Boolean = false
)

enum class LandscapeMode(val title: String, val subtitle: String) {
    AUTO("自动", "跟随设备方向，支持左右横屏反转"),
    LEFT("横屏 1", "锁定一个横屏方向"),
    RIGHT("横屏 2", "锁定反向横屏方向")
}

enum class Destination(val title: String) {
    FAVORITES("收藏"),
    LIBRARY("游戏库"),
    APPS("安卓应用"),
    SETTINGS("设置")
}


enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("日间模式"),
    DARK("夜间模式")
}


data class SafeMarginSettings(
    val leftDp: Int = 24,
    val rightDp: Int = 24
) {
    fun clamped(): SafeMarginSettings = copy(
        leftDp = leftDp.coerceIn(MIN_DP, MAX_DP),
        rightDp = rightDp.coerceIn(MIN_DP, MAX_DP)
    )

    companion object {
        const val MIN_DP = 0
        const val MAX_DP = 96
        const val DEFAULT_DP = 24
    }
}


data class ItemOverride(
    val key: String,
    val title: String? = null,
    val imagePath: String? = null
)

data class ScraperSettings(
    val useLibretro: Boolean = true,
    val theGamesDbApiKey: String = "",
    val steamGridDbApiKey: String = "",
    val screenScraperUser: String = "",
    val screenScraperPassword: String = ""
)

data class CoverCandidate(
    val title: String,
    val imageUrl: String,
    val source: String
)

