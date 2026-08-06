package com.bond.md3elauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeMarginSettingsTest {
    @Test
    fun clampsBothMarginsToSupportedRange() {
        assertEquals(
            SafeMarginSettings(leftDp = SafeMarginSettings.MIN_DP, rightDp = SafeMarginSettings.MAX_DP),
            SafeMarginSettings(leftDp = -20, rightDp = 120).clamped()
        )
    }
}
