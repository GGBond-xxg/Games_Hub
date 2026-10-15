package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.EmulatorLaunchConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomLaunchConfigTest {
    private val config = EmulatorLaunchConfig(enabled = true)

    @Test fun oldPlatformDataDefaultsToExistingLaunchAdapters() {
        assertEquals(EmulatorLaunchConfig(), CustomLaunchConfig.decode(null))
        assertFalse(CustomLaunchConfig.decode(JSONObject()).enabled)
    }

    @Test fun persistedSettingsRoundTripWithoutLosingTemplatesOrTypes() {
        val custom = config.copy(
            activityName = ".GameActivity", action = "android.intent.action.MAIN",
            mimeType = "application/octet-stream", dataTemplate = "",
            extrasJson = """{"ROM":"{romUri}","TITLE":"{gameTitle}","core":"/cores/example.so","resume":true,"slot":3,"stamp":2147483648,"speed":1.5}"""
        )
        val restored = CustomLaunchConfig.decode(JSONObject(CustomLaunchConfig.encode(custom).toString()))
        assertEquals(custom, restored)
        assertNull(CustomLaunchConfig.validationError(restored))
        val extras = CustomLaunchConfig.parseExtras(restored.extrasJson)
        assertEquals(true, extras["resume"])
        assertEquals(3, extras["slot"])
        assertEquals(2147483648L, extras["stamp"])
        assertEquals(1.5, extras["speed"])
    }

    @Test fun rejectsMalformedNestedAndAmbiguousExtras() {
        listOf(
            "[]", "null", "{\"x\":null}", "{\"x\":{}}", "{\"x\":[]}",
            "{'x':1}", "{x:1}", "{\"x\":1,}", "{\"x\":01}", "{\"x\":NaN}",
            "{\"x\":1e999}", "{\"x\":9223372036854775808}", "{\"x\":true} trailing",
            "{\"x\":1,\"x\":2}", "{\"\":1}", "{\"x\":\"bad\nline\"}", "{\"x\":\"\\z\"}"
        ).forEach { raw ->
            assertEquals(raw, "launch.custom.invalid", CustomLaunchConfig.validationError(config.copy(extrasJson = raw)))
        }
    }

    @Test fun rejectsOversizedInputsAndUnknownPlaceholders() {
        listOf(
            config.copy(extrasJson = " ".repeat(16385)),
            config.copy(extrasJson = """{"x":"${"a".repeat(4097)}"}"""),
            config.copy(dataTemplate = "{romPath}"),
            config.copy(extrasJson = """{"x":"{unknown}"}"""),
            config.copy(action = ""), config.copy(action = "invalid action"),
            config.copy(mimeType = "invalid"), config.copy(activityName = "other.package/.Activity"),
            config.copy(activityName = "Activity"), config.copy(activityName = ".")
        ).forEach { assertEquals("launch.custom.invalid", CustomLaunchConfig.validationError(it)) }
    }

    @Test fun templatesPreserveLiteralRomNamesAndJsonEscaping() {
        val extras = CustomLaunchConfig.parseExtras(""" { "title": "hello \"world\"\n\u4e2d", "count": -2147483649 } """)
        assertEquals("hello \"world\"\n中", extras["title"])
        assertEquals(-2147483649L, extras["count"])
        assertEquals("content://roms/{gameTitle}|Name {romUri}", CustomLaunchConfig.renderTemplate(
            "{romUri}|{gameTitle}", "content://roms/{gameTitle}", "Name {romUri}"
        ))
    }

    @Test fun resolvesOnlyRelativeActivityNamesAgainstTheSelectedPackage() {
        assertEquals("org.example.Game", CustomLaunchConfig.activityClass("org.example", ".Game"))
        assertEquals("other.namespace.Game", CustomLaunchConfig.activityClass("org.example", "other.namespace.Game"))
        assertNull(CustomLaunchConfig.activityClass("org.example", ""))
        assertNull(CustomLaunchConfig.validationError(config.copy(activityName = "other.namespace.Game")))
    }

    @Test fun invalidEnabledConfigIsNotSilentlyResetToLegacyLaunch() {
        val restored = CustomLaunchConfig.decode(CustomLaunchConfig.encode(config.copy(extrasJson = "broken")))
        assertTrue(restored.enabled)
        assertEquals("launch.custom.invalid", CustomLaunchConfig.validationError(restored))
        assertNull(CustomLaunchConfig.validationError(restored.copy(enabled = false)))
    }
}
