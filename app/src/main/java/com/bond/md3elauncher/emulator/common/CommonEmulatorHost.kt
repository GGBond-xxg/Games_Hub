package com.bond.md3elauncher.emulator.common

/**
 * Target contract for future full menu modularization.
 *
 * v0.1.74 introduces the shared specs and shared GBA-style touch layout. The next
 * step is to let each Internal*Activity implement this adapter so one common menu
 * view can call save/load/reset/cheat actions without caring which core is used.
 */
internal interface CommonEmulatorHost {
    val platformName: String

    fun saveSlotState(slot: Int)
    fun loadSlotState(slot: Int, hideMenuAfterLoad: Boolean)
    fun deleteSlotState(slot: Int)

    fun saveQuickState()
    fun loadQuickState(hideMenuAfterLoad: Boolean)
    fun deleteQuickState()

    fun openCheatMenu()
    fun openVirtualPadSettings()

    fun resetGameFresh()
    fun restartGameNoExit()
    fun exitGame()
}
