package com.bond.md3elauncher.emulator.common

import android.graphics.RectF
import android.view.KeyEvent
import kotlin.math.max
import kotlin.math.min

/**
 * Standard virtual control layout shared by internal emulators.
 *
 * The geometry is copied from the current stable GBA layout and should be used
 * by FC/NES and future cores so users do not relearn controls per platform.
 */
internal data class CommonTouchButtonSpec(
    val id: String,
    val label: String,
    val keyCodes: Set<Int>,
    val rect: RectF,
    val circle: Boolean = false
) {
    val keyCode: Int get() = keyCodes.firstOrNull() ?: -999999
}

internal data class CommonTouchLayout(
    val buttons: List<CommonTouchButtonSpec>,
    val dpadRects: LinkedHashMap<Int, RectF>,
    val dpadOuter: RectF,
    val dpadCenter: RectF,
    val leftStickOuter: RectF,
    val leftStickCenter: RectF,
    val rightStickOuter: RectF,
    val rightStickCenter: RectF,
    val menuIconRect: RectF,
    val menuPanelRect: RectF,
    val largeTextSize: Float,
    val smallTextSize: Float,
    val dpadDeadZone: Float
)

internal data class CommonTouchKeyMap(
    val l1: Int = KeyEvent.KEYCODE_BUTTON_L1,
    val r1: Int = KeyEvent.KEYCODE_BUTTON_R1,
    val a: Int = KeyEvent.KEYCODE_BUTTON_A,
    val b: Int = KeyEvent.KEYCODE_BUTTON_B,
    val x: Int,
    val y: Int,
    val start: Int = KeyEvent.KEYCODE_BUTTON_START,
    val select: Int = KeyEvent.KEYCODE_BUTTON_SELECT,
    val quickSave: Int,
    val quickLoad: Int,
    val fastForward: Int,
    val exit: Int
)

internal object CommonTouchLayoutBuilder {
    fun buildGbaStyleLayout(
        width: Float,
        height: Float,
        density: Float,
        keys: CommonTouchKeyMap,
        customButtons: List<CommonTouchButtonSpec> = emptyList()
    ): CommonTouchLayout {
        fun dp(value: Float) = value * density
        val buttons = mutableListOf<CommonTouchButtonSpec>()
        val dpadRects = linkedMapOf<Int, RectF>()
        fun pillButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, w: Float, h: Float) {
            buttons += CommonTouchButtonSpec(id, label, setOf(keyCode), RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
        }
        fun circularButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, radius: Float) {
            buttons += CommonTouchButtonSpec(id, label, setOf(keyCode), RectF(cx - radius, cy - radius, cx + radius, cy + radius), circle = true)
        }

        val safeW = width.coerceAtLeast(1f)
        val safeH = height.coerceAtLeast(1f)
        val margin = max(dp(14f), min(safeW, safeH) * 0.035f)
        val primarySize = min(dp(126f), safeH * 0.29f).coerceAtLeast(min(dp(92f), safeH * 0.24f))
        val cell = primarySize / 3f
        val clusterCy = safeH - margin - primarySize / 2f
        val leftCx = margin + primarySize / 2f
        val rightCx = safeW - margin - primarySize / 2f

        val left = leftCx - primarySize / 2f
        val top = clusterCy - primarySize / 2f
        val dpadOuter = RectF(left, top, left + primarySize, top + primarySize)
        val dpadCenter = RectF(leftCx - cell / 2f, clusterCy - cell / 2f, leftCx + cell / 2f, clusterCy + cell / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(leftCx - cell / 2f, top, leftCx + cell / 2f, top + cell)
        dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(leftCx - cell / 2f, top + cell * 2f, leftCx + cell / 2f, top + primarySize)
        dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(left, clusterCy - cell / 2f, left + cell, clusterCy + cell / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(left + cell * 2f, clusterCy - cell / 2f, left + primarySize, clusterCy + cell / 2f)

        val smallPillH = min(dp(32f), safeH * 0.065f).coerceAtLeast(dp(26f))
        val shoulderW = min(dp(108f), safeW * 0.15f)
        val shoulderY = margin + smallPillH / 2f
        pillButton("l1", "L", keys.l1, margin + shoulderW / 2f, shoulderY, shoulderW, smallPillH)
        pillButton("r1", "R", keys.r1, safeW - margin - shoulderW / 2f, shoulderY, shoulderW, smallPillH)

        val stickRadius = min(primarySize * 0.36f, dp(48f))
        val leftStickCx = leftCx + primarySize * 1.08f
        val leftStickOuter = RectF(leftStickCx - stickRadius, clusterCy - stickRadius, leftStickCx + stickRadius, clusterCy + stickRadius)
        val leftStickCenter = RectF(leftStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, leftStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)
        val rightStickCx = rightCx - primarySize * 1.12f
        val rightStickOuter = RectF(rightStickCx - stickRadius, clusterCy - stickRadius, rightStickCx + stickRadius, clusterCy + stickRadius)
        val rightStickCenter = RectF(rightStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, rightStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)

        val faceRadius = min(dp(30f), primarySize * 0.25f)
        circularButton("b", "B", keys.b, rightCx - primarySize * 0.32f, clusterCy + primarySize * 0.18f, faceRadius)
        circularButton("a", "A", keys.a, rightCx + primarySize * 0.26f, clusterCy - primarySize * 0.20f, faceRadius)
        circularButton("y", "Y", keys.y, rightCx - primarySize * 0.34f, clusterCy - primarySize * 0.36f, faceRadius * 0.88f)
        circularButton("x", "X", keys.x, rightCx + primarySize * 0.26f, clusterCy + primarySize * 0.36f, faceRadius * 0.88f)

        val bottomPillY = safeH - margin - smallPillH / 2f
        pillButton("start", "START", keys.start, safeW / 2f + dp(62f), bottomPillY, dp(88f), smallPillH)
        pillButton("select", "SELECT", keys.select, safeW / 2f - dp(62f), bottomPillY, dp(92f), smallPillH)
        pillButton("quick_save", "快存", keys.quickSave, margin + shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
        pillButton("quick_load", "快读", keys.quickLoad, safeW - margin - shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
        pillButton("fast_forward", "快进", keys.fastForward, safeW - margin - shoulderW / 2f, shoulderY + (smallPillH + dp(8f)) * 2f, dp(76f), smallPillH)
        pillButton("exit", "退出", keys.exit, safeW / 2f, shoulderY, dp(86f), smallPillH)

        buttons += customButtons

        val menuSize = dp(42f)
        val menuTop = margin + smallPillH * 2f + dp(20f)
        val menuIconRect = RectF(margin, menuTop, margin + menuSize, menuTop + menuSize)

        val panelW = min(dp(560f), safeW - margin * 2f)
        val panelH = min(dp(420f), safeH - margin * 1.2f)
        val menuPanelRect = RectF((safeW - panelW) / 2f, (safeH - panelH) / 2f, (safeW + panelW) / 2f, (safeH + panelH) / 2f)

        return CommonTouchLayout(
            buttons = buttons,
            dpadRects = dpadRects,
            dpadOuter = dpadOuter,
            dpadCenter = dpadCenter,
            leftStickOuter = leftStickOuter,
            leftStickCenter = leftStickCenter,
            rightStickOuter = rightStickOuter,
            rightStickCenter = rightStickCenter,
            menuIconRect = menuIconRect,
            menuPanelRect = menuPanelRect,
            largeTextSize = dp(18f),
            smallTextSize = dp(12f),
            dpadDeadZone = cell * 0.26f
        )
    }
}
