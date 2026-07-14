package com.bond.md3elauncher.emulator.gba

import android.graphics.RectF
import com.bond.md3elauncher.emulator.common.CommonEmulatorUiSpec
import java.io.File

internal enum class MenuPage { MAIN, SAVE, LOAD, DELETE_SAVE, VIRTUAL_KEYS, VIRTUAL_ALPHA, VIRTUAL_TOUCH_ALPHA, VIRTUAL_EDITOR, CHEATS, CUSTOM_CHEATS, CUSTOM_CHEAT_DELETE, RESET_CONFIRM }

internal enum class SlotListMode { SAVE, LOAD, DELETE }

internal data class GameStorage(
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

internal data class TouchButton(
    val id: String,
    val label: String,
    val keyCodes: Set<Int>,
    val rect: RectF,
    val circle: Boolean = false
) {
    val keyCode: Int get() = keyCodes.firstOrNull() ?: -999999
}

internal data class MenuRow(
    val index: Int,
    val rect: RectF
)

internal data class CustomTouchButton(
    val id: String,
    val label: String,
    val circle: Boolean,
    val keyCodes: Set<Int>
)

internal data class RuntimeCheat(val label: String, val code: String)

internal data class RuntimeCheatPayload(val label: String, val code: String, val lineCount: Int)

internal data class CustomCheat(val id: String, val name: String, val type: String, val code: String, val enabled: Boolean)

internal data class CheatItem(val label: String, val prefKey: String, val desc: String, val code: String)

internal const val VIRTUAL_FAST_FORWARD = -100001
internal const val VIRTUAL_MENU = -100002
internal const val VIRTUAL_TURBO_A = -100003
internal const val VIRTUAL_TURBO_B = -100004
internal const val VIRTUAL_QUICK_SAVE = -100005
internal const val VIRTUAL_QUICK_LOAD = -100006
internal const val VIRTUAL_EXIT = -100007
internal const val MAX_STATE_SLOTS = 5

internal val MAIN_MENU_ITEMS = CommonEmulatorUiSpec.MAIN_MENU_ITEMS
internal val VIRTUAL_KEY_MENU_ITEMS = CommonEmulatorUiSpec.VIRTUAL_KEY_MENU_ITEMS
internal val VIRTUAL_EDITOR_ITEMS = listOf("Add Custom Button", "Increase Button Size", "Decrease Button Size", "Save and Return", "Reset Layout", "Cancel / Return to Game")

internal const val PREF_CUSTOM_TOUCH_BUTTONS = "custom_touch_buttons"
internal const val PREF_TOUCH_LAYOUT_RECTS = "touch_layout_rects"
