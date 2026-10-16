package com.bond.md3elauncher.emulator.md

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.md3elauncher.emulator.ControllerShortcutAction
import com.bond.md3elauncher.emulator.ControllerShortcutSettings
import com.bond.md3elauncher.emulator.fc.InternalFcActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Opt-in real-core test. Supply original diagnostic ROMs separately; none are shipped in the repo/APK.
 * Probe contract: red on boot, button 1 green, button 2 blue, Pause/Start yellow, latched until next input.
 * Run in the main instrumentation process; the runtime owns and closes :internal_md.
 */
@RunWith(Parameterized::class)
class Sega8RuntimeTest(private val systemId: String, private val zipped: Boolean) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private fun key(code: Int, action: Int, down: Long = SystemClock.uptimeMillis()) {
        assertTrue(automation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0,
            -1, 0, 0, InputDevice.SOURCE_GAMEPAD), true))
    }
    private fun press(code: Int) {
        val down = SystemClock.uptimeMillis()
        key(code, KeyEvent.ACTION_DOWN, down)
        SystemClock.sleep(100)
        key(code, KeyEvent.ACTION_UP, down)
        SystemClock.sleep(180)
    }
    private fun shortcut(action: ControllerShortcutAction) {
        val keys = ControllerShortcutSettings.load(context).keysFor(action).toList()
        val down = SystemClock.uptimeMillis()
        keys.forEach { key(it, KeyEvent.ACTION_DOWN, down) }
        SystemClock.sleep(150)
        keys.reversed().forEach { key(it, KeyEvent.ACTION_UP, down) }
        SystemClock.sleep(250)
    }
    private fun await(message: String, test: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < until) {
            if (test()) return
            SystemClock.sleep(200)
        }
        automation.takeScreenshot()?.let { bitmap ->
            File(context.externalCacheDir, "qa-sega8-failure-$systemId-$zipped.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        fail(message)
    }
    private fun color(name: String) {
        await("$systemId/$zipped: expected $name diagnostic frame") {
            val bitmap = automation.takeScreenshot() ?: return@await false
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            bitmap.recycle()
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            when (name) {
                "red" -> r > g + 50 && r > b + 50
                "green" -> g > r + 50 && g > b + 50
                "blue" -> b > r + 50 && b > g + 30
                else -> r > b + 50 && g > b + 50
            }
        }
    }
    private fun runtimePid(): Int? = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .runningAppProcesses?.firstOrNull { it.processName == "${context.packageName}:internal_md" }?.pid
    private fun menuAction(index: Int) {
        press(KeyEvent.KEYCODE_MENU)
        repeat(index) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        press(KeyEvent.KEYCODE_BUTTON_A)
    }

    @Test fun buttonsSlotsQuickLoadRestartResetAndProcessExit() {
        val dir = InstrumentationRegistry.getArguments().getString("sega8_probe_dir")
        assumeTrue("Provide sega8_probe_dir to run original diagnostic programs", dir != null)
        val selected = InstrumentationRegistry.getArguments().getString("sega8_system")
        assumeTrue(selected == null || selected == "$systemId-$zipped")
        val system = requireNotNull(Sega8BitSystem.fromId(systemId))
        val probe = File(dir!!, if (zipped) "probe-$systemId.zip" else "probe.${system.extension}")
        assertTrue("Missing diagnostic ${probe.name}", probe.isFile)
        val title = "qa-sega8-${systemId}-${System.currentTimeMillis()}"
        val roots = File(context.filesDir, "${system.storageDirectory}/game_data")
        fun storage() = roots.listFiles()?.singleOrNull { it.name.endsWith(title) }
        fun launch() = context.startActivity(Intent(context, InternalMdActivity::class.java).apply {
            // The test has no foreground Activity. A fresh task prevents Android from merely
            // bringing the launcher's cold-reset relay task forward on the second launch.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra(InternalFcActivity.EXTRA_ROM_URI, probe.toURI().toString())
            putExtra(InternalFcActivity.EXTRA_FILE_NAME, probe.name)
            putExtra(InternalFcActivity.EXTRA_TITLE, title)
            putExtra(InternalFcActivity.EXTRA_PLATFORM_LABEL, system.kind.title)
            putExtra(InternalFcActivity.EXTRA_CORE_FILE_NAME, "libgenesis_plus_gx_libretro_android.so")
            putExtra(Sega8BitSystem.EXTRA_SYSTEM, systemId)
        })
        try {
            launch(); color("red")
            press(KeyEvent.KEYCODE_BUTTON_B); color("green")
            press(KeyEvent.KEYCODE_BUTTON_A); color("blue")
            press(KeyEvent.KEYCODE_BUTTON_START); color("yellow")
            shortcut(ControllerShortcutAction.TURBO_B); color("green")
            shortcut(ControllerShortcutAction.TURBO_A); color("blue")
            repeat(3) { shortcut(ControllerShortcutAction.FAST_FORWARD) }
            color("blue")
            shortcut(ControllerShortcutAction.OPEN_MENU)
            press(KeyEvent.KEYCODE_BUTTON_B); color("blue")
            press(KeyEvent.KEYCODE_BUTTON_B); color("green")
            menuAction(0)
            for (slot in 1..6) {
                press(KeyEvent.KEYCODE_BUTTON_A)
                val name = if (slot == 6) "quick.state" else "slot_$slot.state"
                await("Missing $systemId $name") { storage()?.let { File(it, name).length() > 0L } == true }
                if (slot < 6) press(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            press(KeyEvent.KEYCODE_BUTTON_Y); color("green")
            press(KeyEvent.KEYCODE_BUTTON_A); color("blue")
            shortcut(ControllerShortcutAction.QUICK_LOAD); color("green")
            press(KeyEvent.KEYCODE_BUTTON_A); color("blue")
            shortcut(ControllerShortcutAction.QUICK_SAVE)
            press(KeyEvent.KEYCODE_BUTTON_B); color("green")
            shortcut(ControllerShortcutAction.QUICK_LOAD); color("blue")
            // Loading a normal slot uses the same menu/controller path as the established cores.
            menuAction(0); press(KeyEvent.KEYCODE_BUTTON_Y); color("green")
            val pid = runtimePid()
            menuAction(4); color("red")
            assertEquals("Soft restart must keep its runtime", pid, runtimePid())
            menuAction(3); press(KeyEvent.KEYCODE_DPAD_UP); press(KeyEvent.KEYCODE_BUTTON_A)
            await("Cold reset must replace its runtime") { runtimePid()?.let { it != pid } == true }
            color("red")
            menuAction(0); press(KeyEvent.KEYCODE_BUTTON_Y); color("green")
            menuAction(5)
            await("Normal exit must close :internal_md") { runtimePid() == null }
            launch(); color("red")
            shortcut(ControllerShortcutAction.QUICK_LOAD); color("blue")
            shortcut(ControllerShortcutAction.EXIT_GAME)
            await("Second exit must close :internal_md") { runtimePid() == null }
        } finally {
            // A failed assertion must not leave the shared native runtime running for the next case.
            runtimePid()?.let { pid ->
                automation.executeShellCommand("run-as ${context.packageName} kill $pid").close()
                await("Failed-test runtime cleanup") { runtimePid() == null }
            }
            // Only the uniquely named QA save directory; never user saves or preferences.
            storage()?.let { root -> root.listFiles()?.forEach { it.delete() }; root.delete() }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, zip={1}")
        fun cases() = listOf(arrayOf<Any>("SMS", false), arrayOf<Any>("GG", false), arrayOf<Any>("SG1000", false), arrayOf<Any>("SMS", true))
    }
}
