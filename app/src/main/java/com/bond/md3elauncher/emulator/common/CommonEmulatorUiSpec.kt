package com.bond.md3elauncher.emulator.common

import android.content.Context
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings

/**
 * Common UI specification for all built-in emulator screens.
 *
 * GBA is the visual baseline. FC/NES and future internal emulators must reuse
 * these menu labels, hints and shortcut text instead of defining their own style.
 */
internal object CommonEmulatorUiSpec {
    val MAIN_MENU_ITEMS: List<String> = listOf(
        "存档",
        "虚拟按键设置",
        "作弊",
        "重置",
        "重启游戏",
        "退出游戏"
    )

    val VIRTUAL_KEY_MENU_ITEMS: List<String> = listOf(
        "透明度设置",
        "虚拟键编辑"
    )

    const val MAX_STATE_SLOTS: Int = 5

    fun mainMenuHint(context: Context): String {
        val exit = exitShortcutLabel(context)
        return "上下选择，A 进入，B 返回。$exit 退出。"
    }

    fun saveMenuHint(): String = "上下选中，A 存档，Y 读档，X 删除，B 返回。"

    fun virtualKeysHint(): String = "A 进入设置项，B 返回。"

    fun resetHint(): String = "冷重载当前 ROM，从头开始游戏，不清除存档。"

    fun restartHint(): String = "不退出游戏界面，直接让当前 core 从头开始。"

    fun exitShortcutLabel(context: Context): String {
        return runCatching {
            val settings = ControllerShortcutSettings.load(context)
            val keys = settings.keysFor(ControllerShortcutAction.EXIT_GAME)
            ControllerShortcutSettings.comboLabel(keys)
        }.getOrDefault("默认 SELECT + X")
    }
}
