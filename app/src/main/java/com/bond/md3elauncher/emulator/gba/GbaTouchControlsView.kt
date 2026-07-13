package com.bond.md3elauncher.emulator.gba

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.bond.md3elauncher.emulator.common.CommonEmulatorUiSpec
import com.bond.md3elauncher.i18n.I18n
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class GbaTouchControlsView(
    private val activity: InternalGbaActivity
) : View(activity) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
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

    private val buttons = mutableListOf<TouchButton>()
    private val dpadRects = linkedMapOf<Int, RectF>()
    private val pointerKeys = mutableMapOf<Int, Set<Int>>()
    private val pressedKeys = mutableSetOf<Int>()
    private val menuRows = mutableListOf<MenuRow>()
    private val dpadOuter = RectF()
    private val dpadCenter = RectF()
    private val leftStickOuter = RectF()
    private val leftStickCenter = RectF()
    private val rightStickOuter = RectF()
    private val rightStickCenter = RectF()
    private val menuIconRect = RectF()
    private val menuPanelRect = RectF()
    private val customDeleteButtonRect = RectF()
    private val cheatAddButtonRect = RectF()
    private val cheatEnableButtonRect = RectF()
    private val cheatResetButtonRect = RectF()
    private val cheatDeleteButtonRect = RectF()
    private val stateSaveButtonRect = RectF()
    private val stateDeleteButtonRect = RectF()
    private val stateLoadButtonRect = RectF()
    private var dpadDeadZone = 0f
    private var largeTextSize = 18f
    private var smallTextSize = 13f
    private var menuVisible = false
    private var menuPage = MenuPage.MAIN
    private var editorMode = false
    private var editorPanelOpen = false
    private var editorBallPointerId: Int? = null
    private var editorBallMoved = false
    private var editorBallLastX = 0f
    private var editorBallLastY = 0f
    private val editorBallRect = RectF()
    private val editorPanelRect = RectF()
    private val editorPanelCloseRect = RectF()
    private val editorPanelRows = mutableListOf<MenuRow>()
    private var menuIndex = 0
    private var saveSlotIndex = 0
    private var loadSlotIndex = 0
    private var deleteSlotIndex = 0
    private var cheatIndex = 0
    private var customCheatIndex = 0
    private var deleteCustomCheatIndex = 0
    private var virtualSettingsIndex = 0
    private var editorIndex = 0
    private var hardwareMode = false
    private var selectedEditId: String? = null
    private var editingPointerId: Int? = null
    private var lastEditX = 0f
    private var lastEditY = 0f
    private val customButtons = mutableListOf<CustomTouchButton>()
    private val layoutOverrides = mutableMapOf<String, RectF>()


    private fun tr(key: String, fallback: String, vararg args: Pair<String, Any?>): String =
        I18n.t(context, key, fallback, *args)

    private fun trShort(key: String, fallback: String, maxChars: Int = 8, vararg args: Pair<String, Any?>): String =
        I18n.short(context, key, fallback, maxChars = maxChars, *args)

    private fun virtualEditorItems(): List<String> = listOf(
        tr("emulator.virtual.item.add", "Add Custom Button"),
        tr("emulator.virtual.item.increase", "Increase Button Size"),
        tr("emulator.virtual.item.decrease", "Decrease Button Size"),
        tr("emulator.virtual.item.save_return", "Save and Return to Game"),
        tr("emulator.virtual.item.reset_layout", "Reset Layout"),
        tr("emulator.virtual.item.cancel_return", "Cancel / Return to Game")
    )

    init {
        customButtons.addAll(loadCustomButtons())
        layoutOverrides.putAll(loadLayoutOverrides())
        isClickable = true
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shouldDrawControls = editorMode || !menuVisible || menuPage == MenuPage.VIRTUAL_KEYS || menuPage == MenuPage.VIRTUAL_ALPHA
        if (shouldDrawControls) {
            val alpha = controlsAlphaForCurrentMode()
            if (alpha > 0f) {
                drawDpad(canvas, alpha)
                drawAnalogStick(canvas, leftStickOuter, leftStickCenter, "L", alpha)
                drawAnalogStick(canvas, rightStickOuter, rightStickCenter, "R", alpha)
                buttons.forEach { button ->
                    val pressed = when {
                        button.keyCodes.contains(VIRTUAL_FAST_FORWARD) -> activity.fastForwardSpeed > 1
                        button.keyCodes.contains(VIRTUAL_MENU) -> menuVisible
                        button.keyCodes.contains(VIRTUAL_TURBO_A) -> activity.isTurboActive(VIRTUAL_TURBO_A)
                        button.keyCodes.contains(VIRTUAL_TURBO_B) -> activity.isTurboActive(VIRTUAL_TURBO_B)
                        else -> button.keyCodes.any { pressedKeys.contains(it) }
                    }
                    drawButton(canvas, button, pressed, alpha)
                }
                drawMenuIcon(canvas, alpha)
                if (editorMode) drawEditSelection(canvas)
            }
        }

        if (editorMode) {
            drawEditorFloatingUi(canvas)
        } else if (menuVisible) {
            drawMenuPanel(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (hardwareMode && event.actionMasked == MotionEvent.ACTION_DOWN) {
            hardwareMode = false
            invalidate()
        }

        if (editorMode) {
            return handleEditorModeTouch(event)
        }

        if (handleMenuTouch(event)) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> updatePointer(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) updatePointer(event, i)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> releasePointer(event, event.actionIndex, cancelled = false)
            MotionEvent.ACTION_CANCEL -> {
                pointerKeys.clear()
                syncPressedKeys()
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

    fun releaseAll() {
        pointerKeys.clear()
        syncPressedKeys()
    }

    fun markHardwareControllerUsed() {
        if (!hardwareMode) {
            releaseAll()
            hardwareMode = true
            invalidate()
        }
    }

    fun hideMenu() {
        menuVisible = false
        menuPage = MenuPage.MAIN
        menuIndex = 0
        releaseAll()
        activity.resumeRetroAfterMenu()
        invalidate()
    }

    fun isMenuOpen(): Boolean = menuVisible

    fun handleHardwareMenuKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isMenuKey = keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_BACK

        if (event.action == KeyEvent.ACTION_DOWN) markHardwareControllerUsed()

        if (editorMode) {
            return handleEditorHardwareKey(event)
        }

        if (!menuVisible && isMenuKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                openMenu()
            }
            return true
        }

        if (!menuVisible) return false

        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN) return true

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveSelection(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> adjustCurrentPage(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjustCurrentPage(1)
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> confirmSelection()
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK -> backFromMenu()
            KeyEvent.KEYCODE_BUTTON_X -> deleteSelectionIfAvailable()
            KeyEvent.KEYCODE_BUTTON_Y -> loadSelectionIfAvailable()
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_MENU -> if (event.repeatCount == 0) hideMenu()
            else -> return true
        }
        invalidate()
        return true
    }

    private fun handleEditorHardwareKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (editorPanelOpen) editorIndex = (editorIndex - 1 + VIRTUAL_EDITOR_ITEMS.size) % VIRTUAL_EDITOR_ITEMS.size
                invalidate()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (editorPanelOpen) editorIndex = (editorIndex + 1) % VIRTUAL_EDITOR_ITEMS.size
                invalidate()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (editorPanelOpen) editorIndex = (editorIndex - 1 + VIRTUAL_EDITOR_ITEMS.size) % VIRTUAL_EDITOR_ITEMS.size else resizeSelectedEditable(-1)
                invalidate()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (editorPanelOpen) editorIndex = (editorIndex + 1) % VIRTUAL_EDITOR_ITEMS.size else resizeSelectedEditable(1)
                invalidate()
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (editorPanelOpen) confirmEditorPanelSelection() else editorPanelOpen = true
                invalidate()
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> confirmReturnFromVirtualEditor()
            KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0) editorPanelOpen = !editorPanelOpen
                invalidate()
            }
            else -> Unit
        }
        return true
    }

    fun openMenuFromShortcut() {
        if (!menuVisible) openMenu()
    }

    private fun openMenu() {
        menuVisible = true
        menuPage = MenuPage.MAIN
        menuIndex = 0
        releaseAll()
        activity.pauseRetroForMenu()
        invalidate()
    }

    private fun startVirtualEditor() {
        menuVisible = false
        menuPage = MenuPage.MAIN
        editorMode = true
        editorPanelOpen = false
        editorIndex = 0
        selectedEditId = null
        editingPointerId = null
        releaseAll()
        ensureEditorBall()
        activity.pauseRetroForMenu()
        invalidate()
    }

    private fun ensureEditorBall() {
        if (editorBallRect.width() > 0f && editorBallRect.height() > 0f) return
        val dp = resources.displayMetrics.density
        val size = 52f * dp
        val x = (width.toFloat() - size - 18f * dp).coerceAtLeast(18f * dp)
        val y = (height.toFloat() * 0.30f).coerceAtLeast(18f * dp)
        editorBallRect.set(x, y, x + size, y + size)
    }

    private fun exitVirtualEditor(save: Boolean) {
        if (save) {
            saveLayoutOverrides(width.toFloat(), height.toFloat())
            activity.showToast(tr("emulator.virtual.saved", "Virtual button layout saved"))
        } else {
            customButtons.clear()
            customButtons.addAll(loadCustomButtons())
            layoutOverrides.clear()
            layoutOverrides.putAll(loadLayoutOverrides())
            rebuildLayout(width.toFloat(), height.toFloat())
        }
        editorMode = false
        editorPanelOpen = false
        editorBallPointerId = null
        selectedEditId = null
        editingPointerId = null
        menuVisible = false
        menuPage = MenuPage.MAIN
        releaseAll()
        activity.resumeRetroAfterMenu()
        invalidate()
    }

    private fun confirmReturnFromVirtualEditor() {
        AlertDialog.Builder(activity)
            .setTitle(tr("emulator.virtual.return_title", "Return to Game?"))
            .setMessage(tr("emulator.virtual.return_message", "Discard unsaved virtual button position/size changes and return to the game?"))
            .setNegativeButton(tr("emulator.virtual.no", "No"), null)
            .setPositiveButton(tr("emulator.virtual.yes", "Yes")) { _, _ -> exitVirtualEditor(save = false) }
            .show()
    }

    private fun handleMenuTouch(event: MotionEvent): Boolean {
        if (editorMode) return false
        val x = event.getX(event.actionIndex.coerceAtMost(event.pointerCount - 1))
        val y = event.getY(event.actionIndex.coerceAtMost(event.pointerCount - 1))
        if (event.actionMasked == MotionEvent.ACTION_DOWN && menuIconRect.contains(x, y)) {
            if (menuVisible) hideMenu() else openMenu()
            return true
        }
        if (!menuVisible) return false
        if (menuPage == MenuPage.VIRTUAL_EDITOR && (editingPointerId != null || !menuPanelRect.contains(x, y))) {
            return handleEditorTouch(event)
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (!menuPanelRect.contains(x, y)) {
            hideMenu()
            return true
        }
        if (menuPage == MenuPage.SAVE) {
            when {
                stateSaveButtonRect.contains(x, y) -> {
                    saveSelectedState()
                    invalidate()
                    return true
                }
                stateDeleteButtonRect.contains(x, y) -> {
                    deleteSelectedState()
                    invalidate()
                    return true
                }
                stateLoadButtonRect.contains(x, y) -> {
                    loadSelectedState()
                    invalidate()
                    return true
                }
            }
        }
        if (menuPage == MenuPage.CHEATS) {
            val custom = activity.loadCustomCheats()
            if (cheatAddButtonRect.contains(x, y)) {
                activity.showAddCustomCheatDialog()
                invalidate()
                return true
            }
            if (custom.isNotEmpty() && cheatEnableButtonRect.contains(x, y)) {
                activity.toggleCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                invalidate()
                return true
            }
            if (custom.isNotEmpty() && cheatDeleteButtonRect.contains(x, y)) {
                activity.deleteCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                invalidate()
                return true
            }
        }
        if (menuPage == MenuPage.CUSTOM_CHEATS && customDeleteButtonRect.contains(x, y)) {
            enterPage(MenuPage.CUSTOM_CHEAT_DELETE)
            invalidate()
            return true
        }
        val row = menuRows.firstOrNull { it.rect.contains(x, y) }
        if (row != null) {
            setCurrentIndex(row.index)
            if (menuPage != MenuPage.CHEATS && menuPage != MenuPage.SAVE) confirmSelection()
            invalidate()
        }
        return true
    }

    private fun handleEditorModeTouch(event: MotionEvent): Boolean {
        ensureEditorBall()
        val action = event.actionMasked
        val index = event.actionIndex.coerceAtMost(event.pointerCount - 1)
        val x = event.getX(index)
        val y = event.getY(index)
        val pointerId = event.getPointerId(index)

        if (handleEditorFloatingTouch(event, pointerId, x, y)) return true
        return handleEditorTouch(event)
    }

    private fun handleEditorFloatingTouch(event: MotionEvent, pointerId: Int, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (editorPanelOpen) {
                    if (editorPanelCloseRect.contains(x, y)) {
                        editorPanelOpen = false
                        invalidate()
                        return true
                    }
                    val row = editorPanelRows.firstOrNull { it.rect.contains(x, y) }
                    if (row != null) {
                        editorIndex = row.index
                        confirmEditorPanelSelection()
                        invalidate()
                        return true
                    }
                    if (editorPanelRect.contains(x, y)) return true
                }
                if (editorBallRect.contains(x, y)) {
                    editorBallPointerId = pointerId
                    editorBallMoved = false
                    editorBallLastX = x
                    editorBallLastY = y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val active = editorBallPointerId ?: return false
                val activeIndex = event.findPointerIndex(active)
                if (activeIndex >= 0) {
                    val nx = event.getX(activeIndex)
                    val ny = event.getY(activeIndex)
                    val dx = nx - editorBallLastX
                    val dy = ny - editorBallLastY
                    if (kotlin.math.abs(dx) > 1f || kotlin.math.abs(dy) > 1f) {
                        editorBallMoved = true
                        editorBallRect.offset(dx, dy)
                        clampRect(editorBallRect)
                        editorBallLastX = nx
                        editorBallLastY = ny
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == editorBallPointerId || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (!editorBallMoved && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                        editorPanelOpen = !editorPanelOpen
                    }
                    editorBallPointerId = null
                    editorBallMoved = false
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    private fun confirmEditorPanelSelection() {
        when (editorIndex.coerceIn(0, VIRTUAL_EDITOR_ITEMS.size - 1)) {
            0 -> showAddCustomButtonDialog()
            1 -> resizeSelectedEditable(1)
            2 -> resizeSelectedEditable(-1)
            3 -> exitVirtualEditor(save = true)
            4 -> resetVirtualKeyLayout()
            5 -> confirmReturnFromVirtualEditor()
        }
    }

    private fun controlsAlphaForCurrentMode(): Float = when {
        editorMode -> max(activity.hardwareControlsAlpha, 0.72f)
        menuVisible && menuPage == MenuPage.VIRTUAL_EDITOR -> max(activity.hardwareControlsAlpha, 0.55f)
        menuVisible && (menuPage == MenuPage.VIRTUAL_KEYS || menuPage == MenuPage.VIRTUAL_ALPHA) -> activity.hardwareControlsAlpha
        hardwareMode -> activity.hardwareControlsAlpha
        else -> activity.touchControlsAlpha
    }.coerceIn(0f, 1f)

    private fun currentCount(): Int = when (menuPage) {
        MenuPage.MAIN -> CommonEmulatorUiSpec.mainMenuItems(context).size
        MenuPage.SAVE -> MAX_STATE_SLOTS + 1
        MenuPage.LOAD -> MAX_STATE_SLOTS
        MenuPage.DELETE_SAVE -> MAX_STATE_SLOTS + 1
        MenuPage.VIRTUAL_KEYS -> VIRTUAL_KEY_MENU_ITEMS.size
        MenuPage.VIRTUAL_ALPHA -> 1
        MenuPage.VIRTUAL_EDITOR -> VIRTUAL_EDITOR_ITEMS.size
        MenuPage.CHEATS -> activity.loadCustomCheats().size
        MenuPage.CUSTOM_CHEATS -> activity.loadCustomCheats().size + 1
        MenuPage.CUSTOM_CHEAT_DELETE -> max(1, activity.loadCustomCheats().size)
        MenuPage.RESET_CONFIRM -> 2
    }

    private fun currentIndex(): Int = when (menuPage) {
        MenuPage.MAIN -> menuIndex
        MenuPage.SAVE -> saveSlotIndex
        MenuPage.LOAD -> loadSlotIndex
        MenuPage.DELETE_SAVE -> deleteSlotIndex
        MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex
        MenuPage.VIRTUAL_ALPHA -> 0
        MenuPage.VIRTUAL_EDITOR -> editorIndex
        MenuPage.CHEATS -> cheatIndex
        MenuPage.CUSTOM_CHEATS -> customCheatIndex
        MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex
        MenuPage.RESET_CONFIRM -> menuIndex.coerceIn(0, 1)
    }

    private fun setCurrentIndex(index: Int) {
        val fixed = index.coerceIn(0, max(0, currentCount() - 1))
        when (menuPage) {
            MenuPage.MAIN -> menuIndex = fixed
            MenuPage.SAVE -> saveSlotIndex = fixed
            MenuPage.LOAD -> loadSlotIndex = fixed
            MenuPage.DELETE_SAVE -> deleteSlotIndex = fixed
            MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex = fixed
            MenuPage.VIRTUAL_ALPHA -> Unit
            MenuPage.VIRTUAL_EDITOR -> editorIndex = fixed
            MenuPage.CHEATS -> cheatIndex = fixed
            MenuPage.CUSTOM_CHEATS -> customCheatIndex = fixed
            MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex = fixed
            MenuPage.RESET_CONFIRM -> menuIndex = fixed
        }
    }

    private fun moveSelection(delta: Int) {
        val count = currentCount()
        if (count <= 0) return
        setCurrentIndex((currentIndex() + delta + count) % count)
    }

    private fun adjustCurrentPage(delta: Int) {
        when (menuPage) {
            MenuPage.VIRTUAL_ALPHA -> {
                activity.hardwareControlsAlpha = (activity.hardwareControlsAlpha + delta * 0.05f).coerceIn(0f, 1f)
                activity.showToast(tr("emulator.virtual.alpha_title", "Controller Virtual Button Opacity: {percent}%", "percent" to (activity.hardwareControlsAlpha * 100).roundToInt()))
            }
            MenuPage.VIRTUAL_EDITOR -> resizeSelectedEditable(delta)
            MenuPage.VIRTUAL_KEYS,
            MenuPage.SAVE,
            MenuPage.LOAD,
            MenuPage.DELETE_SAVE,
            MenuPage.CHEATS,
            MenuPage.CUSTOM_CHEATS,
            MenuPage.CUSTOM_CHEAT_DELETE,
            MenuPage.RESET_CONFIRM,
            MenuPage.MAIN -> moveSelection(delta)
        }
    }

    private fun confirmSelection() {
        when (menuPage) {
            MenuPage.MAIN -> when (menuIndex) {
                0 -> enterPage(MenuPage.SAVE)
                1 -> enterPage(MenuPage.VIRTUAL_KEYS)
                2 -> enterPage(MenuPage.CHEATS)
                3 -> enterPage(MenuPage.RESET_CONFIRM)
                4 -> activity.softRestartGameNoExit()
                5 -> activity.exitGame()
            }
            MenuPage.SAVE -> saveSelectedState()
            MenuPage.LOAD -> {
                val file = activity.gameStorage?.slotStateFile(loadSlotIndex + 1)
                if (file != null && file.exists() && file.length() > 0L) {
                    activity.loadSlotState(loadSlotIndex + 1, hideMenuAfterLoad = true)
                } else {
                    activity.showToast(tr("emulator.state.slot_empty", "Save {slot} is empty", "slot" to (loadSlotIndex + 1)))
                }
            }
            MenuPage.DELETE_SAVE -> deleteLegacyDeleteState()
            MenuPage.VIRTUAL_KEYS -> when (virtualSettingsIndex) {
                0 -> enterPage(MenuPage.VIRTUAL_ALPHA)
                1 -> startVirtualEditor()
            }
            MenuPage.VIRTUAL_ALPHA -> Unit
            MenuPage.VIRTUAL_EDITOR -> when (editorIndex) {
                0 -> showAddCustomButtonDialog()
                1 -> resizeSelectedEditable(1)
                2 -> resizeSelectedEditable(-1)
                3 -> { saveLayoutOverrides(width.toFloat(), height.toFloat()); activity.showToast(tr("emulator.virtual.saved", "Virtual button layout saved")) }
                4 -> resetVirtualKeyLayout()
                5 -> confirmReturnFromVirtualEditor()
            }
            MenuPage.CHEATS -> {
                val custom = activity.loadCustomCheats()
                if (custom.isEmpty()) {
                    activity.showToast(tr("emulator.cheat.add_first", "Add a custom cheat in the top-right first"))
                } else {
                    activity.toggleCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
                }
            }
            MenuPage.CUSTOM_CHEATS -> {
                when (customCheatIndex) {
                    0 -> activity.showAddCustomCheatDialog()
                    else -> activity.toggleCustomCheat(customCheatIndex - 1)
                }
            }
            MenuPage.CUSTOM_CHEAT_DELETE -> {
                val custom = activity.loadCustomCheats()
                if (custom.isEmpty()) {
                    activity.showToast(tr("emulator.cheat.none", "No custom cheats"))
                } else {
                    activity.deleteCustomCheat(deleteCustomCheatIndex.coerceIn(0, custom.lastIndex))
                }
            }
            MenuPage.RESET_CONFIRM -> {
                if (menuIndex == 0) activity.restartGameFresh() else backFromMenu()
            }
        }
    }

    private fun enterPage(page: MenuPage) {
        menuPage = page
        menuIndex = 0
        when (page) {
            MenuPage.SAVE -> saveSlotIndex = 0
            MenuPage.LOAD -> loadSlotIndex = 0
            MenuPage.DELETE_SAVE -> deleteSlotIndex = 0
            MenuPage.CHEATS -> cheatIndex = 0
            MenuPage.CUSTOM_CHEATS -> customCheatIndex = 0
            MenuPage.CUSTOM_CHEAT_DELETE -> deleteCustomCheatIndex = 0
            MenuPage.VIRTUAL_KEYS -> virtualSettingsIndex = 0
            MenuPage.VIRTUAL_EDITOR -> editorIndex = 0
            else -> Unit
        }
        invalidate()
    }

    private fun backFromMenu() {
        when (menuPage) {
            MenuPage.MAIN -> hideMenu()
            MenuPage.VIRTUAL_ALPHA, MenuPage.VIRTUAL_EDITOR -> {
                menuPage = MenuPage.VIRTUAL_KEYS
                virtualSettingsIndex = 0
                invalidate()
            }
            MenuPage.CUSTOM_CHEAT_DELETE -> {
                menuPage = MenuPage.CUSTOM_CHEATS
                customCheatIndex = 0
                invalidate()
            }
            MenuPage.CUSTOM_CHEATS -> {
                menuPage = MenuPage.CHEATS
                cheatIndex = 0
                invalidate()
            }
            else -> {
                menuPage = MenuPage.MAIN
                menuIndex = 0
                invalidate()
            }
        }
    }

    private fun deleteSelectionIfAvailable() {
        when (menuPage) {
            MenuPage.SAVE -> deleteSelectedState()
            MenuPage.DELETE_SAVE -> deleteLegacyDeleteState()
            MenuPage.CHEATS -> {
                val custom = activity.loadCustomCheats()
                if (custom.isNotEmpty()) activity.deleteCustomCheat(cheatIndex.coerceIn(0, custom.lastIndex))
            }
            MenuPage.CUSTOM_CHEAT_DELETE -> {
                val custom = activity.loadCustomCheats()
                if (custom.isNotEmpty()) activity.deleteCustomCheat(deleteCustomCheatIndex.coerceIn(0, custom.lastIndex))
            }
            else -> Unit
        }
    }

    private fun saveSelectedState() {
        if (saveSlotIndex >= MAX_STATE_SLOTS) {
            activity.saveQuickState()
        } else {
            activity.saveSlotState(saveSlotIndex + 1)
        }
    }

    private fun loadSelectedState() {
        if (saveSlotIndex >= MAX_STATE_SLOTS) {
            activity.loadQuickState(hideMenuAfterLoad = true)
            return
        }
        val slot = saveSlotIndex + 1
        val file = activity.gameStorage?.slotStateFile(slot)
        if (file != null && file.exists() && file.length() > 0L) {
            activity.loadSlotState(slot, hideMenuAfterLoad = true)
        } else {
            activity.showToast(tr("emulator.state.slot_empty", "Save {slot} is empty", "slot" to slot))
        }
    }

    private fun deleteSelectedState() {
        if (saveSlotIndex >= MAX_STATE_SLOTS) {
            activity.deleteQuickState()
        } else {
            activity.deleteSlotState(saveSlotIndex + 1)
        }
    }

    private fun deleteLegacyDeleteState() {
        if (deleteSlotIndex == 0) {
            activity.deleteQuickState()
        } else {
            activity.deleteSlotState(deleteSlotIndex)
        }
    }

    private fun loadSelectionIfAvailable() {
        when (menuPage) {
            MenuPage.SAVE -> loadSelectedState()
            MenuPage.LOAD -> {
                val slot = loadSlotIndex + 1
                val file = activity.gameStorage?.slotStateFile(slot)
                if (file != null && file.exists() && file.length() > 0L) {
                    activity.loadSlotState(slot, hideMenuAfterLoad = true)
                } else {
                    activity.showToast(tr("emulator.state.slot_empty", "Save {slot} is empty", "slot" to slot))
                }
            }
            MenuPage.CHEATS -> {
                activity.showToast(tr("emulator.cheat.reload_cancelled", "Reload was cancelled; A toggles instantly, native engine is still in development"))
            }
            else -> Unit
        }
    }

    private fun updatePointer(event: MotionEvent, index: Int) {
        val pointerId = event.getPointerId(index)
        pointerKeys[pointerId] = keysFor(event.getX(index), event.getY(index))
        syncPressedKeys()
    }

    private fun releasePointer(event: MotionEvent, index: Int, cancelled: Boolean) {
        val pointerId = event.getPointerId(index)
        val oldKeys = pointerKeys.remove(pointerId).orEmpty()
        if (!cancelled) {
            when {
                oldKeys.contains(VIRTUAL_MENU) -> openMenu()
                oldKeys.contains(VIRTUAL_EXIT) -> activity.exitGame()
                oldKeys.contains(VIRTUAL_FAST_FORWARD) -> activity.cycleFastForwardSpeed()
                oldKeys.contains(VIRTUAL_QUICK_SAVE) -> activity.saveQuickState()
                oldKeys.contains(VIRTUAL_QUICK_LOAD) -> activity.loadQuickState(hideMenuAfterLoad = false)
            }
        }
        syncPressedKeys()
    }

    private fun syncPressedKeys() {
        val flattened = pointerKeys.values.asSequence().flatten().toList()

        val wantsTurboA = flattened.contains(VIRTUAL_TURBO_A)
        val wantsTurboB = flattened.contains(VIRTUAL_TURBO_B)
        if (wantsTurboA) activity.startTurbo(VIRTUAL_TURBO_A, KeyEvent.KEYCODE_BUTTON_A) else activity.stopTurbo(VIRTUAL_TURBO_A)
        if (wantsTurboB) activity.startTurbo(VIRTUAL_TURBO_B, KeyEvent.KEYCODE_BUTTON_B) else activity.stopTurbo(VIRTUAL_TURBO_B)

        val wantsVirtualExit = flattened.contains(KeyEvent.KEYCODE_BUTTON_SELECT) && flattened.contains(VIRTUAL_TURBO_A)
        if (wantsVirtualExit) {
            activity.releaseAllGameInputs()
            pointerKeys.clear()
            activity.exitGame()
            return
        }

        val nextPressed = flattened.asSequence().filter { it >= 0 }.toSet()
        val toRelease = pressedKeys - nextPressed
        val toPress = nextPressed - pressedKeys
        toRelease.forEach { key -> activity.sendVirtualKey(KeyEvent.ACTION_UP, key) }
        toPress.forEach { key -> activity.sendVirtualKey(KeyEvent.ACTION_DOWN, key) }
        pressedKeys.clear()
        pressedKeys.addAll(nextPressed)
        invalidate()
    }

    private fun keysFor(x: Float, y: Float): Set<Int> {
        val hitButtons = buttons.filter { button ->
            if (button.circle) {
                val dx = x - button.rect.centerX()
                val dy = y - button.rect.centerY()
                dx * dx + dy * dy <= button.rect.width() * button.rect.width() / 4f
            } else button.rect.contains(x, y)
        }
        if (hitButtons.isNotEmpty()) return hitButtons.flatMap { it.keyCodes }.toSet()

        val aButton = buttons.firstOrNull { it.keyCode == KeyEvent.KEYCODE_BUTTON_A }
        val bButton = buttons.firstOrNull { it.keyCode == KeyEvent.KEYCODE_BUTTON_B }
        if (aButton != null && bButton != null) {
            val centerX = (aButton.rect.centerX() + bButton.rect.centerX()) / 2f
            val centerY = (aButton.rect.centerY() + bButton.rect.centerY()) / 2f
            val dx = x - centerX
            val dy = y - centerY
            val compositeRadius = aButton.rect.width() * 0.62f
            if (dx * dx + dy * dy <= compositeRadius * compositeRadius) {
                return setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
            }
        }

        if (rightStickOuter.contains(x, y)) {
            return directionalKeys(x, y, rightStickCenter, rightStickOuter.width() * 0.12f)
        }

        if (dpadOuter.contains(x, y)) {
            return directionalKeys(x, y, dpadCenter, dpadDeadZone)
        }

        if (leftStickOuter.contains(x, y)) {
            return directionalKeys(x, y, leftStickCenter, leftStickOuter.width() * 0.12f)
        }
        return emptySet()
    }

    private fun directionalKeys(x: Float, y: Float, center: RectF, deadZone: Float): Set<Int> {
        val keys = mutableSetOf<Int>()
        val dx = x - center.centerX()
        val dy = y - center.centerY()
        if (dx < -deadZone) keys += KeyEvent.KEYCODE_DPAD_LEFT
        if (dx > deadZone) keys += KeyEvent.KEYCODE_DPAD_RIGHT
        if (dy < -deadZone) keys += KeyEvent.KEYCODE_DPAD_UP
        if (dy > deadZone) keys += KeyEvent.KEYCODE_DPAD_DOWN
        return keys
    }

    private fun loadCustomButtons(): List<CustomTouchButton> {
        val raw = activity.settingsPrefs.getString(PREF_CUSTOM_TOUCH_BUTTONS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val keys = parseTouchMethod(parts[3])
            if (keys.isEmpty()) return@mapNotNull null
            CustomTouchButton(parts[0], parts[1], parts[2] == "circle", keys)
        }.toList()
    }

    private fun saveCustomButtons() {
        val raw = customButtons.joinToString("\n") { item ->
            val style = if (item.circle) "circle" else "pill"
            val method = item.keyCodes.joinToString("+")
            listOf(item.id, item.label, style, method).joinToString("|")
        }
        activity.settingsPrefs.edit().putString(PREF_CUSTOM_TOUCH_BUTTONS, raw).apply()
    }

    private fun loadLayoutOverrides(): MutableMap<String, RectF> {
        val result = mutableMapOf<String, RectF>()
        val raw = activity.settingsPrefs.getString(PREF_TOUCH_LAYOUT_RECTS, "").orEmpty()
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

    private fun saveLayoutOverrides(viewW: Float, viewH: Float) {
        if (viewW <= 0f || viewH <= 0f) return
        saveCustomButtons()
        fun norm(rect: RectF): RectF = RectF(rect.left / viewW, rect.top / viewH, rect.right / viewW, rect.bottom / viewH)
        layoutOverrides["dpad"] = norm(dpadOuter)
        layoutOverrides["left_stick"] = norm(leftStickOuter)
        layoutOverrides["right_stick"] = norm(rightStickOuter)
        layoutOverrides["menu_icon"] = norm(menuIconRect)
        buttons.forEach { button -> layoutOverrides[button.id] = norm(button.rect) }
        val raw = layoutOverrides.entries.joinToString("\n") { (id, rect) ->
            "$id|${rect.left}|${rect.top}|${rect.right}|${rect.bottom}"
        }
        activity.settingsPrefs.edit().putString(PREF_TOUCH_LAYOUT_RECTS, raw).apply()
    }

    private fun applyLayoutOverrides(viewW: Float, viewH: Float) {
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
        buttons.forEach { applyToRect(it.id, it.rect) }
    }

    private fun rebuildDpadRectsFromOuter() {
        val cellW = dpadOuter.width() / 3f
        val cellH = dpadOuter.height() / 3f
        val cx = dpadOuter.centerX()
        val cy = dpadOuter.centerY()
        dpadCenter.set(cx - cellW / 2f, cy - cellH / 2f, cx + cellW / 2f, cy + cellH / 2f)
        dpadDeadZone = min(cellW, cellH) * 0.26f
        dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(cx - cellW / 2f, dpadOuter.top, cx + cellW / 2f, dpadOuter.top + cellH)
        dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(cx - cellW / 2f, dpadOuter.bottom - cellH, cx + cellW / 2f, dpadOuter.bottom)
        dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(dpadOuter.left, cy - cellH / 2f, dpadOuter.left + cellW, cy + cellH / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(dpadOuter.right - cellW, cy - cellH / 2f, dpadOuter.right, cy + cellH / 2f)
    }

    private fun rebuildStickCenter(outer: RectF, center: RectF) {
        val r = min(outer.width(), outer.height()) * 0.35f
        center.set(outer.centerX() - r / 2f, outer.centerY() - r / 2f, outer.centerX() + r / 2f, outer.centerY() + r / 2f)
    }

    private fun findEditableIdAt(x: Float, y: Float): String? {
        if (menuIconRect.contains(x, y)) return "menu_icon"
        buttons.asReversed().firstOrNull { button ->
            if (button.circle) {
                val dx = x - button.rect.centerX()
                val dy = y - button.rect.centerY()
                dx * dx + dy * dy <= button.rect.width() * button.rect.width() / 4f
            } else button.rect.contains(x, y)
         }?.let { return it.id }
        if (rightStickOuter.contains(x, y)) return "right_stick"
        if (leftStickOuter.contains(x, y)) return "left_stick"
        if (dpadOuter.contains(x, y)) return "dpad"
        return null
    }

    private fun editableRect(id: String?): RectF? = when (id) {
        "dpad" -> dpadOuter
        "left_stick" -> leftStickOuter
        "right_stick" -> rightStickOuter
        "menu_icon" -> menuIconRect
        null -> null
        else -> buttons.firstOrNull { it.id == id }?.rect
    }

    private fun moveSelectedEditable(dx: Float, dy: Float) {
        val id = selectedEditId ?: return
        val rect = editableRect(id) ?: return
        rect.offset(dx, dy)
        clampRect(rect)
        if (id == "dpad") rebuildDpadRectsFromOuter()
        if (id == "left_stick") rebuildStickCenter(leftStickOuter, leftStickCenter)
        if (id == "right_stick") rebuildStickCenter(rightStickOuter, rightStickCenter)
        invalidate()
    }

    private fun resizeSelectedEditable(delta: Int) {
        val id = selectedEditId ?: return activity.showToast(tr("emulator.virtual.select_first", "Select a virtual button on the screen first"))
        val rect = editableRect(id) ?: return
        val scale = if (delta > 0) 1.08f else 0.92f
        val cx = rect.centerX()
        val cy = rect.centerY()
        val minSize = 28f * resources.displayMetrics.density
        // Do not cap the maximum size here. Some handheld layouts need much larger
        // touch targets, so only enforce a minimum size and let the user resize freely.
        val w = max(minSize, rect.width() * scale)
        val h = max(minSize, rect.height() * scale)
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        clampRect(rect)
        if (id == "dpad") rebuildDpadRectsFromOuter()
        if (id == "left_stick") rebuildStickCenter(leftStickOuter, leftStickCenter)
        if (id == "right_stick") rebuildStickCenter(rightStickOuter, rightStickCenter)
        invalidate()
    }

    private fun clampRect(rect: RectF) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        if (rect.left < 0f) rect.offset(-rect.left, 0f)
        if (rect.top < 0f) rect.offset(0f, -rect.top)
        if (rect.right > w) rect.offset(w - rect.right, 0f)
        if (rect.bottom > h) rect.offset(0f, h - rect.bottom)
    }

    private fun handleEditorTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex.coerceAtMost(event.pointerCount - 1)
        val pointerId = event.getPointerId(index)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(index)
                val y = event.getY(index)
                selectedEditId = findEditableIdAt(x, y)
                editingPointerId = pointerId
                lastEditX = x
                lastEditY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val active = editingPointerId ?: return true
                val activeIndex = event.findPointerIndex(active)
                if (activeIndex >= 0) {
                    val x = event.getX(activeIndex)
                    val y = event.getY(activeIndex)
                    moveSelectedEditable(x - lastEditX, y - lastEditY)
                    lastEditX = x
                    lastEditY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == editingPointerId || action == MotionEvent.ACTION_CANCEL) {
                    editingPointerId = null
                }
            }
        }
        return true
    }

    private fun drawEditSelection(canvas: Canvas) {
        val alpha = controlsAlphaForCurrentMode().coerceAtLeast(0.35f)
        val selected = selectedEditId
        fun drawRect(id: String, rect: RectF, circle: Boolean = false) {
            val isSelected = id == selected
            strokePaint.strokeWidth = if (isSelected) 3.5f * resources.displayMetrics.density else 1.5f * resources.displayMetrics.density
            strokePaint.color = if (isSelected) Color.argb((240 * alpha).roundToInt(), 255, 214, 90) else Color.argb((110 * alpha).roundToInt(), 255, 255, 255)
            if (circle) canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, strokePaint)
            else canvas.drawRoundRect(rect, min(rect.width(), rect.height()) / 6f, min(rect.width(), rect.height()) / 6f, strokePaint)
        }
        drawRect("dpad", dpadOuter)
        drawRect("left_stick", leftStickOuter, circle = true)
        drawRect("right_stick", rightStickOuter, circle = true)
        drawRect("menu_icon", menuIconRect)
        buttons.forEach { drawRect(it.id, it.rect, it.circle) }
        strokePaint.strokeWidth = 2.5f * resources.displayMetrics.density
    }

    private fun resetVirtualKeyLayout() {
        layoutOverrides.clear()
        if (!editorMode) activity.settingsPrefs.edit().remove(PREF_TOUCH_LAYOUT_RECTS).apply()
        selectedEditId = null
        rebuildLayout(width.toFloat(), height.toFloat())
        activity.showToast(tr("emulator.virtual.reset_done", "Default virtual button layout restored"))
        invalidate()
    }

    private fun showAddCustomButtonDialog() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20f * dp).roundToInt(), (8f * dp).roundToInt(), (20f * dp).roundToInt(), 0)
        }
        val nameInput = EditText(activity).apply {
            hint = tr("emulator.virtual.add_name_hint", "Button label, for example: A+B")
            setSingleLine(true)
        }
        val styleInput = EditText(activity).apply {
            hint = tr("emulator.virtual.add_style_hint", "Style: circle or pill")
            setText("circle")
            setSingleLine(true)
        }
        val methodInput = EditText(activity).apply {
            hint = tr("emulator.virtual.add_method_hint", "Method: A+B / SELECT+X / Save / Load / Fast / Exit")
            setSingleLine(true)
        }
        layout.addView(nameInput)
        layout.addView(styleInput)
        layout.addView(methodInput)
        AlertDialog.Builder(activity)
            .setTitle(tr("emulator.virtual.add_title", "Add Custom Virtual Button"))
            .setMessage(tr("emulator.virtual.add_message", "After adding, the button appears in the center. Drag it in the editor and use left/right to resize."))
            .setView(layout)
            .setNegativeButton(tr("common.cancel", "Cancel"), null)
            .setPositiveButton(tr("common.add", "Add")) { _, _ ->
                val label = nameInput.text?.toString()?.trim().orEmpty().ifBlank { tr("emulator.virtual.custom_default", "Custom") }
                val style = styleInput.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val keys = parseTouchMethod(methodInput.text?.toString().orEmpty())
                if (keys.isEmpty()) {
                    activity.showToast(tr("emulator.virtual.method_unknown", "Method not recognized"))
                } else {
                    val id = "custom_${System.currentTimeMillis()}"
                    val isCircle = style.contains("circle") || style.contains("圆")
                    customButtons += CustomTouchButton(id, label.take(8), isCircle, keys)
                    val w = if (isCircle) 56f * resources.displayMetrics.density else 82f * resources.displayMetrics.density
                    val h = if (isCircle) 56f * resources.displayMetrics.density else 42f * resources.displayMetrics.density
                    buttons += TouchButton(id, label.take(8), keys, RectF(width / 2f - w / 2f, height / 2f - h / 2f, width / 2f + w / 2f, height / 2f + h / 2f), isCircle)
                    selectedEditId = id
                    activity.showToast(tr("emulator.virtual.added", "Added custom button: {name}", "name" to label))
                    invalidate()
                }
            }
            .show()
    }

    private fun parseTouchMethod(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        return raw.replace("＋", "+").replace(",", "+").split("+").mapNotNull { tokenRaw ->
            tokenRaw.trim().toIntOrNull() ?: when (tokenRaw.trim().uppercase(Locale.ROOT)) {
                "A", "确认" -> KeyEvent.KEYCODE_BUTTON_A
                "B", "取消" -> KeyEvent.KEYCODE_BUTTON_B
                "X", "连发A", "连续确认" -> VIRTUAL_TURBO_A
                "Y", "连发B", "连续取消" -> VIRTUAL_TURBO_B
                "L", "L1" -> KeyEvent.KEYCODE_BUTTON_L1
                "R", "R1" -> KeyEvent.KEYCODE_BUTTON_R1
                "START", "开始" -> KeyEvent.KEYCODE_BUTTON_START
                "SELECT", "选择" -> KeyEvent.KEYCODE_BUTTON_SELECT
                "快存", "快捷保存", "SAVE" -> VIRTUAL_QUICK_SAVE
                "快读", "快捷读取", "LOAD" -> VIRTUAL_QUICK_LOAD
                "快进", "FF", "FAST" -> VIRTUAL_FAST_FORWARD
                "菜单", "MENU" -> VIRTUAL_MENU
                "退出", "EXIT" -> VIRTUAL_EXIT
                else -> null
            }
        }.toSet()
    }


    private fun rebuildLayout(width: Float, height: Float) {
        buttons.clear()
        dpadRects.clear()
        menuRows.clear()
        val density = resources.displayMetrics.density
        fun dp(value: Float) = value * density
        fun pillButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, w: Float, h: Float) {
            buttons += TouchButton(id, label, setOf(keyCode), RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f))
        }

        val margin = max(dp(14f), min(width, height) * 0.035f)
        val primarySize = min(dp(126f), height * 0.29f).coerceAtLeast(min(dp(92f), height * 0.24f))
        val cell = primarySize / 3f
        val clusterCy = height - margin - primarySize / 2f
        val leftCx = margin + primarySize / 2f
        val rightCx = width - margin - primarySize / 2f

        val left = leftCx - primarySize / 2f
        val top = clusterCy - primarySize / 2f
        dpadDeadZone = cell * 0.26f
        dpadOuter.set(left, top, left + primarySize, top + primarySize)
        dpadCenter.set(leftCx - cell / 2f, clusterCy - cell / 2f, leftCx + cell / 2f, clusterCy + cell / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_UP] = RectF(leftCx - cell / 2f, top, leftCx + cell / 2f, top + cell)
        dpadRects[KeyEvent.KEYCODE_DPAD_DOWN] = RectF(leftCx - cell / 2f, top + cell * 2f, leftCx + cell / 2f, top + primarySize)
        dpadRects[KeyEvent.KEYCODE_DPAD_LEFT] = RectF(left, clusterCy - cell / 2f, left + cell, clusterCy + cell / 2f)
        dpadRects[KeyEvent.KEYCODE_DPAD_RIGHT] = RectF(left + cell * 2f, clusterCy - cell / 2f, left + primarySize, clusterCy + cell / 2f)

        val smallPillH = min(dp(32f), height * 0.065f).coerceAtLeast(dp(26f))
        val shoulderW = min(dp(108f), width * 0.15f)
        val shoulderY = margin + smallPillH / 2f
        pillButton("l1", "L", KeyEvent.KEYCODE_BUTTON_L1, margin + shoulderW / 2f, shoulderY, shoulderW, smallPillH)
        pillButton("r1", "R", KeyEvent.KEYCODE_BUTTON_R1, width - margin - shoulderW / 2f, shoulderY, shoulderW, smallPillH)

        val stickRadius = min(primarySize * 0.36f, dp(48f))
        val leftStickCx = leftCx + primarySize * 1.08f
        leftStickOuter.set(leftStickCx - stickRadius, clusterCy - stickRadius, leftStickCx + stickRadius, clusterCy + stickRadius)
        leftStickCenter.set(leftStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, leftStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)
        val rightStickCx = rightCx - primarySize * 1.12f
        rightStickOuter.set(rightStickCx - stickRadius, clusterCy - stickRadius, rightStickCx + stickRadius, clusterCy + stickRadius)
        rightStickCenter.set(rightStickCx - stickRadius * 0.35f, clusterCy - stickRadius * 0.35f, rightStickCx + stickRadius * 0.35f, clusterCy + stickRadius * 0.35f)

        val faceRadius = min(dp(30f), primarySize * 0.25f)
        buttons += circularButton("b", "B", KeyEvent.KEYCODE_BUTTON_B, rightCx - primarySize * 0.32f, clusterCy + primarySize * 0.18f, faceRadius)
        buttons += circularButton("a", "A", KeyEvent.KEYCODE_BUTTON_A, rightCx + primarySize * 0.26f, clusterCy - primarySize * 0.20f, faceRadius)
        buttons += circularButton("y", "Y", VIRTUAL_TURBO_B, rightCx - primarySize * 0.34f, clusterCy - primarySize * 0.36f, faceRadius * 0.88f)
        buttons += circularButton("x", "X", VIRTUAL_TURBO_A, rightCx + primarySize * 0.26f, clusterCy + primarySize * 0.36f, faceRadius * 0.88f)

        val bottomPillY = height - margin - smallPillH / 2f
        pillButton("start", "START", KeyEvent.KEYCODE_BUTTON_START, width / 2f + dp(62f), bottomPillY, dp(88f), smallPillH)
        pillButton("select", "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, width / 2f - dp(62f), bottomPillY, dp(92f), smallPillH)
        pillButton("quick_save", trShort("emulator.short.quick_save", "Save", 4), VIRTUAL_QUICK_SAVE, margin + shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
        pillButton("quick_load", trShort("emulator.short.quick_load", "Load", 4), VIRTUAL_QUICK_LOAD, width - margin - shoulderW / 2f, shoulderY + smallPillH + dp(8f), dp(76f), smallPillH)
        pillButton("fast_forward", trShort("emulator.short.fast_forward", "Fast", 4), VIRTUAL_FAST_FORWARD, width - margin - shoulderW / 2f, shoulderY + (smallPillH + dp(8f)) * 2f, dp(76f), smallPillH)
        pillButton("exit", trShort("emulator.short.exit", "Exit", 4), VIRTUAL_EXIT, width / 2f, shoulderY, dp(86f), smallPillH)
        customButtons.forEach { custom ->
            val w = if (custom.circle) dp(56f) else dp(82f)
            val h = if (custom.circle) dp(56f) else smallPillH
            buttons += TouchButton(custom.id, custom.label, custom.keyCodes, RectF(width / 2f - w / 2f, height / 2f - h / 2f, width / 2f + w / 2f, height / 2f + h / 2f), custom.circle)
        }
        val menuSize = dp(42f)
        val menuTop = margin + smallPillH * 2f + dp(20f)
        menuIconRect.set(margin, menuTop, margin + menuSize, menuTop + menuSize)
        applyLayoutOverrides(width, height)

        val panelW = min(dp(560f), width - margin * 2f)
        val panelH = min(dp(420f), height - margin * 1.2f)
        menuPanelRect.set((width - panelW) / 2f, (height - panelH) / 2f, (width + panelW) / 2f, (height + panelH) / 2f)

        largeTextSize = dp(18f)
        smallTextSize = dp(12f)
    }

    private fun circularButton(id: String, label: String, keyCode: Int, cx: Float, cy: Float, radius: Float): TouchButton =
        TouchButton(id, label, setOf(keyCode), RectF(cx - radius, cy - radius, cx + radius, cy + radius), circle = true)

    private fun drawDpad(canvas: Canvas, alpha: Float) {
        strokePaint.color = alphaColor(150, alpha)
        val corner = dpadOuter.width() / 10f
        dpadRects.values.forEach { rect ->
            fillPaint.color = alphaColor(40, alpha)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
        }
        fillPaint.color = alphaColor(28, alpha)
        canvas.drawRoundRect(dpadCenter, corner, corner, fillPaint)
        canvas.drawRoundRect(dpadCenter, corner, corner, strokePaint)
        textPaint.textSize = largeTextSize
        drawCenteredText(canvas, "▲", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_UP), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP), alpha)
        drawCenteredText(canvas, "▼", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_DOWN), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN), alpha)
        drawCenteredText(canvas, "◀", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_LEFT), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT), alpha)
        drawCenteredText(canvas, "▶", dpadRects.getValue(KeyEvent.KEYCODE_DPAD_RIGHT), pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT), alpha)
    }

    private fun drawAnalogStick(canvas: Canvas, outer: RectF, center: RectF, label: String, alpha: Float, active: Boolean = false) {
        if (outer.width() <= 0f || outer.height() <= 0f) return
        fillPaint.color = alphaColor(if (active) 90 else 34, alpha)
        strokePaint.color = alphaColor(if (active) 230 else 135, alpha)
        canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, fillPaint)
        canvas.drawCircle(outer.centerX(), outer.centerY(), outer.width() / 2f, strokePaint)
        fillPaint.color = alphaColor(if (active) 120 else 48, alpha)
        canvas.drawCircle(center.centerX(), center.centerY(), center.width() / 2f, fillPaint)
        textPaint.textSize = smallTextSize
        drawCenteredText(canvas, label, outer, active, alpha)
    }

    private fun drawButton(canvas: Canvas, button: TouchButton, pressed: Boolean, alpha: Float) {
        fillPaint.color = alphaColor(if (pressed) 115 else 42, alpha)
        strokePaint.color = alphaColor(if (pressed) 230 else 150, alpha)
        if (button.circle) {
            canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, fillPaint)
            canvas.drawCircle(button.rect.centerX(), button.rect.centerY(), button.rect.width() / 2f, strokePaint)
        } else {
            val radius = button.rect.height() / 2f
            canvas.drawRoundRect(button.rect, radius, radius, fillPaint)
            canvas.drawRoundRect(button.rect, radius, radius, strokePaint)
        }
        textPaint.textSize = if (button.label.length > 2) smallTextSize else largeTextSize
        drawCenteredText(canvas, button.label, button.rect, pressed, alpha)
    }

    private fun drawMenuIcon(canvas: Canvas, alpha: Float) {
        fillPaint.color = alphaColor(50, alpha)
        strokePaint.color = alphaColor(170, alpha)
        val radius = menuIconRect.height() / 2f
        canvas.drawRoundRect(menuIconRect, radius, radius, fillPaint)
        canvas.drawRoundRect(menuIconRect, radius, radius, strokePaint)
        textPaint.textSize = largeTextSize
        textPaint.color = alphaColor(255, alpha)
        val y = menuIconRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("☰", menuIconRect.centerX(), y, textPaint)
    }

    private fun drawEditorFloatingUi(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        ensureEditorBall()

        fillPaint.color = Color.argb(190, 20, 24, 32)
        strokePaint.color = Color.argb(220, 255, 255, 255)
        strokePaint.strokeWidth = 2f * dp
        canvas.drawCircle(editorBallRect.centerX(), editorBallRect.centerY(), editorBallRect.width() / 2f, fillPaint)
        canvas.drawCircle(editorBallRect.centerX(), editorBallRect.centerY(), editorBallRect.width() / 2f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 14f * dp
        textPaint.color = Color.WHITE
        val ballY = editorBallRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(trShort("emulator.virtual.editor_fab", "Edit", 4), editorBallRect.centerX(), ballY, textPaint)

        if (!editorPanelOpen) return

        val panelW = min(360f * dp, width.toFloat() - 28f * dp)
        val desiredPanelH = 96f * dp + virtualEditorItems().size * 37f * dp + 18f * dp
        val panelH = min(desiredPanelH, height.toFloat() - 28f * dp)
        var left = editorBallRect.left - panelW - 10f * dp
        if (left < 14f * dp) left = editorBallRect.right + 10f * dp
        if (left + panelW > width - 14f * dp) left = width - 14f * dp - panelW
        var top = editorBallRect.top
        if (top + panelH > height - 14f * dp) top = height - 14f * dp - panelH
        if (top < 14f * dp) top = 14f * dp
        editorPanelRect.set(left, top, left + panelW, top + panelH)
        editorPanelCloseRect.set(editorPanelRect.right - 42f * dp, editorPanelRect.top + 8f * dp, editorPanelRect.right - 10f * dp, editorPanelRect.top + 40f * dp)

        fillPaint.color = Color.argb(235, 18, 22, 30)
        strokePaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawRoundRect(editorPanelRect, 18f * dp, 18f * dp, fillPaint)
        canvas.drawRoundRect(editorPanelRect, 18f * dp, 18f * dp, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 16f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(tr("emulator.virtual.editor_title", "Virtual Button Editor"), editorPanelRect.left + 16f * dp, editorPanelRect.top + 32f * dp, textPaint)

        fillPaint.color = Color.argb(70, 255, 255, 255)
        strokePaint.color = Color.argb(165, 255, 255, 255)
        canvas.drawRoundRect(editorPanelCloseRect, 12f * dp, 12f * dp, fillPaint)
        canvas.drawRoundRect(editorPanelCloseRect, 12f * dp, 12f * dp, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15f * dp
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("×", editorPanelCloseRect.centerX(), editorPanelCloseRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10.5f * dp
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.argb(210, 255, 255, 255)
        val selectedName = selectedEditId ?: tr("emulator.virtual.not_selected", "Not Selected")
        val selectedRect = editableRect(selectedEditId)
        val selectedSizeText = if (selectedRect != null) " · ${selectedRect.width().roundToInt()}×${selectedRect.height().roundToInt()}" else ""
        canvas.drawText(tr("emulator.virtual.editor_status", "Drag any virtual button. Current: {name}{size}", "name" to selectedName, "size" to selectedSizeText), editorPanelRect.left + 16f * dp, editorPanelRect.top + 54f * dp, textPaint)
        canvas.drawText(tr("emulator.virtual.editor_tip", "Tap increase/decrease, or use left/right on a controller; tap × to collapse this panel."), editorPanelRect.left + 16f * dp, editorPanelRect.top + 72f * dp, textPaint)

        editorPanelRows.clear()
        val rowTop = editorPanelRect.top + 88f * dp
        val available = (editorPanelRect.bottom - rowTop - 12f * dp).coerceAtLeast(1f)
        val gap = min(6f * dp, max(3f * dp, available * 0.018f))
        val rowH = ((available - gap * (virtualEditorItems().size - 1)) / virtualEditorItems().size).coerceIn(30f * dp, 38f * dp)
        virtualEditorItems().forEachIndexed { index, label ->
            val rect = RectF(editorPanelRect.left + 14f * dp, rowTop + index * (rowH + gap), editorPanelRect.right - 14f * dp, rowTop + index * (rowH + gap) + rowH)
            if (rect.bottom > editorPanelRect.bottom - 12f * dp) return@forEachIndexed
            editorPanelRows += MenuRow(index, rect)
            val selected = index == editorIndex
            fillPaint.color = if (selected) Color.argb(100, 255, 255, 255) else Color.argb(34, 255, 255, 255)
            strokePaint.color = if (selected) Color.argb(230, 255, 255, 255) else Color.argb(95, 255, 255, 255)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 12f * dp
            textPaint.color = Color.WHITE
            canvas.drawText(label, rect.left + 14f * dp, rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    private fun drawMenuPanel(canvas: Canvas) {
        fillPaint.color = Color.argb(232, 18, 22, 30)
        strokePaint.color = Color.argb(220, 255, 255, 255)
        strokePaint.strokeWidth = 1.5f * resources.displayMetrics.density
        canvas.drawRoundRect(menuPanelRect, 22f, 22f, fillPaint)
        canvas.drawRoundRect(menuPanelRect, 22f, 22f, strokePaint)

        menuRows.clear()
        val dp = resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 18f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(menuTitle(), menuPanelRect.left + 20f * dp, menuPanelRect.top + 36f * dp, textPaint)

        textPaint.textSize = 11.5f * dp
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.argb(210, 255, 255, 255)
        val hint = menuHint()
        if (hint.isNotBlank()) {
            drawFittedText(canvas, hint, menuPanelRect.left + 20f * dp, menuPanelRect.top + 58f * dp, menuPanelRect.width() - 40f * dp, textPaint)
        }

        when (menuPage) {
            MenuPage.MAIN -> drawList(canvas, CommonEmulatorUiSpec.mainMenuItems(context), menuIndex, menuPanelRect.top + 64f * dp) { index, _ -> subtitleForMain(index) }
            MenuPage.SAVE -> drawSaveManager(canvas)
            MenuPage.LOAD -> drawSlotList(canvas, loadSlotIndex, mode = SlotListMode.LOAD)
            MenuPage.DELETE_SAVE -> drawSlotList(canvas, deleteSlotIndex, mode = SlotListMode.DELETE)
            MenuPage.VIRTUAL_KEYS -> drawVirtualSettings(canvas)
            MenuPage.VIRTUAL_ALPHA -> drawVirtualAlphaSettings(canvas)
            MenuPage.VIRTUAL_EDITOR -> drawVirtualEditor(canvas)
            MenuPage.CHEATS -> drawCheatList(canvas)
            MenuPage.CUSTOM_CHEATS -> drawCustomCheatList(canvas)
            MenuPage.CUSTOM_CHEAT_DELETE -> drawCustomCheatDeleteList(canvas)
            MenuPage.RESET_CONFIRM -> drawResetConfirm(canvas)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }

    private fun menuTitle(): String = when (menuPage) {
        MenuPage.MAIN -> tr("emulator.menu.title", "Built-in {platform} Menu", "platform" to "GBA")
        MenuPage.SAVE -> tr("emulator.menu.save", "Save States")
        MenuPage.LOAD -> tr("emulator.menu.load", "Load State")
        MenuPage.DELETE_SAVE -> tr("emulator.menu.delete_save", "Delete Save")
        MenuPage.VIRTUAL_KEYS -> tr("emulator.menu.virtual_keys", "Virtual Buttons")
        MenuPage.VIRTUAL_ALPHA -> tr("emulator.menu.transparency", "Transparency")
        MenuPage.VIRTUAL_EDITOR -> tr("emulator.menu.virtual_editor", "Virtual Button Editor")
        MenuPage.CHEATS -> tr("emulator.menu.cheat", "Cheats")
        MenuPage.CUSTOM_CHEATS -> tr("emulator.menu.cheats_custom", "Custom Cheats")
        MenuPage.CUSTOM_CHEAT_DELETE -> tr("emulator.menu.cheat_delete", "Delete Cheat")
        MenuPage.RESET_CONFIRM -> tr("emulator.menu.reset_game", "Reset Game")
    }

    private fun menuHint(): String = when (menuPage) {
        MenuPage.MAIN -> CommonEmulatorUiSpec.mainMenuHint(activity)
        MenuPage.SAVE -> CommonEmulatorUiSpec.saveMenuHint(context)
        MenuPage.LOAD -> tr("emulator.hint.load", "Up/Down select a slot, A load and close menu, B back.")
        MenuPage.DELETE_SAVE -> tr("emulator.hint.delete", "Up/Down select, A delete, B back. Quick save can also be deleted.")
        MenuPage.VIRTUAL_KEYS -> CommonEmulatorUiSpec.virtualKeysHint(context)
        MenuPage.VIRTUAL_ALPHA -> tr("emulator.hint.virtual_alpha", "Controller-mode opacity. Left/right adjust, B back.")
        MenuPage.VIRTUAL_EDITOR -> tr("emulator.hint.virtual_editor", "Drag buttons; tap increase/decrease or use left/right to resize, then save.")
        MenuPage.CHEATS -> ""
        MenuPage.CUSTOM_CHEATS -> tr("emulator.hint.custom_cheats", "A add/toggle, B back; use the delete button to remove cheats.")
        MenuPage.CUSTOM_CHEAT_DELETE -> tr("emulator.hint.delete_cheat", "Up/Down select, A delete, B back.")
        MenuPage.RESET_CONFIRM -> CommonEmulatorUiSpec.resetHint(context)
    }

    private fun subtitleForMain(index: Int): String = when (index) {
        0 -> tr("emulator.subtitle.save", "Manage saves, loads, deletes, and quick save")
        1 -> tr("emulator.subtitle.virtual", "Opacity, position, size, and custom combo buttons")
        2 -> tr("emulator.subtitle.cheat", "Manage custom cheat codes")
        3 -> CommonEmulatorUiSpec.resetHint(context)
        4 -> CommonEmulatorUiSpec.restartHint(context)
        5 -> tr("emulator.subtitle.exit", "Exit to launcher")
        else -> ""
    }

    private fun drawList(canvas: Canvas, labels: List<String>, selected: Int, top: Float, subtitle: (Int, String) -> String = { _, _ -> "" }) {
        val dp = resources.displayMetrics.density
        if (labels.isEmpty()) return
        val left = menuPanelRect.left + 20f * dp
        val right = menuPanelRect.right - 20f * dp
        val bottomPadding = when (menuPage) {
            MenuPage.CHEATS -> 72f * dp
            MenuPage.SAVE -> 72f * dp
            else -> 12f * dp
        }
        val available = (menuPanelRect.bottom - bottomPadding - top).coerceAtLeast(1f)
        val minRowH = 32f * dp
        val gap = min(7f * dp, max(3f * dp, available * 0.018f))
        val maxVisible = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
        val visibleCount = min(labels.size, maxVisible)
        val startIndex = when {
            labels.size <= visibleCount -> 0
            selected <= visibleCount / 2 -> 0
            selected >= labels.size - (visibleCount - visibleCount / 2) -> labels.size - visibleCount
            else -> selected - visibleCount / 2
        }.coerceIn(0, max(0, labels.size - visibleCount))
        val rowH = ((available - gap * (visibleCount - 1)) / visibleCount).coerceIn(minRowH, 48f * dp)

        for (offset in 0 until visibleCount) {
            val index = startIndex + offset
            val label = labels[index]
            val y = top + offset * (rowH + gap)
            val rect = RectF(left, y, right, y + rowH)
            if (rect.bottom > menuPanelRect.bottom - bottomPadding / 2f) break
            menuRows += MenuRow(index, rect)
            val isSelected = index == selected
            fillPaint.color = if (isSelected) Color.argb(94, 255, 255, 255) else Color.argb(30, 255, 255, 255)
            strokePaint.color = if (isSelected) Color.argb(230, 255, 255, 255) else Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = min(14f * dp, rowH * 0.32f)
            textPaint.color = Color.WHITE
            val sub = subtitle(index, label)
            val mainY = if (sub.isBlank()) {
                rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            } else {
                rect.top + rowH * 0.40f
            }
            drawFittedText(canvas, label, rect.left + 18f * dp, mainY, rect.width() - 36f * dp, textPaint)
            if (sub.isNotBlank()) {
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = min(10.5f * dp, rowH * 0.23f)
                textPaint.color = Color.argb(185, 255, 255, 255)
                drawFittedText(canvas, sub, rect.left + 18f * dp, rect.top + rowH * 0.74f, rect.width() - 36f * dp, textPaint)
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

    private fun drawSaveManager(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        stateSaveButtonRect.setEmpty()
        stateDeleteButtonRect.setEmpty()
        stateLoadButtonRect.setEmpty()

        val entries = (1..MAX_STATE_SLOTS).map { slot ->
            val time = activity.stateTime(activity.gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
            "${tr("emulator.state.slot_label", "Save {slot}", "slot" to slot)}    $time"
        } + listOf("${tr("emulator.state.quick_label", "Quick Save")}    ${activity.stateTime(activity.gameStorage?.quickStateFile ?: File("/__md3e_missing_quick_state__"))}")

        val left = menuPanelRect.left + 20f * dp
        val right = menuPanelRect.right - 20f * dp
        val top = menuPanelRect.top + 68f * dp
        val actionH = 32f * dp
        val actionBottom = menuPanelRect.bottom - 14f * dp
        val listBottom = actionBottom - actionH - 16f * dp
        val available = (listBottom - top).coerceAtLeast(1f)
        val gap = 4f * dp
        val separatorH = 10f * dp
        val minRowH = 26f * dp
        val maxRowH = 34f * dp
        val selected = saveSlotIndex.coerceIn(0, entries.lastIndex)

        // 小屏或横屏时按当前选中项自动滚动，只绘制可见行，避免和底部 A/X/Y 按钮重叠。
        var visibleCount = ((available + gap) / (minRowH + gap)).toInt().coerceAtLeast(1)
        visibleCount = min(entries.size, visibleCount)
        var startIndex = when {
            entries.size <= visibleCount -> 0
            selected <= visibleCount / 2 -> 0
            selected >= entries.size - (visibleCount - visibleCount / 2) -> entries.size - visibleCount
            else -> selected - visibleCount / 2
        }.coerceIn(0, max(0, entries.size - visibleCount))

        var quickVisible = startIndex <= MAX_STATE_SLOTS && startIndex + visibleCount > MAX_STATE_SLOTS
        var rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
        fun saveListUsedHeight(count: Int, itemHeight: Float, showQuickSeparator: Boolean): Float {
            val separator = if (showQuickSeparator) separatorH else 0f
            return count * itemHeight + gap * (count - 1) + separator
        }

        while (visibleCount > 1 && top + saveListUsedHeight(visibleCount, rowH, quickVisible) > listBottom) {
            visibleCount -= 1
            startIndex = when {
                entries.size <= visibleCount -> 0
                selected <= visibleCount / 2 -> 0
                selected >= entries.size - (visibleCount - visibleCount / 2) -> entries.size - visibleCount
                else -> selected - visibleCount / 2
            }.coerceIn(0, max(0, entries.size - visibleCount))
            quickVisible = startIndex <= MAX_STATE_SLOTS && startIndex + visibleCount > MAX_STATE_SLOTS
            rowH = ((available - gap * (visibleCount - 1) - (if (quickVisible) separatorH else 0f)) / visibleCount).coerceIn(minRowH, maxRowH)
        }

        var y = top
        for (offset in 0 until visibleCount) {
            val index = startIndex + offset
            if (index !in entries.indices) break
            if (index == MAX_STATE_SLOTS) {
                val lineY = y + separatorH * 0.35f
                strokePaint.strokeWidth = 1.1f * dp
                strokePaint.color = Color.argb(130, 255, 255, 255)
                canvas.drawLine(left + 8f * dp, lineY, right - 8f * dp, lineY, strokePaint)
                y += separatorH
            }
            if (y + rowH > listBottom + 0.5f * dp) break

            val rect = RectF(left, y, right, y + rowH)
            menuRows += MenuRow(index, rect)
            val isSelected = index == selected
            fillPaint.color = if (isSelected) Color.argb(94, 255, 255, 255) else Color.argb(30, 255, 255, 255)
            strokePaint.color = if (isSelected) Color.argb(230, 255, 255, 255) else Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, fillPaint)
            canvas.drawRoundRect(rect, rowH / 2f, rowH / 2f, strokePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = min(13f * dp, rowH * 0.34f)
            textPaint.color = Color.WHITE
            canvas.drawText(entries[index], rect.left + 18f * dp, rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
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

        val selectedFile = if (selected >= MAX_STATE_SLOTS) {
            activity.gameStorage?.quickStateFile
        } else {
            activity.gameStorage?.slotStateFile(selected + 1)
        }
        val hasState = selectedFile?.let { it.exists() && it.length() > 0L } == true
        val actionLeft = menuPanelRect.left + 20f * dp
        val actionRight = menuPanelRect.right - 20f * dp
        val actionGap = 10f * dp
        val actionW = ((actionRight - actionLeft - actionGap * 2f) / 3f).coerceAtLeast(74f * dp)
        stateSaveButtonRect.set(actionLeft, actionBottom - actionH, actionLeft + actionW, actionBottom)
        stateDeleteButtonRect.set(stateSaveButtonRect.right + actionGap, actionBottom - actionH, stateSaveButtonRect.right + actionGap + actionW, actionBottom)
        stateLoadButtonRect.set(stateDeleteButtonRect.right + actionGap, actionBottom - actionH, actionRight, actionBottom)
        drawSmallMenuButton(canvas, stateSaveButtonRect, tr("emulator.state.button_save", "A Save"), enabled = true)
        drawSmallMenuButton(canvas, stateDeleteButtonRect, tr("emulator.state.button_delete", "X Delete"), enabled = hasState)
        drawSmallMenuButton(canvas, stateLoadButtonRect, tr("emulator.state.button_load", "Y Load"), enabled = hasState)
    }

    private fun drawSlotList(canvas: Canvas, selected: Int, mode: SlotListMode) {
        val dp = resources.displayMetrics.density
        val labels = when (mode) {
            SlotListMode.DELETE -> listOf("${tr("emulator.state.quick_label", "Quick Save")}    ${activity.stateTime(activity.gameStorage?.quickStateFile ?: File("/__md3e_missing_quick_state__"))}") +
                (1..MAX_STATE_SLOTS).map { slot ->
                    val time = activity.stateTime(activity.gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
                    "${tr("emulator.state.slot_label", "Save {slot}", "slot" to slot)}    $time"
                }
            SlotListMode.SAVE,
            SlotListMode.LOAD -> (1..MAX_STATE_SLOTS).map { slot ->
                val time = activity.stateTime(activity.gameStorage?.slotStateFile(slot) ?: File("/__md3e_missing_slot_state__"))
                "${tr("emulator.state.slot_label", "Save {slot}", "slot" to slot)}    $time"
            }
        }
        drawList(canvas, labels, selected, menuPanelRect.top + 64f * dp) { index, _ ->
            when (mode) {
                SlotListMode.SAVE -> tr("emulator.state.button_save", "A Save")
                SlotListMode.LOAD -> tr("emulator.state.button_load", "Y Load")
                SlotListMode.DELETE -> if (index == 0) tr("emulator.state.delete_quick", "A Delete Quick Save") else tr("emulator.state.delete_slot", "A Delete Save {slot}", "slot" to index)
            }
        }
    }

    private fun drawVirtualSettings(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        drawList(canvas, CommonEmulatorUiSpec.virtualKeyMenuItems(context), virtualSettingsIndex, menuPanelRect.top + 72f * dp) { index, _ ->
            if (index == 0) tr("emulator.virtual.subtitle.transparency", "Adjust controller-mode virtual button opacity") else tr("emulator.virtual.subtitle.editor", "Drag button positions, resize with left/right, add combo buttons")
        }
    }

    private fun drawVirtualAlphaSettings(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val left = menuPanelRect.left + 26f * dp
        val right = menuPanelRect.right - 26f * dp
        val top = menuPanelRect.top + 96f * dp
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 15f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(tr("emulator.virtual.alpha_title", "Controller Virtual Button Opacity: {percent}%", "percent" to (activity.hardwareControlsAlpha * 100).roundToInt()), left, top, textPaint)

        val cy = top + 42f * dp
        strokePaint.strokeWidth = 5f * dp
        strokePaint.color = Color.argb(95, 255, 255, 255)
        canvas.drawLine(left, cy, right, cy, strokePaint)
        val thumbX = left + (right - left) * activity.hardwareControlsAlpha
        strokePaint.color = Color.argb(225, 255, 255, 255)
        canvas.drawLine(left, cy, thumbX, cy, strokePaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, cy, 10f * dp, fillPaint)
        strokePaint.strokeWidth = 2.5f * dp

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 12f * dp
        textPaint.color = Color.argb(205, 255, 255, 255)
        canvas.drawText(tr("emulator.virtual.alpha_left_right", "← More Transparent    → More Visible"), left, cy + 36f * dp, textPaint)
        canvas.drawText(tr("emulator.virtual.alpha_note", "Default is 0%. Raise it only if you need touch assistance while using a controller."), left, cy + 58f * dp, textPaint)
    }

    private fun drawVirtualEditor(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val selectedName = selectedEditId ?: tr("emulator.virtual.not_selected", "Not Selected")
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10.5f * dp
        textPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText(tr("emulator.virtual.selected", "Current: {name}; drag buttons to move them, use left/right to resize.", "name" to selectedName), menuPanelRect.left + 20f * dp, menuPanelRect.top + 74f * dp, textPaint)
        drawList(canvas, virtualEditorItems(), editorIndex, menuPanelRect.top + 84f * dp) { index, _ ->
            when (index) {
                0 -> tr("emulator.virtual.editor_sub.add", "Enter a label, style, and method, then place it on the screen center")
                1 -> tr("emulator.virtual.editor_sub.increase", "Increase the selected virtual button size")
                2 -> tr("emulator.virtual.editor_sub.decrease", "Decrease the selected virtual button size")
                3 -> tr("emulator.virtual.editor_sub.save", "Save all virtual button positions and sizes, then return to game")
                4 -> tr("emulator.virtual.editor_sub.reset", "Restore default button positions")
                else -> tr("emulator.virtual.editor_sub.cancel", "Discard unsaved changes and return to game")
            }
        }
    }

    private fun drawCheatList(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val custom = activity.loadCustomCheats()
        val selected = if (custom.isEmpty()) 0 else cheatIndex.coerceIn(0, custom.lastIndex)

        val buttonW = 120f * dp
        val buttonH = 34f * dp
        cheatAddButtonRect.set(
            menuPanelRect.right - 20f * dp - buttonW,
            menuPanelRect.top + 15f * dp,
            menuPanelRect.right - 20f * dp,
            menuPanelRect.top + 15f * dp + buttonH
        )
        drawSmallMenuButton(canvas, cheatAddButtonRect, tr("emulator.cheat.add_custom", "Custom Cheat"), enabled = true)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10.5f * dp
        textPaint.color = Color.argb(172, 255, 255, 255)
        val noteX = menuPanelRect.left + 20f * dp
        val noteMaxW = menuPanelRect.width() - 40f * dp
        drawFittedText(canvas, tr("emulator.cheat.close_note", "Disable: quick-save first, restart, then auto quick-load."), noteX, menuPanelRect.top + 56f * dp, noteMaxW, textPaint)
        drawFittedText(canvas, tr("emulator.cheat.stable_note", "This is the stable path; seamless disable needs a native CheatManager later."), noteX, menuPanelRect.top + 72f * dp, noteMaxW, textPaint)
        textPaint.color = Color.argb(230, 255, 230, 120)
        drawFittedText(canvas, tr("emulator.cheat.conflict_tip", "Tip: Walk-through-walls and shiny may conflict when both are enabled; use one at a time if needed."), noteX, menuPanelRect.top + 88f * dp, noteMaxW, textPaint)

        if (custom.isEmpty()) {
            cheatEnableButtonRect.setEmpty()
            cheatResetButtonRect.setEmpty()
            cheatDeleteButtonRect.setEmpty()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 15f * dp
            textPaint.color = Color.argb(220, 255, 255, 255)
            canvas.drawText(tr("emulator.cheat.no_cheats", "No Cheats"), menuPanelRect.centerX(), menuPanelRect.centerY() + 2f * dp, textPaint)
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 12f * dp
            textPaint.color = Color.argb(175, 255, 255, 255)
            canvas.drawText(tr("emulator.cheat.add_tip", "Tap Custom Cheat to add one"), menuPanelRect.centerX(), menuPanelRect.centerY() + 28f * dp, textPaint)
        } else {
            val labels = custom.map { item -> "${item.name}: ${if (item.enabled) tr("emulator.cheat.status_on", "On") else tr("emulator.cheat.status_off", "Off")}" }
            drawList(canvas, labels, selected, menuPanelRect.top + 100f * dp) { index, _ ->
                val item = custom.getOrNull(index)
                if (item == null) "" else tr("emulator.cheat.type_preview", "{type} · {code} Cheat Code", "type" to item.type, "code" to cheatPreview(item.code))
            }
        }

        val bottom = menuPanelRect.bottom - 16f * dp
        val actionH = 34f * dp
        val hasSelection = custom.isNotEmpty()
        val actionLeft = menuPanelRect.left + 20f * dp
        val actionRight = menuPanelRect.right - 20f * dp
        val actionGap = 12f * dp
        val actionW = ((actionRight - actionLeft - actionGap) / 2f).coerceAtLeast(96f * dp)
        cheatEnableButtonRect.set(actionLeft, bottom - actionH, actionLeft + actionW, bottom)
        cheatResetButtonRect.setEmpty()
        cheatDeleteButtonRect.set(cheatEnableButtonRect.right + actionGap, bottom - actionH, actionRight, bottom)
        val enabledLabel = custom.getOrNull(selected)?.enabled == true
        drawSmallMenuButton(canvas, cheatEnableButtonRect, if (enabledLabel) tr("emulator.cheat.disable", "A Disable") else tr("emulator.cheat.enable", "A Enable"), enabled = hasSelection)
        drawSmallMenuButton(canvas, cheatDeleteButtonRect, tr("emulator.cheat.delete", "X Delete"), enabled = hasSelection)
    }

    private fun drawSmallMenuButton(canvas: Canvas, rect: RectF, label: String, enabled: Boolean) {
        val dp = resources.displayMetrics.density
        fillPaint.color = if (enabled) Color.argb(96, 255, 255, 255) else Color.argb(28, 255, 255, 255)
        strokePaint.color = if (enabled) Color.argb(225, 255, 255, 255) else Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 11.5f * dp
        textPaint.color = if (enabled) Color.WHITE else Color.argb(125, 255, 255, 255)
        canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    private fun drawCustomCheatList(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val custom = activity.loadCustomCheats()
        val labels = mutableListOf(tr("emulator.cheat.add", "Add Cheat"))
        custom.forEach { item -> labels += "${item.name}: ${if (item.enabled) tr("emulator.cheat.status_on", "On") else tr("emulator.cheat.status_off", "Off")}" }
        drawList(canvas, labels, customCheatIndex.coerceIn(0, max(0, labels.size - 1)), menuPanelRect.top + 64f * dp) { index, _ ->
            when (index) {
                0 -> tr("emulator.cheat.add_subtitle", "Enter name, type, and code, then save")
                else -> {
                    val item = custom.getOrNull(index - 1)
                    if (item == null) "" else tr("emulator.cheat.type_preview", "{type} · {code} Cheat Code", "type" to item.type, "code" to cheatPreview(item.code))
                }
            }
        }

        val buttonW = 96f * dp
        val buttonH = 34f * dp
        customDeleteButtonRect.set(
            menuPanelRect.right - 20f * dp - buttonW,
            menuPanelRect.bottom - 16f * dp - buttonH,
            menuPanelRect.right - 20f * dp,
            menuPanelRect.bottom - 16f * dp
        )
        fillPaint.color = Color.argb(92, 255, 255, 255)
        strokePaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawRoundRect(customDeleteButtonRect, buttonH / 2f, buttonH / 2f, fillPaint)
        canvas.drawRoundRect(customDeleteButtonRect, buttonH / 2f, buttonH / 2f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 12f * dp
        textPaint.color = Color.WHITE
        canvas.drawText(tr("emulator.cheat.delete_short", "Delete"), customDeleteButtonRect.centerX(), customDeleteButtonRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    private fun drawCustomCheatDeleteList(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val custom = activity.loadCustomCheats()
        val labels = if (custom.isEmpty()) listOf(tr("emulator.cheat.no_custom", "No custom cheats")) else custom.map { tr("emulator.cheat.delete_prefix", "Delete: {name}", "name" to it.name) }
        drawList(canvas, labels, deleteCustomCheatIndex.coerceIn(0, max(0, labels.size - 1)), menuPanelRect.top + 64f * dp) { index, _ ->
            val item = custom.getOrNull(index)
            if (item == null) tr("emulator.cheat.add_first", "Add a custom cheat in the top-right first") else tr("emulator.cheat.delete_subtitle", "A Delete · {type} · {code}", "type" to item.type, "code" to cheatPreview(item.code))
        }
    }

    private fun cheatPreview(code: String): String {
        val normalized = activity.normalizeCheatCode(code)
        return if (normalized.length <= 22) normalized else normalized.take(22) + "..."
    }

    fun refreshCustomCheatIndex() {
        val size = activity.loadCustomCheats().size
        cheatIndex = cheatIndex.coerceIn(0, max(0, size - 1))
        customCheatIndex = customCheatIndex.coerceIn(0, size)
        deleteCustomCheatIndex = deleteCustomCheatIndex.coerceIn(0, max(0, size - 1))
    }

    private fun drawResetConfirm(canvas: Canvas) {
        val dp = resources.displayMetrics.density
        val labels = listOf(tr("emulator.reset.confirm", "A: Confirm reload game"), tr("emulator.reset.cancel", "B: Cancel and return"))
        drawList(canvas, labels, menuIndex.coerceIn(0, 1), menuPanelRect.top + 92f * dp) { _, _ -> "" }
    }

    private fun drawFittedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (maxWidth <= 0f) return
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) {
            end--
        }
        canvas.drawText(if (end <= 0) ellipsis else text.take(end) + ellipsis, x, y, paint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, pressed: Boolean, alpha: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (pressed) Color.BLACK else alphaColor(255, alpha)
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), y, textPaint)
    }

    private fun alphaColor(alpha: Int, multiplier: Float): Int =
        Color.argb((alpha * multiplier).roundToInt().coerceIn(0, 255), 255, 255, 255)
}
