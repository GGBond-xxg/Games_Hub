package com.bond.md3elauncher.emulator.md

import com.bond.md3elauncher.emulator.fc.InternalFcActivity

/**
 * Runs the shared cartridge-era libretro host in its own MD/Genesis process.
 *
 * The separate Activity class lets Android isolate MD native-core failures from
 * the existing FC/NES/SFC runtime while retaining the common save/menu contract.
 */
class InternalMdActivity : InternalFcActivity()
