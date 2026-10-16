package com.bond.md3elauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.md3elauncher.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LauncherIconTest {
    @Test fun legacyIconHasTransparentEdgesAtEveryDensity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (density in listOf(160, 240, 320, 480, 640)) {
            val drawable = context.resources.getDrawableForDensity(R.mipmap.ic_launcher_legacy, density, context.theme)!!
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                for (x in 0 until bitmap.width) {
                    assertEquals(0, Color.alpha(bitmap.getPixel(x, 0)))
                    assertEquals(0, Color.alpha(bitmap.getPixel(x, bitmap.height - 1)))
                }
                for (y in 0 until bitmap.height) {
                    assertEquals(0, Color.alpha(bitmap.getPixel(0, y)))
                    assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
                }
                // Verify the original red/white artwork remains visible, not an empty icon.
                val top = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
                assertEquals(255, Color.alpha(top))
                assertTrue(Color.red(top) > 200 && Color.green(top) < 80)
                assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4))
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun modernLauncherStillResolvesAdaptiveIcons() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
        assertTrue(context.packageManager.getApplicationIcon(context.applicationInfo) is AdaptiveIconDrawable)
    }
}
