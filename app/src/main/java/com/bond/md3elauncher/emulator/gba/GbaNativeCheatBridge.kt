package com.bond.md3elauncher.emulator.gba
/**
 * Phase 1 scaffold for the planned native mGBA backend.
 *
 * Current GBA gameplay still uses LibretroDroid. This bridge intentionally does not load a native
 * library yet because the project currently only contains prebuilt libretro cores, not the mGBA
 * standalone source/JNI layer required for My Boy!-style instant cheat hooks.
 */
internal object GbaNativeCheatBridge {
    const val BACKEND_LIBRETRO = 0
    const val BACKEND_NATIVE_MGBA = 1

    fun activeBackend(): Int = BACKEND_LIBRETRO

    fun isNativeBackendAvailable(): Boolean = false

    fun explainStatus(): String =
        "当前仍是 LibretroDroid 后端；My Boy! 式即时开关需要接入 native mGBA CheatManager。"
}
