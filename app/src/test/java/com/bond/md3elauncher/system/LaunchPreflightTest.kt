package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.*
import org.junit.Test

class LaunchPreflightTest {
    @Test fun preventsUnsupportedInternalArchivesWithoutBlockingExternalEmulators() {
        assertEquals("launch.check.archive", LaunchPreflight.formatIssue(PlatformKind.GBA, "7Z", true))
        assertNull(LaunchPreflight.formatIssue(PlatformKind.GBA, "7z", false))
        assertNull(LaunchPreflight.formatIssue(PlatformKind.ARCADE, "zip", true))
    }
    @Test fun respectsCurrentSingleFilePs1Contract() {
        assertEquals("launch.check.disc", LaunchPreflight.formatIssue(PlatformKind.PS1, "cue", true))
        assertNull(LaunchPreflight.formatIssue(PlatformKind.PS1, "chd", true))
        assertNull(LaunchPreflight.formatIssue(PlatformKind.PS1, "cue", false))
    }
}
