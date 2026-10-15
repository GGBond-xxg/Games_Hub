package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.EmulatorLaunchConfig
import org.json.JSONObject

/** Structured Intent settings only: no shell commands or filesystem-path inference. */
object CustomLaunchConfig {
    private const val INVALID = "launch.custom.invalid"
    private const val MAX_TEXT = 4096
    private const val MAX_EXTRAS = 16384
    private val className = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    private val placeholder = Regex("\\{(?:romUri|gameTitle)\\}")
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")

    fun validationError(config: EmulatorLaunchConfig): String? {
        if (!config.enabled) return null
        return runCatching {
            require(config.action.isNotBlank() && config.action.length <= 256 && config.action.none { it.isWhitespace() || it.isISOControl() })
            require(config.activityName.length <= 512)
            require(config.activityName.isEmpty() || (
                className.matches(config.activityName.removePrefix(".")) &&
                    (config.activityName.startsWith(".") || config.activityName.contains('.'))
                ))
            require(config.mimeType.isEmpty() || (config.mimeType.length <= 256 &&
                Regex("[A-Za-z0-9!#$&^_.+*-]+/[A-Za-z0-9!#$&^_.+*-]+").matches(config.mimeType)))
            validateTemplate(config.dataTemplate)
            parseExtras(config.extrasJson).values.filterIsInstance<String>().forEach(::validateTemplate)
            null
        }.getOrElse { INVALID }
    }

    private fun validateTemplate(value: String) {
        require(value.length <= MAX_TEXT && value.none { it == '\u0000' })
        val remaining = placeholder.replace(value, "")
        require('{' !in remaining && '}' !in remaining)
    }

    /** Replace in one pass so braces in a ROM name cannot become a second template. */
    fun renderTemplate(template: String, romUri: String, gameTitle: String): String =
        placeholder.replace(template) { if (it.value == "{romUri}") romUri else gameTitle }

    fun activityClass(packageName: String, activityName: String): String? = when {
        activityName.isEmpty() -> null
        activityName.startsWith(".") -> packageName + activityName
        else -> activityName
    }

    /** Strict JSON object parser; Android's JSONObject alone also accepts non-JSON syntax. */
    fun parseExtras(raw: String): Map<String, Any> {
        require(raw.length <= MAX_EXTRAS)
        var offset = 0
        fun whitespace() { while (offset < raw.length && raw[offset] in " \t\r\n") offset++ }
        fun consume(char: Char) { whitespace(); require(raw.getOrNull(offset) == char); offset++ }
        fun readString(): String {
            whitespace()
            require(raw.getOrNull(offset) == '"')
            val start = offset++
            while (offset < raw.length) {
                val char = raw[offset++]
                require(char >= ' ')
                if (char == '"') {
                    val token = raw.substring(start, offset)
                    return JSONObject("{\"value\":$token}").getString("value")
                }
                if (char == '\\') {
                    val escape = raw.getOrNull(offset++)
                    if (escape == 'u') {
                        require(offset + 4 <= raw.length)
                        repeat(4) { require(raw[offset++] in "0123456789abcdefABCDEF") }
                    } else {
                        require(escape != null && escape in "\"\\/bfnrt")
                    }
                }
            }
            error("Unterminated JSON string")
        }
        consume('{')
        whitespace()
        val result = linkedMapOf<String, Any>()
        if (raw.getOrNull(offset) != '}') {
            while (true) {
                val key = readString()
                require(key.isNotBlank() && key.length <= 256 && key.none { it.isISOControl() } && key !in result && result.size < 64)
                consume(':')
                whitespace()
                val value: Any = when {
                    raw.getOrNull(offset) == '"' -> readString().also { require(it.length <= MAX_TEXT) }
                    raw.startsWith("true", offset) -> { offset += 4; true }
                    raw.startsWith("false", offset) -> { offset += 5; false }
                    else -> {
                        val match = number.find(raw, offset)
                        require(match != null && match.range.first == offset)
                        offset = match.range.last + 1
                        val token = match.value
                        if (token.contains('.') || token.contains('e', ignoreCase = true)) {
                            token.toDouble().also { require(it.isFinite()) }
                        } else {
                            val integer = token.toLong()
                            if (integer in Int.MIN_VALUE..Int.MAX_VALUE) integer.toInt() else integer
                        }
                    }
                }
                result[key] = value
                whitespace()
                if (raw.getOrNull(offset) == '}') break
                consume(',')
            }
        }
        consume('}')
        whitespace()
        require(offset == raw.length)
        return result
    }

    fun encode(config: EmulatorLaunchConfig): JSONObject = JSONObject()
        .put("enabled", config.enabled)
        .put("activityName", config.activityName)
        .put("action", config.action)
        .put("mimeType", config.mimeType)
        .put("dataTemplate", config.dataTemplate)
        .put("extrasJson", config.extrasJson)

    fun decode(json: JSONObject?): EmulatorLaunchConfig {
        if (json == null) return EmulatorLaunchConfig()
        // Keep enabled-but-invalid settings enabled; launching must fail instead of falling back.
        return EmulatorLaunchConfig(
            enabled = json.optBoolean("enabled", false),
            activityName = json.optString("activityName", ""),
            action = json.optString("action", "android.intent.action.VIEW"),
            mimeType = json.optString("mimeType", ""),
            dataTemplate = json.optString("dataTemplate", "{romUri}"),
            extrasJson = json.optString("extrasJson", "{}")
        )
    }
}
