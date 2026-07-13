package androidx.compose.ui.input.key

import android.view.KeyEvent as AndroidKeyEvent

/**
 * Compatibility bridge for Compose versions where KeyEvent.nativeKeyEvent is not exported.
 *
 * The launcher only needs action + keyCode while editing controller shortcuts in Compose.
 * Internal emulator activities still receive the real Android KeyEvent directly.
 */
val KeyEvent.nativeKeyEvent: AndroidKeyEvent
    get() {
        val action = when (type) {
            KeyEventType.KeyUp -> AndroidKeyEvent.ACTION_UP
            else -> AndroidKeyEvent.ACTION_DOWN
        }
        return AndroidKeyEvent(action, key.toAndroidKeyCodeCompat())
    }

private fun Key.toAndroidKeyCodeCompat(): Int {
    return when (this) {
        Key.ButtonA -> AndroidKeyEvent.KEYCODE_BUTTON_A
        Key.ButtonB -> AndroidKeyEvent.KEYCODE_BUTTON_B
        Key.ButtonX -> AndroidKeyEvent.KEYCODE_BUTTON_X
        Key.ButtonY -> AndroidKeyEvent.KEYCODE_BUTTON_Y
        Key.ButtonL1 -> AndroidKeyEvent.KEYCODE_BUTTON_L1
        Key.ButtonR1 -> AndroidKeyEvent.KEYCODE_BUTTON_R1
        Key.DirectionUp -> AndroidKeyEvent.KEYCODE_DPAD_UP
        Key.DirectionDown -> AndroidKeyEvent.KEYCODE_DPAD_DOWN
        Key.DirectionLeft -> AndroidKeyEvent.KEYCODE_DPAD_LEFT
        Key.DirectionRight -> AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        Key.DirectionCenter -> AndroidKeyEvent.KEYCODE_DPAD_CENTER
        Key.Enter -> AndroidKeyEvent.KEYCODE_ENTER
        Key.NumPadEnter -> AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        Key.A -> AndroidKeyEvent.KEYCODE_A
        Key.B -> AndroidKeyEvent.KEYCODE_B
        Key.X -> AndroidKeyEvent.KEYCODE_X
        Key.Y -> AndroidKeyEvent.KEYCODE_Y
        else -> rawKeyCodeFromValueClass() ?: rawKeyCodeFromToString() ?: AndroidKeyEvent.KEYCODE_UNKNOWN
    }
}

private fun Key.rawKeyCodeFromValueClass(): Int? {
    return runCatching {
        val boxed = this as Any
        val method = boxed.javaClass.declaredMethods.firstOrNull { method ->
            method.name.contains("unbox", ignoreCase = true) && method.parameterTypes.isEmpty()
        }
        if (method != null) {
            method.isAccessible = true
            val value = method.invoke(boxed)
            when (value) {
                is Long -> value.toInt()
                is Int -> value
                else -> null
            }
        } else {
            val field = boxed.javaClass.declaredFields.firstOrNull { field ->
                field.name == "keyCode" || field.type == java.lang.Long.TYPE || field.type == java.lang.Integer.TYPE
            }
            if (field == null) {
                null
            } else {
                field.isAccessible = true
                when (val value = field.get(boxed)) {
                    is Long -> value.toInt()
                    is Int -> value
                    else -> null
                }
            }
        }
    }.getOrNull()
}

private fun Key.rawKeyCodeFromToString(): Int? {
    return Regex("\\d+")
        .findAll(toString())
        .lastOrNull()
        ?.value
        ?.toIntOrNull()
}
