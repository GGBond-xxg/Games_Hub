package com.bond.md3elauncher.emulator.ps1

import com.bond.md3elauncher.emulator.fc.InternalFcActivity

/**
 * Runs the shared libretro host in a dedicated PS1 process.
 *
 * The host forces PCSX-ReARMed's HLE BIOS so GameHub never needs to bundle
 * Sony firmware. Users may choose an external emulator for wider disc support.
 */
class InternalPs1Activity : InternalFcActivity()
