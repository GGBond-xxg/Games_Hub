package com.bond.md3elauncher.emulator.fc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.bond.md3elauncher.emulator.common.CommonEmulatorUiSpec
import com.bond.md3elauncher.emulator.common.CommonTouchKeyMap
import com.bond.md3elauncher.emulator.common.CommonTouchLabels
import com.bond.md3elauncher.emulator.common.CommonTouchLayoutBuilder
import com.bond.md3elauncher.i18n.I18n
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class FcMenuPage {
    MAIN,
    SAVE,
    VIRTUAL_KEYS,
    VIRTUAL_ALPHA,
    VIRTUAL_TOUCH_ALPHA,
    VIRTUAL_EDITOR,
    CHEATS,
    RESET_CONFIRM
}

internal class FcTouchControlsView(context: Context) : View(context) {
    private val activity = context as InternalFcActivity
    private val buttons = mutableListOf<FcTouchButton>()
    private val activePointers = mutableMapOf<Int, FcTouchButton>()
    private val menuRows = mutableListOf<FcMenuRow>()
    private val dpadRects = linkedMapOf<Int, RectF>()
    private val dpadOuter = RectF()
    private val dpadCenter = RectF()
    private val leftStickOuter = RectF()
    private val leftStickCenter = RectF()
    private val rightStickOuter = RectF()
    private val rightStickCenter = RectF()
    private val menuIconRect = RectF()
    private val menuPanelRect = RectF()
    private val stateSaveButtonRect = RectF()
    private val stateLoadButtonRect = RectF()
    private val stateDeleteButtonRect = RectF()
    private val layoutOverrides = mutableMapOf<String, RectF>()

    private var menuOpen = false
    private var menuPage = FcMenuPage.MAIN
    private var menuSelected = 0
    private var saveSelected = 0
    private var virtualSelected = 0
    private var resetSelected = 1
    private var hardwareMode = false
    private var largeTextSize = 18f
    private var smallTextSize = 12f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private fun tr(key: String, fallback: String, vararg args: Pair<String, Any?>): String =
        I18n.t(context, key, fallback, *args)

    private fun trShort(key: String, fallback: String, maxChars: Int = 8, vararg args: Pair<String, Any?>): String =
        I18n.short(context, key, fallback, maxChars = maxChars, *args)

    init {
        layoutOverrides.putAll(loadLayoutOverrides())
        isClickable = true
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout(w.toFloat(), h.toFloat())
    }

    private fun rebuildLayout(w: Float, h: Float) {
        buttons.clear()
        dpadRects.clear()
        menuRows.clear()
        if (w <= 0f || h <= 0f) return

        val layout = CommonTouchLayoutBuilder.buildGbaStyleLayout(
            width = w,
            height = h,
            density = resources.displayMetrics.density,
            keys = CommonTouchKeyMap(
                x = if (activity.isSfcMode) KeyEvent.KEYCODE_BUTTON_X else FC_VIRTUAL_TURBO_A,
                y = if (activity.isSfcMode) KeyEvent.KEYCODE_BUTTON_Y else FC_VIRTUAL_TURBO_B,
                quickSave = FC_VIRTUAL_QUICK_SAVE,
                quickLoad = FC_VIRTUAL_QUICK_LOAD,
                fastForward = FC_VIRTUAL_FAST_FORWARD,
                exit = FC_VIRTUAL_EXIT
            ),
            labels = CommonTouchLabels(
                quickSave = I18n.short(context, "emulator.short.quick_save", "Save", maxChars = 4),
                quickLoad = I18n.short(context, "emulator.short.quick_load", "Load", maxChars = 4),
                fastForward = I18n.short(context, "emulator.short.fast_forward", "Fast", maxChars = 4),
                exit = I18n.short(context, "emulator.short.exit", "Exit", maxChars = 4)
            )
        )

        dpadOuter.set(layout.dpadOuter)
        dpadCenter.set(layout.dpadCenter)
        leftStickOuter.set(layout.leftStickOuter)
        leftStickCenter.set(layout.leftStickCenter)
        rightStickOuter.set(layout.rightStickOuter)
        rightStickCenter.set(layout.rightStickCenter)
        menuIconRect.set(layout.menuIconRect)
        menuPanelRect.set(layout.menuPanelRect)
        largeTextSize = layout.largeTextSize
        smallTextSize = layout.smallTextSize

        dpadRects.putAll(layout.dpadRects)
        dpadRects.forEach { (key, rect) ->
            buttons += FcTouchButton("dpad_$key", "", setOf(key), RectF(rect))
        }
        layout.buttons.forEach { item ->
            buttons += FcTouchButton(item.id, item.label, item.keyCodes, RectF(item.rect), item.circle)
        }
        applyLayoutOverrides(w, h)
    }

    private fun loadLayoutOverrides(): MutableMap<String, RectF> {
        val result = mutableMapOf<String, RectF>()
        val raw = activity.settingsPrefs.getString("touch_layout_rects", "").orEmpty()
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

    private fun applyLayoutOverrides(viewW: Float, viewH: Float) {
        if (viewW <= 0f || viewH <= 0f || layoutOverrides.isEmpty()) return
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
        buttons.forEach { button -> applyToRect(button.id, button.rect) }
    }

    private fun rebuildDpadRectsFromOuter() {
        if (dpadOuter.width() <= 0f || dpadOuter.height() <= 0f) return
        val cellW = dpadOuter.width() / 3f
        val cellH = dpadOuter.height() / 3f
        val cx = dpadOuter.centerX()
        val cy = dpadOuter.centerY()
        dpadCenter.set(cx - cellW / 2f, cy - cellH / 2f, cx + cellW / 2f, cy + cellH / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(cx - cellW / 2f, dpadOuter.top, cx + cellW / 2f, dpadOuter.top + cellH)
        dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(cx - cellW / 2f, dpadOuter.bottom - cellH, cx + cellW / 2f, dpadOuter.bottom)
        dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(dpadOuter.left, cy - cellH / 2f, dpadOuter.left + cellW, cy + cellH / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(dpadOuter.right - cellW, cy - cellH / 2f, dpadOuter.right, cy + cellH / 2f)
        buttons.removeAll { it.id.startsWith("dpad_") }
        val dpadButtons = dpadRects.map { (key, rect) -> FcTouchButton("dpad_$key", "", setOf(key), RectF(rect)) }
        buttons.addAll(0, dpadButtons)
    }

    private fun rebuildStickCenter(outer: RectF, center: RectF) {
        if (outer.width() <= 0f || outer.height() <= 0f) return
        val r = min(outer.width(), outer.height()) * 0.35f
        center.set(outer.centerX() - r / 2f, outer.centerY() - r / 2f, outer.centerX() + r / 2f, outer.centerY() + r / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shouldDrawControls = !menuOpen || menuPage == FcMenuPage.VIRTUAL_KEYS || menuPage == FcMenuPage.VIRTUAL_ALPHA || menuPage == FcMenuPage.VIRTUAL_TOUCH_ALPHA
        if (shouldDrawControls) {
            val alpha = when {
                menuOpen && menuPage == FcMenuPage.VIRTUAL_ALPHA -> activity.hardwareControlsAlpha
                menuOpen && (menuPage == FcMenuPage.VIRTUAL_KEYS || menuPage == FcMenuPage.VIRTUAL_TOUCH_ALPHA) -> activity.touchControlsAlpha
                hardwareMode -> activity.hardwareControlsAlpha
                else -> activity.touchControlsAlpha
            }.coerceIn(0f, 1f)
            if (alpha > 0.01f) {
                drawDpad(canvas, alpha)
                drawAnalogStick(canvas, leftStickOuter, leftStickCenter, "L", alpha)
                drawAnalogStick(canvas, rightStickOuter, rightStickCenter, "R", alpha)
                buttons.filterNot { it.id.startsWith("dpad_") }.forEach { button ->
                    val pressed = when (button.keyCode) {
                        FC_VIRTUAL_FAST_FORWARD -> activity.fastForwardSpeed > 1
                        FC_VIRTUAL_TURBO_A -> activity.isTurboActive(FC_VIRTUAL_TURBO_A)
                        FC_VIRTUAL_TURBO_B -> activity.isTurboActive(FC_VIRTUAL_TURBO_B)
                        else -> activePointers.values.any { it.id == button.id }
                    }
                    drawButton(canvas, button, pressed, alpha)
                }
                drawMenuIcon(canvas, alpha)
            }
        }

        if (menuOpen) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
            drawMenuPanel(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (hardwareMode && event.actionMasked == MotionEvent.ACTION_DOWN) {
            hardwareMode = false
            invalidate()
        }

        if (handleMenuTouch(event)) return true

        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            releaseAll()
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                handlePointerDown(pointerId, event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    handlePointerMove(pointerId, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                handlePointerUp(pointerId)
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

    private fun handleMenuTouch(event: MotionEvent): Boolean {
        val index = event.actionIndex.coerceAtMost(event.pointerCount - 1)
        val x = event.getX(index)
        val y = event.getY(index)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && menuIconRect.contains(x, y)) {
            if (menuOpen) hideMenu() else openMenu()
            return true
        }
        if (!menuOpen) return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (!menuPanelRect.contains(x, y)) {
            hideMenu()
            return true
        }
        if (menuPage == FcMenuPage.SAVE) {
            when {
                stateSaveButtonRect.contains(x, y) -> {
                    saveSelectedState()
                    invalidate()
                    return true
                }
                stateLoadButtonRect.contains(x, y) -> {
                    loadSelectedState()
                    invalidate()
                    return true
                }
                stateDeleteButtonRect.contains(x, y) -> {
                    deleteSelectedState()
                    invalidate()
                    return true
                }
            }
        }
        val row = menuRows.firstOrNull { it.rect.contains(x, y) }
        if (row != null) {
            setCurrentIndex(row.index)
            confirmSelection()
            invalidate()
        }
        return true
    }

    private fun handlePointerDown(pointerId: Int, x: Float, y: Float) {
        val button = findButton(x, y) ?: return
        activePointers[pointerId] = button
        pressButton(button)
        checkVirtualExitCombo()
        invalidate()
    }

    private fun handlePointerMove(pointerId: Int, x: Float, y: Float) {
        val old = activePointers[pointerId]
        val next = findButton(x, y)
        if (old?.id == next?.id) return
        if (old != null) releaseButton(old)
        if (next != null) {
            activePointers[pointerId] = next
            pressButton(next)
        } else {
            activePointers.remove(pointerId)
        }
        checkVirtualExitCombo()
        invalidate()
    }

    private fun handlePointerUp(pointerId: Int) {
        val button = activePointers.remove(pointerId)
        if (button != null) {
            releaseButton(button)
            checkVirtualExitCombo()
            invalidate()
        }
    }

    private fun checkVirtualExitCombo() {
        val keys = activePointers.values.flatMap { it.keyCodes }.toSet()
        val exitComboKey = if (activity.isSfcMode) KeyEvent.KEYCODE_BUTTON_X else FC_VIRTUAL_TURBO_A
        if (KeyEvent.KEYCODE_BUTTON_SELECT in keys && exitComboKey in keys) {
            releaseAll()
            activity.exitGame()
        }
    }

    private fun findButton(x: Float, y: Float): FcTouchButton? {
        directionalButtonFromStick(x, y, leftStickOuter, leftStickCenter, "left")?.let { return it }
        directionalButtonFromStick(x, y, rightStickOuter, rightStickCenter, "right")?.let { return it }
        return buttons.lastOrNull { button ->
            if (button.circle) {
                val r = min(button.rect.width(), button.rect.height()) / 2f
                hypot(x - button.rect.centerX(), y - button.rect.centerY()) <= r
            } else {
                button.rect.contains(x, y)
            }
        }
    }

    private fun directionalButtonFromStick(x: Float, y: Float, outer: RectF, center: RectF, prefix: String): FcTouchButton? {
        if (!outer.contains(x, y)) return null
        val dx = x - center.centerX()
        val dy = y - center.centerY()
        val deadZone = outer.width() * 0.12f
        if (hypot(dx, dy) < deadZone) return null
        val key = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            if (dy > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        }
        return FcTouchButton("${prefix}_stick_$key", "", setOf(key), RectF(outer), circle = true)
    }

    private fun pressButton(button: FcTouchButton) {
        when (button.keyCode) {
            FC_VIRTUAL_FAST_FORWARD -> activity.cycleFastForwardSpeed()
            FC_VIRTUAL_QUICK_SAVE -> activity.saveQuickState()
            FC_VIRTUAL_QUICK_LOAD -> activity.loadQuickState(hideMenuAfterLoad = false)
            FC_VIRTUAL_EXIT -> activity.exitGame()
            FC_VIRTUAL_TURBO_A -> activity.startTurbo(FC_VIRTUAL_TURBO_A, KeyEvent.KEYCODE_BUTTON_A)
            FC_VIRTUAL_TURBO_B -> activity.startTurbo(FC_VIRTUAL_TURBO_B, KeyEvent.KEYCODE_BUTTON_B)
            else -> button.keyCodes.forEach { key -> activity.sendVirtualKey(KeyEvent.ACTION_DOWN, key) }
        }
    }

    private fun releaseButton(button: FcTouchButton) {
        when (button.keyCode) {
            FC_VIRTUAL_TURBO_A -> activity.stopTurbo(FC_VIRTUAL_TURBO_A)
            FC_VIRTUAL_TURBO_B -> activity.stopTurbo(FC_VIRTUAL_TURBO_B)
            FC_VIRTUAL_FAST_FORWARD,
            FC_VIRTUAL_QUICK_SAVE,
            FC_VIRTUAL_QUICK_LOAD,
            FC_VIRTUAL_EXIT -> Unit
            else -> button.keyCodes.forEach { key -> activity.sendVirtualKey(KeyEvent.ACTION_UP, key) }
        }
    }

    fun releaseAll() {
        activePointers.values.forEach { releaseButton(it) }
        activePointers.clear()
        invalidate()
    }

    fun isMenuOpen(): Boolean = menuOpen

    fun markHardwareControllerUsed() {
        if (!hardwareMode) {
            releaseAll()
            hardwareMode = true
            invalidate()
        }
    }

    fun hideMenu() {
        menuOpen = false
        menuPage = FcMenuPage.MAIN
        menuSelected = 0
        releaseAll()
        activity.resumeRetroAfterMenu()
        invalidate()
    }

    fun toggleMenu() {
        if (menuOpen) hideMenu() else openMenu()
    }

    fun openMenuFromShortcut() {
        if (!menuOpen) openMenu()
    }

    private fun openMenu() {
        menuOpen = true
        menuPage = FcMenuPage.MAIN
        menuSelected = 0
        releaseAll()
        activity.pauseRetroForMenu()
        invalidate()
    }

    fun handleHardwareMenuKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isMenuKey = keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE

        if (event.action == KeyEvent.ACTION_DOWN) markHardwareControllerUsed()

        if (!menuOpen && isMenuKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openMenu()
            return true
        }

        if (!menuOpen) return false
        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN) return true

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveSelection(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> adjustCurrentPage(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjustCurrentPage(1)
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE -> confirmSelection()
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE -> backFromMenu()
            KeyEvent.KEYCODE_BUTTON_X -> deleteSelectionIfAvailable()
            KeyEvent.KEYCODE_BUTTON_Y -> loadSelectionIfAvailable()
            else -> Unit
        }
        invalidate()
        return true
    }

    private fun currentCount(): Int = when (menuPage) {
        FcMenuPage.MAIN -> CommonEmulatorUiSpec.mainMenuItems(context).size
        FcMenuPage.SAVE -> CommonEmulatorUiSpec.MAX_STATE_SLOTS + 1
        FcMenuPage.VIRTUAL_KEYS -> CommonEmulatorUiSpec.virtualKeyMenuItems(context).size
        FcMenuPage.VIRTUAL_ALPHA -> 1
        FcMenuPage.VIRTUAL_TOUCH_ALPHA -> 1
        FcMenuPage.VIRTUAL_EDITOR -> 1
        FcMenuPage.CHEATS -> 1
        FcMenuPage.RESET_CONFIRM -> 2
    }

    private fun currentIndex(): Int = when (menuPage) {
        FcMenuPage.MAIN -> menuSelected
        FcMenuPage.SAVE -> saveSelected
        FcMenuPage.VIRTUAL_KEYS -> virtualSelected
        FcMenuPage.VIRTUAL_ALPHA -> 0
        FcMenuPage.VIRTUAL_TOUCH_ALPHA -> 0
        FcMenuPage.VIRTUAL_EDITOR -> 0
        FcMenuPage.CHEATS -> 0
        FcMenuPage.RESET_CONFIRM -> resetSelected
    }

    private fun setCurrentIndex(index: Int) {
        val fixed = index.coerceIn(0, max(0, currentCount() - 1))
        when (menuPage) {
            FcMenuPage.MAIN -> menuSelected = fixed
            FcMenuPage.SAVE -> saveSelected = fixed
            FcMenuPage.VIRTUAL_KEYS -> virtualSelected = fixed
            FcMenuPage.VIRTUAL_ALPHA -> Unit
            FcMenuPage.VIRTUAL_TOUCH_ALPHA -> Unit
            FcMenuPage.VIRTUAL_EDITOR -> Unit
            FcMenuPage.CHEATS -> Unit
            FcMenuPage.RESET_CONFIRM -> resetSelected = fixed
        }
    }

    private fun moveSelection(delta: Int) {
        val count = currentCount()
        if (count <= 0) return
        setCurrentIndex((currentIndex() + delta + count) % count)
    }

    private fun adjustCurrentPage(delta: Int) {
        when (menuPage) {
            FcMenuPage.VIRTUAL_ALPHA -> {
                activity.hardwareControlsAlpha = (activity.hardwareControlsAlpha + delta * 0.05f).coerceIn(0f, 1f)
                activity.showToast(tr("emulator.virtual.alpha_title", "Real Controller Opacity: {percent}%", "percent" to (activity.hardwareControlsAlpha * 100).roundToInt()))
            }
            FcMenuPage.VIRTUAL_TOUCH_ALPHA -> {
                activity.touchControlsAlpha = (activity.touchControlsAlpha + delta * 0.05f).coerceIn(0f, 1f)
                activity.showToast(tr("emulator.virtual.touch_alpha_title", "Virtual Controller Opacity: {percent}%", "percent" to (activity.touchControlsAlpha * 100).roundToInt()))
            }
            FcMenuPage.VIRTUAL_EDITOR -> Unit
            else -> moveSelection(delta)
        }
    }

    private fun confirmSelection() {
        when (menuPage) {
            FcMenuPage.MAIN -> when (menuSelected) {
                0 -> enterPage(FcMenuPage.SAVE)
                1 -> enterPage(FcMenuPage.VIRTUAL_KEYS)
                2 -> enterPage(FcMenuPage.CHEATS)
                3 -> enterPage(FcMenuPage.RESET_CONFIRM)
                4 -> activity.softRestartGameNoExit()
                5 -> activity.exitGame()
            }
            FcMenuPage.SAVE -> saveSelectedState()
            FcMenuPage.VIRTUAL_KEYS -> when (virtualSelected) {
                0 -> enterPage(FcMenuPage.VIRTUAL_ALPHA)
                1 -> enterPage(FcMenuPage.VIRTUAL_TOUCH_ALPHA)
                2 -> enterPage(FcMenuPage.VIRTUAL_EDITOR)
            }
            FcMenuPage.VIRTUAL_ALPHA -> Unit
            FcMenuPage.VIRTUAL_TOUCH_ALPHA -> Unit
            FcMenuPage.VIRTUAL_EDITOR -> activity.showToast(tr("emulator.hint.fc_virtual_editor", "Common virtual button editor is reserved for this core."))
            FcMenuPage.CHEATS -> activity.showToast(if (activity.isSfcMode) tr("emulator.hint.sfc_cheats", "SFC/SNES built-in cheats are reserved for later.") else tr("emulator.hint.fc_cheats", "FC/NES built-in cheats are reserved for later."))
            FcMenuPage.RESET_CONFIRM -> {
                if (resetSelected == 0) activity.restartGameFresh() else backFromMenu()
            }
        }
    }

    private fun enterPage(page: FcMenuPage) {
        menuPage = page
        when (page) {
            FcMenuPage.MAIN -> menuSelected = 0
            FcMenuPage.SAVE -> saveSelected = 0
            FcMenuPage.VIRTUAL_KEYS -> virtualSelected = 0
            FcMenuPage.VIRTUAL_ALPHA -> Unit
            FcMenuPage.VIRTUAL_TOUCH_ALPHA -> Unit
            FcMenuPage.VIRTUAL_EDITOR -> Unit
            FcMenuPage.CHEATS -> Unit
            FcMenuPage.RESET_CONFIRM -> resetSelected = 1
        }
        invalidate()
    }

    private fun backFromMenu() {
        when (menuPage) {
            FcMenuPage.MAIN -> hideMenu()
            FcMenuPage.VIRTUAL_ALPHA, FcMenuPage.VIRTUAL_TOUCH_ALPHA, FcMenuPage.VIRTUAL_EDITOR -> {
                val previous = menuPage
                enterPage(FcMenuPage.VIRTUAL_KEYS)
                virtualSelected = when (previous) {
                    FcMenuPage.VIRTUAL_ALPHA -> 0
                    FcMenuPage.VIRTUAL_TOUCH_ALPHA -> 1
                    else -> 2
                }
            }
            else -> enterPage(FcMenuPage.MAIN)
        }
    }

    private fun saveSelectedState() {
        if (saveSelected >= FC_MAX_STATE_SLOTS) activity.saveQuickState() else activity.saveSlotState(saveSelected + 1)
    }

    private fun loadSelectedState() {
        if (saveSelected >= FC_MAX_STATE_SLOTS) {
            activity.loadQuickState(hideMenuAfterLoad = true)
            return
        }
        val slot = saveSelected + 1
        val file = activity.gameStorage?.slotStateFile(slot)
        if (file != null && file.exists() && file.length() > 0L) {
            activity.loadSlotState(slot, hideMenuAfterLoad = true)
        } else {
            activity.showToast(tr("emulator.state.slot_empty", "Save {slot} is empty", "slot" to slot))
        }
    }

    private fun deleteSelectedState() {
        if (saveSelected >= FC_MAX_STATE_SLOTS) activity.deleteQuickState() else activity.deleteSlotState(saveSelected + 1)
    }

    private fun deleteSelectionIfAvailable() {
        if (menuPage == FcMenuPage.SAVE) deleteSelectedState()
    }

    private fun loadSelectionIfAvailable() {
        if (menuPage == FcMenuPage.SAVE) loadSelectedState()
    }

    private fun drawDpad(canvas: Canvas, alpha: Float) {
        val corner = dpadOuter.width() / 10f
        strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
        strokePaint.color = alphaColor(150, alpha)
        dpadRects.forEach { (key, rect) ->
            val pressed = activePointers.values.any { it.keyCodes.contains(key) }
            fillPaint.color = alphaColor(if (pressed) 110 else 40, alpha)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
        }
        fillPaint.color = alphaColor(28, alpha)
        canvas.drawRoundRect(dpadCenter, corner, corner, fillPaint)
        canvas.drawRoundRect(dpadCenter, corner, corner, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = largeTextSize
        drawCenteredText(canvas, "▲", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_UP), false, alpha)
        drawCenteredText(canvas, "▼", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_DOWN), false, alpha)
        drawCenteredText(canvas, "◀", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_LEFT), false, alpha)
        drawCenteredText(canvas, "▶", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_RIGHT), false, alpha)
    }

    private fun drawAnalogStick(canvas: Canvas, outer: RectF, center: RectF, label: String, alpha: Float) {
        if (outer.width() <= 0f || outer.height() <= 0f) return
        fillPaint.color = alphaColor(34, alpha)
        strokePaint.color = alphaColor(135, alpha)
        strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
        canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, fillPaint)
        canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, strokePaint)
        fillPaint.color = alphaColor(48, alpha)
        canvas.drawCircle(center.centerX(), center.centerY(), center.width() / 2f, fillPaint)
        textPaint.textSize = smallTextSize
        drawCenteredText(canvas, label, outer, false, alpha)
    }

    private fun drawButton(canvas: Canvas, button: FcTouchButton, pressed: Boolean, alpha: Float) {
        fillPaint.color = alphaColor(if (pressed) 115 else 42, alpha)
        strokePaint.color = alphaColor(if (pressed) 230 else 150, alpha)
        strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
        if (button.circle) {
            canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, fillPaint)
            canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, strokePaint)
        } else {
            val radius = button.rect.height() / 2f
            canvas.drawRoundRect(button.rect, radius, radius, fillPaint)
            canvas.drawRoundRect(button.rect, radius, radius, strokePaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = if (button.label.length <= 2) largeTextSize else smallTextSize
        drawCenteredText(canvas, button.label, button.rect, pressed, alpha)
    }

    private fun drawMenuIcon(canvas: Canvas, alpha: Float) {
        fillPaint.color = alphaColor(50, alpha)
        strokePaint.color = alphaColor(170, alpha)
        strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
        val radius = menuIconRect.height() / 2f
        canvas.drawRoundRect(menuIconRect, radius, radius, fillPaint)
        canvas.drawRoundRect(menuIconRect, radius, radius, strokePaint)
        textPaint.textSize = largeTextSize
        drawCenteredText(canvas, "☰", menuIconRect, false, alpha)
    }

    private fun drawMenuPanel(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        fillPaint.color = Color.argb(232, 18, 22, 30)
        strokePaint.color = Color.argb(220, 255, 255, 255)
        strokePaint.strokeWidth = 1.5f * dp
        canvas.drawRoundRect(menuPanelRect, 22f, 22f, fillPaint)
        canvas.drawRoundRect(menuPanelRect, 22f, 22f, strokePaint)

        menuRows.clear()
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 18f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(menuTitle(), menuPanelRect.left + 20f * dp, menuPanelRect.top + 36f * dp, textPaint)

        subTextPaint.textSize = 11.5f * dp
        subTextPaint.typeface = Typeface.DEFAULT
        subTextPaint.color = Color.argb(210, 255, 255, 255)
        val hint = menuHint()
        if (hint.isNotBlank()) {
            drawFittedText(canvas, hint, menuPanelRect.left + 20f * dp, menuPanelRect.top + 58f * dp, menuPanelRect.width() - 40f * dp, subTextPaint)
        }

        when (menuPage) {
            FcMenuPage.MAIN -> drawList(canvas, CommonEmulatorUiSpec.mainMenuItems(context), menuSelected, menuPanelRect.top + 64f * dp) { index -> subtitleForMain(index) }
            FcMenuPage.SAVE -> drawSaveManager(canvas)
            FcMenuPage.VIRTUAL_KEYS -> drawList(canvas, CommonEmulatorUiSpec.virtualKeyMenuItems(context), virtualSelected, menuPanelRect.top + 64f * dp) { index -> virtualSubtitle(index) }
            FcMenuPage.VIRTUAL_ALPHA -> drawVirtualAlphaSettings(canvas, isTouch = false)
            FcMenuPage.VIRTUAL_TOUCH_ALPHA -> drawVirtualAlphaSettings(canvas, isTouch = true)
            FcMenuPage.VIRTUAL_EDITOR -> drawList(canvas, virtualEditorLabels(), 0, menuPanelRect.top + 64f * dp) { index -> virtualEditorSubtitle(index) }
            FcMenuPage.CHEATS -> drawList(canvas, listOf(if (activity.isSfcMode) tr("emulator.hint.sfc_cheats", "SFC/SNES built-in cheats are reserved for later.") else tr("emulator.hint.fc_cheats", "FC/NES built-in cheats are reserved for later.")), 0, menuPanelRect.top + 76f * dp) { _ -> tr("emulator.hint.fc_cheats_external", "You can still use cheat features in an external emulator.") }
            FcMenuPage.RESET_CONFIRM -> drawList(canvas, listOf(tr("emulator.reset.confirm", "A: Confirm reload game"), tr("common.back", "Back")), resetSelected, menuPanelRect.top + 92f * dp) { index -> if (index == 0) CommonEmulatorUiSpec.resetHint(context) else tr("emulator.reset.cancel", "B: Cancel and return") }
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawSaveManager(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        stateSaveButtonRect.setEmpty()
        stateLoadButtonRect.setEmpty()
        stateDeleteButtonRect.setEmpty()

        val entries = buildList {
            for (slot in 1..FC_MAX_STATE_SLOTS) {
                val file = activity.gameStorage?.slotStateFile(slot)
                add("${tr("emulator.state.slot_label", "Save {slot}", "slot" to slot)}    ${stateLabel(file)}")
            }
            add("${tr("emulator.state.quick_label", "Quick Save")}    ${stateLabel(activity.gameStorage?.quickStateFile)}")
        }

        val left = menuPanelRect.left + 20f * dp
        val right = menuPanelRect.right - 20f * dp
        val top = menuPanelRect.top + 68f * dp
        val actionH = 34f * dp
        val actionBottom = menuPanelRect.bottom - 14f * dp
        val listBottom = actionBottom - actionH - 16f * dp
        val available = (listBottom - top).coerceAtLeast(1f)
        val gap = 4f * dp
        val separatorH = 10f * dp
        val minRowH = 26f * dp
        val maxRowH = 34f * dp
        val selected = saveSelected.coerceIn(0, entries.lastIndex)

        // 和 GBA 存档页一样：小屏横屏只绘制可见行，选中项自动滚动。
        // 每行只放一行文字，不再把说明文字塞进行内，避免字体上下重叠。
        var visibleCount = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
        visibleCount = min(entries.size, visibleCount)
        fun calcStart(count: Int): Int = when {
            entries.size <= count -> 0
            selected <= count / 2 -> 0
            selected >= entries.size - (count - count / 2) -> entries.size - count
            else -> selected - count / 2
        }.coerceIn(0, max(0, entries.size - count))

        var startIndex = calcStart(visibleCount)
        var quickVisible = startIndex <= FC_MAX_STATE_SLOTS && startIndex + visibleCount > FC_MAX_STATE_SLOTS
        var rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
        fun usedHeight(count: Int, itemHeight: Float, showSeparator: Boolean): Float {
            return count * itemHeight + gap * (count - 1) + if (showSeparator) separatorH else 0f
        }
        while (visibleCount > 1 && top + usedHeight(visibleCount, rowH, quickVisible) > listBottom) {
            visibleCount -= 1
            startIndex = calcStart(visibleCount)
            quickVisible = startIndex <= FC_MAX_STATE_SLOTS && startIndex + visibleCount > FC_MAX_STATE_SLOTS
            rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
        }

        var y = top
        for (offset in 0 until visibleCount) {
            val index = startIndex + offset
            if (index !in entries.indices) break
            if (index == FC_MAX_STATE_SLOTS) {
                val lineY = y + separatorH * 0.35f
                strokePaint.strokeWidth = 1.1f * dp
                strokePaint.color = Color.argb(130, 255, 255, 255)
                canvas.drawLine(left + 8f * dp, lineY, right - 8f * dp, lineY, strokePaint)
                y += separatorH
            }
            if (y + rowH > listBottom + 0.5f * dp) break

            val row = RectF(left, y, right, y + rowH)
            menuRows += FcMenuRow(index, row)
            val isSelected = index == selected
            fillPaint.color = if (isSelected) Color.argb(105, 95, 150, 255) else Color.argb(38, 255, 255, 255)
            strokePaint.color = if (isSelected) Color.argb(220, 255, 255, 255) else Color.argb(80, 255, 255, 255)
            strokePaint.strokeWidth = 1.2f * dp
            canvas.drawRoundRect(row, rowH / 2f, rowH / 2f, fillPaint)
            canvas.drawRoundRect(row, rowH / 2f, rowH / 2f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = min(13f * dp, rowH * 0.34f)
            textPaint.color = Color.WHITE
            drawFittedText(
                canvas,
                entries[index],
                row.left + 18f * dp,
                row.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f,
                row.width() - 36f * dp,
                textPaint
            )
            y += rowH + gap
        }

        if (entries.size > visibleCount) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 10f * dp
            textPaint.color = Color.argb(175, 255, 255, 255)
            val hint = when {
                startIndex == 0 -> tr("emulator.list.more_down", "More ↓")
                startIndex + visibleCount >= entries.size -> tr("emulator.list.more_up", "↑ More")
                else -> tr("emulator.list.more_both", "↑ More ↓")
            }
            canvas.drawText(hint, right - 8f * dp, listBottom - 4f * dp, textPaint)
        }

        val selectedFile = if (selected >= FC_MAX_STATE_SLOTS) {
            activity.gameStorage?.quickStateFile
        } else {
            activity.gameStorage?.slotStateFile(selected + 1)
        }
        val hasState = selectedFile?.let { it.exists() && it.length() > 0L } == true
        val actionGap = 10f * dp
        val buttonW = ((right - left - actionGap * 2f) / 3f).coerceAtLeast(74f * dp)
        stateSaveButtonRect.set(left, actionBottom - actionH, left + buttonW, actionBottom)
        stateLoadButtonRect.set(stateSaveButtonRect.right + actionGap, actionBottom - actionH, stateSaveButtonRect.right + actionGap + buttonW, actionBottom)
        stateDeleteButtonRect.set(stateLoadButtonRect.right + actionGap, actionBottom - actionH, right, actionBottom)
        drawPanelButton(canvas, stateSaveButtonRect, tr("emulator.state.button_save", "A Save"), true)
        drawPanelButton(canvas, stateLoadButtonRect, tr("emulator.state.button_load", "Y Load"), hasState)
        drawPanelButton(canvas, stateDeleteButtonRect, tr("emulator.state.button_delete", "X Delete"), hasState)
    }

    private fun drawList(
        canvas: Canvas,
        labels: List<String>,
        selected: Int,
        top: Float,
        subtitle: (Int) -> String = { "" }
    ) {
        val dp = resources.displayMetrics.density
        if (labels.isEmpty()) return
        val left = menuPanelRect.left + 20f * dp
        val right = menuPanelRect.right - 20f * dp
        val bottomPadding = 12f * dp
        val available = (menuPanelRect.bottom - bottomPadding - top).coerceAtLeast(1f)
        val minRowH = 34f * dp
        val gap = min(7f * dp, max(3f * dp, available * 0.018f))
        val maxVisible = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
        val visibleCount = min(labels.size, maxVisible)
        val fixedSelected = selected.coerceIn(0, labels.lastIndex)
        val startIndex = when {
            labels.size <= visibleCount -> 0
            fixedSelected <= visibleCount / 2 -> 0
            fixedSelected >= labels.size - (visibleCount - visibleCount / 2) -> labels.size - visibleCount
            else -> fixedSelected - visibleCount / 2
        }.coerceIn(0, max(0, labels.size - visibleCount))
        val rowH = ((available - gap * (visibleCount - 1)) / visibleCount).coerceIn(minRowH, 48f * dp)

        for (offset in 0 until visibleCount) {
            val index = startIndex + offset
            val label = labels[index]
            val y = top + offset * (rowH + gap)
            val row = RectF(left, y, right, y + rowH)
            if (row.bottom > menuPanelRect.bottom - bottomPadding / 2f) break
            menuRows += FcMenuRow(index, row)
            val isSelected = index == fixedSelected
            fillPaint.color = if (isSelected) Color.argb(105, 95, 150, 255) else Color.argb(38, 255, 255, 255)
            strokePaint.color = if (isSelected) Color.argb(220, 255, 255, 255) else Color.argb(80, 255, 255, 255)
            strokePaint.strokeWidth = 1.2f * dp
            canvas.drawRoundRect(row, rowH / 2f, rowH / 2f, fillPaint)
            canvas.drawRoundRect(row, rowH / 2f, rowH / 2f, strokePaint)

            val sub = subtitle(index)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = min(13f * dp, rowH * 0.32f)
            textPaint.color = Color.WHITE
            val mainY = if (sub.isBlank()) {
                row.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            } else {
                row.top + rowH * 0.40f
            }
            drawFittedText(canvas, label, row.left + 18f * dp, mainY, row.width() - 36f * dp, textPaint)

            if (sub.isNotBlank()) {
                subTextPaint.textAlign = Paint.Align.LEFT
                subTextPaint.typeface = Typeface.DEFAULT
                subTextPaint.textSize = min(10.5f * dp, rowH * 0.23f)
                subTextPaint.color = Color.argb(190, 255, 255, 255)
                drawFittedText(canvas, sub, row.left + 18f * dp, row.top + rowH * 0.76f, row.width() - 36f * dp, subTextPaint)
            }
        }

        if (labels.size > visibleCount) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 10f * dp
            textPaint.color = Color.argb(175, 255, 255, 255)
            val hint = when {
                startIndex == 0 -> tr("emulator.list.more_down", "More ↓")
                startIndex + visibleCount >= labels.size -> tr("emulator.list.more_up", "↑ More")
                else -> tr("emulator.list.more_both", "↑ More ↓")
            }
            canvas.drawText(hint, right - 8f * dp, menuPanelRect.bottom - 8f * dp, textPaint)
        }
    }

    private fun drawPanelButton(canvas: Canvas, rect: RectF, label: String, selected: Boolean) {
        val dp = resources.displayMetrics.density
        fillPaint.color = if (selected) Color.argb(105, 95, 150, 255) else Color.argb(48, 255, 255, 255)
        strokePaint.color = Color.argb(150, 255, 255, 255)
        strokePaint.strokeWidth = 1.2f * dp
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 12f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    private fun menuTitle(): String = when (menuPage) {
        FcMenuPage.MAIN -> tr("emulator.menu.title", "Built-in {platform} Menu", "platform" to if (activity.isSfcMode) "SFC/SNES" else "FC/NES")
        FcMenuPage.SAVE -> tr("emulator.menu.save", "Save States")
        FcMenuPage.VIRTUAL_KEYS -> tr("emulator.menu.virtual_keys", "Virtual Buttons")
        FcMenuPage.VIRTUAL_ALPHA -> tr("emulator.menu.hardware_opacity", "Real Controller Opacity")
        FcMenuPage.VIRTUAL_TOUCH_ALPHA -> tr("emulator.menu.touch_opacity", "Virtual Controller Opacity")
        FcMenuPage.VIRTUAL_EDITOR -> tr("emulator.menu.virtual_editor", "Virtual Button Editor")
        FcMenuPage.CHEATS -> tr("emulator.menu.cheat", "Cheats")
        FcMenuPage.RESET_CONFIRM -> tr("emulator.menu.reset_game", "Reset Game")
    }

    private fun menuHint(): String = when (menuPage) {
        FcMenuPage.MAIN -> CommonEmulatorUiSpec.mainMenuHint(activity)
        FcMenuPage.SAVE -> CommonEmulatorUiSpec.saveMenuHint(context)
        FcMenuPage.VIRTUAL_KEYS -> CommonEmulatorUiSpec.virtualKeysHint(context)
        FcMenuPage.VIRTUAL_ALPHA -> tr("emulator.hint.virtual_alpha", "Adjust opacity when a real controller is connected. Left/right adjust, B back.")
        FcMenuPage.VIRTUAL_TOUCH_ALPHA -> tr("emulator.hint.virtual_touch_alpha", "Adjust opacity when using virtual touch controls. Left/right adjust, B back.")
        FcMenuPage.VIRTUAL_EDITOR -> tr("emulator.hint.fc_virtual_editor", "Common virtual button editor is reserved for this core.")
        FcMenuPage.CHEATS -> if (activity.isSfcMode) tr("emulator.hint.sfc_cheats", "SFC/SNES built-in cheats are reserved for later.") else tr("emulator.hint.fc_cheats", "FC/NES built-in cheats are reserved for later.")
        FcMenuPage.RESET_CONFIRM -> CommonEmulatorUiSpec.resetHint(context)
    }

    private fun subtitleForMain(index: Int): String = when (index) {
        0 -> tr("emulator.subtitle.save", "Manage saves, loads, deletes, and quick save")
        1 -> tr("emulator.subtitle.fc_virtual", "Real-controller opacity, virtual-controller opacity, and common editor entry")
        2 -> if (activity.isSfcMode) tr("emulator.subtitle.sfc_cheat", "SFC/SNES cheat entry reserved") else tr("emulator.subtitle.fc_cheat", "FC/NES cheat entry reserved")
        3 -> CommonEmulatorUiSpec.resetHint(context)
        4 -> CommonEmulatorUiSpec.restartHint(context)
        5 -> tr("emulator.subtitle.exit", "Exit to launcher")
        else -> ""
    }

    private fun virtualSubtitle(index: Int): String = when (index) {
        0 -> tr("emulator.virtual.subtitle.hardware_opacity", "Adjust opacity when a real controller is connected")
        1 -> tr("emulator.virtual.subtitle.touch_opacity", "Adjust opacity when using virtual touch controls")
        2 -> tr("emulator.virtual.subtitle.editor", "Drag button positions, resize with left/right, add combo buttons")
        else -> ""
    }

    private fun virtualEditorLabels(): List<String> = listOf(
        tr("emulator.menu.virtual_editor", "Virtual Button Editor")
    )

    private fun virtualEditorSubtitle(index: Int): String = when (index) {
        0 -> tr("emulator.virtual.editor_sub.common_editor_pending", "Common editor entry reserved; later it will share GBA drag/resize logic")
        else -> ""
    }

    private fun drawVirtualAlphaSettings(canvas: Canvas, isTouch: Boolean) {
        val dp = resources.displayMetrics.density
        val left = menuPanelRect.left + 26f * dp
        val right = menuPanelRect.right - 26f * dp
        val top = menuPanelRect.top + 96f * dp
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 15f * dp
        textPaint.color = Color.WHITE
        val value = if (isTouch) activity.touchControlsAlpha else activity.hardwareControlsAlpha
        val title = if (isTouch) {
            tr("emulator.virtual.touch_alpha_title", "Virtual Controller Opacity: {percent}%", "percent" to (value * 100).roundToInt())
        } else {
            tr("emulator.virtual.alpha_title", "Real Controller Opacity: {percent}%", "percent" to (value * 100).roundToInt())
        }
        canvas.drawText(title, left, top, textPaint)

        val cy = top + 42f * dp
        strokePaint.strokeWidth = 5f * dp
        strokePaint.color = Color.argb(95, 255, 255, 255)
        canvas.drawLine(left, cy, right, cy, strokePaint)
        val thumbX = left + (right - left) * value
        strokePaint.color = Color.argb(225, 255, 255, 255)
        canvas.drawLine(left, cy, thumbX, cy, strokePaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, cy, 10f * dp, fillPaint)
        strokePaint.strokeWidth = 2.5f * dp

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 12f * dp
        textPaint.color = Color.argb(205, 255, 255, 255)
        canvas.drawText(tr("emulator.virtual.alpha_left_right", "← More Transparent    → More Visible"), left, cy + 36f * dp, textPaint)
        val note = if (isTouch) {
            tr("emulator.virtual.touch_alpha_note", "Controls shown while using the touch screen. This does not affect real-controller mode.")
        } else {
            tr("emulator.virtual.alpha_note", "Default is 0%. This only applies when a real controller is connected.")
        }
        canvas.drawText(note, left, cy + 58f * dp, textPaint)
    }

    private fun stateLabel(file: File?): String = if (file != null && file.exists() && file.length() > 0L) activity.stateTime(file) else tr("emulator.state.empty", "Empty")

    private fun drawCenteredText(canvas: Canvas, label: String, rect: RectF, pressed: Boolean, alpha: Float) {
        textPaint.color = if (pressed) Color.argb((255 * alpha).roundToInt(), 160, 210, 255) else alphaColor(255, alpha)
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), y, textPaint)
    }

    private fun drawFittedText(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, baseline, paint)
            return
        }
        val ellipsis = "..."
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--
        canvas.drawText(text.substring(0, end) + ellipsis, x, baseline, paint)
    }

    private fun alphaColor(baseAlpha: Int, scale: Float): Int = Color.argb(
        (baseAlpha * scale).roundToInt().coerceIn(0, 255),
        255,
        255,
        255
    )

}
