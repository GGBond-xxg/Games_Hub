package com.bond.md3elauncher.emulator

import android.content.Context
import android.view.KeyEvent
import java.util.Locale

/**
 * Hardware controller shortcuts shared by internal emulators.
 *
 * The bindings are intentionally stored in a standalone SharedPreferences file so that
 * emulator activities running in their own processes can read the latest settings too.
 */
enum class ControllerShortcutAction(
    val title: String,
    val subtitle: String
) {
    QUICK_SAVE("快速保存", "保存当前游戏状态"),
    QUICK_LOAD("快速读取", "读取快捷存档"),
    FAST_FORWARD("快进切换", "1x / 2x / 4x 循环"),
    OPEN_MENU("打开菜单", "打开内置模拟器菜单"),
    EXIT_GAME("退出游戏", "返回 MD3ELauncher"),
    TURBO_A("连发 A", "按住后连续触发 A"),
    TURBO_B("连发 B", "按住后连续触发 B")
}

data class ControllerShortcutSettings(
    val bindings: Map<ControllerShortcutAction, Set<Int>>
) {
    fun keysFor(action: ControllerShortcutAction): Set<Int> =
        bindings[action].orEmpty().ifEmpty { DEFAULT_BINDINGS.getValue(action) }

    fun withBinding(action: ControllerShortcutAction, keys: Set<Int>): ControllerShortcutSettings =
        copy(bindings = bindings.toMutableMap().apply { put(action, normalizeKeys(keys)) })

    companion object {
        private const val PREF_NAME = "controller_shortcut_settings"
        private const val KEY_PREFIX = "shortcut_"
        private const val MAX_KEYS_PER_SHORTCUT = 3

        val EDITABLE_ACTIONS: List<ControllerShortcutAction> = listOf(
            ControllerShortcutAction.QUICK_SAVE,
            ControllerShortcutAction.QUICK_LOAD,
            ControllerShortcutAction.FAST_FORWARD,
            ControllerShortcutAction.OPEN_MENU,
            ControllerShortcutAction.EXIT_GAME,
            ControllerShortcutAction.TURBO_A,
            ControllerShortcutAction.TURBO_B
        )

        val DEFAULT_BINDINGS: Map<ControllerShortcutAction, Set<Int>> = mapOf(
            ControllerShortcutAction.QUICK_SAVE to setOf(KeyEvent.KEYCODE_BUTTON_L1),
            ControllerShortcutAction.QUICK_LOAD to setOf(KeyEvent.KEYCODE_BUTTON_R1),
            ControllerShortcutAction.FAST_FORWARD to setOf(KeyEvent.KEYCODE_BUTTON_THUMBR),
            ControllerShortcutAction.OPEN_MENU to setOf(KeyEvent.KEYCODE_BUTTON_L2),
            ControllerShortcutAction.EXIT_GAME to setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_X),
            ControllerShortcutAction.TURBO_A to setOf(KeyEvent.KEYCODE_BUTTON_X),
            ControllerShortcutAction.TURBO_B to setOf(KeyEvent.KEYCODE_BUTTON_Y)
        )

        fun load(context: Context): ControllerShortcutSettings {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val loaded = EDITABLE_ACTIONS.associateWith { action ->
                val raw = prefs.getString(KEY_PREFIX + action.name, null)
                parseKeys(raw).ifEmpty { DEFAULT_BINDINGS.getValue(action) }
            }
            return ControllerShortcutSettings(loaded)
        }

        fun save(context: Context, settings: ControllerShortcutSettings) {
            val editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            EDITABLE_ACTIONS.forEach { action ->
                editor.putString(KEY_PREFIX + action.name, encodeKeys(settings.keysFor(action)))
            }
            editor.apply()
        }

        fun saveBinding(context: Context, action: ControllerShortcutAction, keys: Set<Int>): ControllerShortcutSettings {
            val next = load(context).withBinding(action, keys)
            save(context, next)
            return next
        }

        fun resetToDefault(context: Context): ControllerShortcutSettings {
            val next = ControllerShortcutSettings(DEFAULT_BINDINGS)
            save(context, next)
            return next
        }

        fun normalizeKeys(keys: Set<Int>): Set<Int> =
            keys.asSequence()
                .filter { isCaptureCandidate(it) }
                .distinct()
                .take(MAX_KEYS_PER_SHORTCUT)
                .toSet()

        fun isCaptureCandidate(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK -> false
            else -> true
        }

        fun conflictAction(
            settings: ControllerShortcutSettings,
            action: ControllerShortcutAction,
            keys: Set<Int>
        ): ControllerShortcutAction? {
            val normalized = normalizeKeys(keys)
            if (normalized.isEmpty()) return null
            return EDITABLE_ACTIONS.firstOrNull { other ->
                other != action && settings.keysFor(other) == normalized
            }
        }

        fun findTriggeredAction(
            settings: ControllerShortcutSettings,
            pressedKeys: Set<Int>,
            currentKey: Int,
            alreadyFired: Set<ControllerShortcutAction>
        ): ControllerShortcutAction? {
            return EDITABLE_ACTIONS
                .sortedWith(
                    compareByDescending<ControllerShortcutAction> { settings.keysFor(it).size }
                        .thenBy { EDITABLE_ACTIONS.indexOf(it) }
                )
                .firstOrNull { action ->
                    val combo = settings.keysFor(action)
                    combo.isNotEmpty() &&
                        currentKey in combo &&
                        action !in alreadyFired &&
                        combo.all { it in pressedKeys }
                }
        }

        fun releasedActions(
            settings: ControllerShortcutSettings,
            pressedKeys: Set<Int>,
            alreadyFired: Set<ControllerShortcutAction>
        ): List<ControllerShortcutAction> = alreadyFired.filter { action ->
            settings.keysFor(action).any { it !in pressedKeys }
        }

        fun comboLabel(keys: Set<Int>): String {
            val normalized = normalizeKeys(keys)
            if (normalized.isEmpty()) return "未设置"
            return normalized.joinToString(" + ") { keyName(it) }
        }

        fun keyName(keyCode: Int): String = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            KeyEvent.KEYCODE_BUTTON_START -> "START"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
            KeyEvent.KEYCODE_BUTTON_MODE -> "MODE"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
            KeyEvent.KEYCODE_DPAD_LEFT -> "←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
            KeyEvent.KEYCODE_ENTER -> "ENTER"
            KeyEvent.KEYCODE_SPACE -> "SPACE"
            else -> KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_")
                .lowercase(Locale.ROOT)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        private fun parseKeys(raw: String?): Set<Int> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .let { normalizeKeys(it.toSet()) }
        }

        private fun encodeKeys(keys: Set<Int>): String = normalizeKeys(keys).joinToString(",")
    }
}
