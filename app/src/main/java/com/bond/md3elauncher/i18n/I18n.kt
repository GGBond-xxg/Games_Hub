package com.bond.md3elauncher.i18n

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON based text provider.
 *
 * Language rule:
 * - Default locale is English.
 * - Device Simplified Chinese uses assets/i18n/zh.json.
 * - Device Traditional Chinese uses assets/i18n/zh-Hant.json.
 * - Every other device language falls back to English until that locale is added.
 *
 * Text rule: all new user-facing text must be added to JSON first, then referenced
 * by key through I18n.t(...). UI text must also set maxLines / overflow when the
 * copy may become longer after localization.
 */
internal object I18n {
    private const val TAG = "GameHub_I18N"
    private const val PREFS_NAME = "md3e_launcher_store"
    private const val KEY_LANGUAGE_MODE = "language_mode"
    private const val LANGUAGE_MODE_FILE = "i18n_language_mode.txt"
    const val LANG_SYSTEM = "system"
    const val DEFAULT_LANG = "en"
    const val SIMPLIFIED_ZH = "zh"
    const val TRADITIONAL_ZH = "zh-Hant"
    val SUPPORTED_LANGUAGE_MODES = listOf(LANG_SYSTEM, DEFAULT_LANG, SIMPLIFIED_ZH, TRADITIONAL_ZH)
    private val cache = ConcurrentHashMap<String, JSONObject>()
    private val languageRevision = mutableIntStateOf(0)

    /**
     * Read by composables through I18n.t()/languageFor() so changing language
     * invalidates current UI immediately instead of requiring an app restart.
     */
    fun observeLanguageRevision(): Int = languageRevision.intValue

    fun savedLanguageMode(context: Context): String {
        // v0.1.85：内置模拟器运行在 :internal_gba / :internal_fc 独立进程。
        // Android SharedPreferences 在多进程场景下可能被旧进程缓存，导致主进程切换语言后，
        // 重新进入仍显示旧语言。这里先读一个小文件作为跨进程语言源，再回退 SharedPreferences。
        val fileValue = runCatching {
            File(context.filesDir, LANGUAGE_MODE_FILE).takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim()
        }.getOrNull()
        val raw = fileValue
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_MODE, LANG_SYSTEM)
            ?: LANG_SYSTEM
        return raw.takeIf { it in SUPPORTED_LANGUAGE_MODES } ?: LANG_SYSTEM
    }

    fun setLanguageOverride(context: Context, mode: String) {
        val clean = mode.takeIf { it in SUPPORTED_LANGUAGE_MODES } ?: LANG_SYSTEM
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_MODE, clean)
            .apply()
        runCatching {
            File(context.filesDir, LANGUAGE_MODE_FILE).writeText(clean, Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "write language override file failed", it)
        }
        languageRevision.intValue += 1
    }

    fun languageFor(context: Context): String {
        observeLanguageRevision()
        return when (val mode = savedLanguageMode(context)) {
            DEFAULT_LANG, SIMPLIFIED_ZH, TRADITIONAL_ZH -> mode
            else -> deviceLanguageFor(context)
        }
    }

    fun isChinese(context: Context): Boolean = languageFor(context) == SIMPLIFIED_ZH || languageFor(context) == TRADITIONAL_ZH

    fun displayNameForMode(context: Context, mode: String): String = when (mode) {
        LANG_SYSTEM -> t(context, "settings.language.system", "System")
        DEFAULT_LANG -> "English"
        SIMPLIFIED_ZH -> "简体中文"
        TRADITIONAL_ZH -> "繁體中文"
        else -> mode
    }

    private fun deviceLanguageFor(context: Context): String {
        val tag = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].toLanguageTag()
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.toLanguageTag()
            }
        }.getOrNull() ?: Locale.getDefault().toLanguageTag()
        val normalized = tag.lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("zh-hant") ||
                normalized.startsWith("zh-tw") ||
                normalized.startsWith("zh-hk") ||
                normalized.startsWith("zh-mo") -> TRADITIONAL_ZH
            normalized.startsWith("zh") -> SIMPLIFIED_ZH
            else -> DEFAULT_LANG
        }
    }

    fun t(
        context: Context,
        key: String,
        fallback: String = key,
        vararg args: Pair<String, Any?>
    ): String {
        val lang = languageFor(context)
        val raw = get(context, lang, key)
            ?: get(context, DEFAULT_LANG, key)
            ?: get(context, SIMPLIFIED_ZH, key)
            ?: fallback
        if (args.isEmpty()) return raw
        var result = raw
        args.forEach { (name, value) ->
            result = result.replace("{$name}", value?.toString().orEmpty())
        }
        return result
    }

    fun short(
        context: Context,
        key: String,
        fallback: String = key,
        maxChars: Int = 10,
        vararg args: Pair<String, Any?>
    ): String = ellipsize(t(context, key, fallback, *args), maxChars)

    fun ellipsize(value: String, maxChars: Int): String {
        if (maxChars <= 0) return "…"
        val clean = value.replace('\n', ' ').trim()
        return if (clean.length <= maxChars) clean else clean.take((maxChars - 1).coerceAtLeast(1)) + "…"
    }

    private fun get(context: Context, lang: String, key: String): String? {
        val json = load(context, lang) ?: return null
        if (!json.has(key)) return null
        return json.optString(key).takeIf { it.isNotBlank() }
    }

    private fun load(context: Context, lang: String): JSONObject? {
        return cache.getOrPut(lang) {
            runCatching {
                context.assets.open("i18n/$lang.json").bufferedReader(Charsets.UTF_8).use { reader ->
                    JSONObject(reader.readText())
                }
            }.onFailure {
                Log.w(TAG, "load locale failed: $lang", it)
            }.getOrElse { JSONObject() }
        }.takeIf { it.length() > 0 }
    }
}

@Composable
internal fun rememberT(key: String, fallback: String = key, vararg args: Pair<String, Any?>): String {
    return I18n.t(LocalContext.current, key, fallback, *args)
}

@Composable
internal fun I18nText(
    key: String,
    fallback: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    vararg args: Pair<String, Any?>
) {
    Text(
        text = I18n.t(LocalContext.current, key, fallback, *args),
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}
