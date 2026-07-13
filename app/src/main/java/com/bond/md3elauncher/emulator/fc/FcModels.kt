package com.bond.md3elauncher.emulator.fc

import android.graphics.RectF
import java.io.File

internal data class FcGameStorage(
    val key: String,
    val displayName: String,
    val root: File
) {
    val saveRamFile: File get() = File(root, "save_ram.srm")
    val quickStateFile: File get() = File(root, "quick.state")
    fun slotStateFile(slot: Int): File = File(root, "slot_$slot.state")
}

internal data class FcTouchButton(
    val id: String,
    val label: String,
    val keyCodes: Set<Int>,
    val rect: RectF,
    val circle: Boolean = false
) {
    val keyCode: Int get() = keyCodes.firstOrNull() ?: -999999
}

internal data class FcMenuRow(
    val index: Int,
    val rect: RectF
)

internal const val FC_VIRTUAL_FAST_FORWARD = -200001
internal const val FC_VIRTUAL_MENU = -200002
internal const val FC_VIRTUAL_TURBO_A = -200003
internal const val FC_VIRTUAL_TURBO_B = -200004
internal const val FC_VIRTUAL_QUICK_SAVE = -200005
internal const val FC_VIRTUAL_QUICK_LOAD = -200006
internal const val FC_VIRTUAL_EXIT = -200007

internal const val FC_MAX_STATE_SLOTS = 5
