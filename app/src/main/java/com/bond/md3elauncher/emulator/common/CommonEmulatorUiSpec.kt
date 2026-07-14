package com.bond.md3elauncher.emulator.common

import android.content.Context
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings

/**
 * Common UI specification for all built-in emulator screens.
 *
 * GBA is the visual baseline. FC/NES and future internal emulators must reuse
 * these menu labels, hints and shortcut text instead of defining their own style.
 */
internal object CommonEmulatorUiSpec {
    // Legacy fallback lists kept for old code paths. New code should call
    // mainMenuItems(context) / virtualKeyMenuItems(context) so the text comes from JSON.
    val MAIN_MENU_ITEMS: List<String> = listOf("Save States", "Virtual Buttons", "Cheats", "Reset", "Restart Game", "Exit Game")

    val VIRTUAL_KEY_MENU_ITEMS: List<String> = listOf("Real Controller Opacity", "Virtual Controller Opacity", "Virtual Button Editor")

    fun mainMenuItems(context: Context): List<String> = listOf(
        I18n.t(context, "emulator.menu.save", "Save States"),
        I18n.t(context, "emulator.menu.virtual_keys", "Virtual Buttons"),
        I18n.t(context, "emulator.menu.cheat", "Cheats"),
        I18n.t(context, "emulator.menu.reset", "Reset"),
        I18n.t(context, "emulator.menu.restart", "Restart Game"),
        I18n.t(context, "emulator.menu.exit", "Exit Game")
    )

    fun virtualKeyMenuItems(context: Context): List<String> = listOf(
        I18n.t(context, "emulator.menu.hardware_opacity", "Real Controller Opacity"),
        I18n.t(context, "emulator.menu.touch_opacity", "Virtual Controller Opacity"),
        I18n.t(context, "emulator.menu.virtual_editor", "Virtual Button Editor")
    )

    const val MAX_STATE_SLOTS: Int = 5

    fun mainMenuHint(context: Context): String {
        val exit = exitShortcutLabel(context)
        return I18n.t(context, "emulator.hint.main", "Up/Down to choose, A enter, B back. {exit} exits.", "exit" to exit)
    }

    fun saveMenuHint(context: Context): String = I18n.t(context, "emulator.hint.save", "Up/Down select, A save, Y load, X delete, B back.")

    fun virtualKeysHint(context: Context): String = I18n.t(context, "emulator.hint.virtual_keys", "A enters item, B returns.")

    fun resetHint(context: Context): String = I18n.t(context, "emulator.hint.reset", "Cold reload current ROM from the beginning without deleting saves.")

    fun restartHint(context: Context): String = I18n.t(context, "emulator.hint.restart", "Restart the current core from the beginning without leaving the game screen.")

    fun exitShortcutLabel(context: Context): String {
        return runCatching {
            val settings = ControllerShortcutSettings.load(context)
            val keys = settings.keysFor(ControllerShortcutAction.EXIT_GAME)
            ControllerShortcutSettings.comboLabel(keys)
        }.getOrDefault("Default SELECT + X")
    }
}
