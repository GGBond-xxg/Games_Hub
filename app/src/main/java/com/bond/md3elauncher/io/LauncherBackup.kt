package com.bond.md3elauncher.io

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable, versioned backups of user data only. No ROMs, BIOS or scraper credentials. */
class LauncherBackup(private val context: Context) {
    private val names = listOf("md3e_launcher_store", "controller_shortcut_settings", "internal_gba_settings")
    private val secrets = setOf("scraper_tgdb_key", "scraper_steamgrid_key", "scraper_screenscraper_user", "scraper_screenscraper_pass", "scraper_screenscraper_dev_id", "scraper_screenscraper_dev_pass")
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun requireEmulatorsClosed() {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        check(manager.runningAppProcesses.orEmpty().none {
            it.uid == android.os.Process.myUid() && it.processName.startsWith("${context.packageName}:internal_")
        }) { "Close the running internal emulator first" }
    }

    fun export(uri: Uri) {
        requireEmulatorsClosed()
        val metadata = JSONObject().put("format", "GameHub").put("version", 1)
            .put("filesRoot", context.filesDir.absolutePath)
        val preferences = JSONObject()
        names.forEach { name -> preferences.put(name, encode(prefs(name).all.filterKeys { it !in secrets })) }
        metadata.put("preferences", preferences)
        val files = context.filesDir.walkTopDown().filter { it.isFile && BackupPaths.allowed(it.relativeTo(context.filesDir).invariantSeparatorsPath) }.toList()
        require(files.size < BackupPaths.MAX_ENTRIES && files.all { it.length() <= BackupPaths.MAX_FILE_BYTES } && files.sumOf { it.length() } < BackupPaths.MAX_BYTES)
        val manifest = metadata.toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= 16 * 1024 * 1024 && files.sumOf { it.length() } + manifest.size <= BackupPaths.MAX_BYTES)
        ZipOutputStream(context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup destination")).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            files.forEach { file ->
                zip.putNextEntry(ZipEntry("files/" + file.relativeTo(context.filesDir).invariantSeparatorsPath).apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(uri: Uri) {
        requireEmulatorsClosed()
        val work = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val stage = File(work, "stage").apply { mkdirs() }
        val rollback = File(work, "rollback").apply { mkdirs() }
        val touched = mutableListOf<String>()
        val previous = names.associateWith { prefs(it).all.toMap() }
        var preferencesTouched = false
        try {
            BackupArchive.extract(context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open backup"), stage)
            val metadata = JSONObject(File(stage, "manifest.json").readText())
            require(metadata.getString("format") == "GameHub" && metadata.getInt("version") == 1)
            val oldRoot = metadata.getString("filesRoot")
            require(oldRoot.startsWith("/") && oldRoot.length > 1)
            val preferenceData = metadata.getJSONObject("preferences")
            val decoded = names.associateWith { name -> decode(preferenceData.getJSONObject(name)) }
            val launcher = decoded.getValue(names.first()).toMutableMap()
            launcher.forEach { (key, value) ->
                require(when (key) {
                    "favorites", "android_games" -> value is Set<*>
                    "home_prompt_done", "dynamic_color", "scraper_libretro" -> value is Boolean
                    "safe_margin_left", "safe_margin_right" -> value is Int
                    else -> value is String
                }) { "Invalid launcher preference type" }
            }
            // Paths differ between Android users/devices. Keep document URIs so matching
            // directory reauthorization can preserve game IDs, favorites and ordering.
            listOf("games", "games_previous_valid", "item_overrides").forEach { key ->
                (launcher[key] as? String)?.let { raw ->
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        listOf("coverPath", "backgroundPath", "previewImagePath", "gridImagePath", "imagePath").forEach { field ->
                            val path = item.optString(field)
                            if (path.startsWith("$oldRoot/")) {
                                val relative = path.removePrefix("$oldRoot/")
                                if (BackupPaths.allowed(relative)) item.put(field, File(context.filesDir, relative).absolutePath)
                                else item.put(field, JSONObject.NULL)
                            } else if (path.isNotEmpty() && path != "null") {
                                item.put(field, JSONObject.NULL)
                            }
                        }
                    }
                    launcher[key] = array.toString()
                }
            }
            // Parse all launcher containers before changing any live data.
            listOf("platforms", "games", "games_previous_valid", "item_overrides", "recent", "tab_order").forEach { key ->
                (launcher[key] as? String)?.let { JSONArray(it) }
            }
            (launcher["item_orders"] as? String)?.let { JSONObject(it) }
            (launcher["games"] as? String)?.let { raw ->
                val games = JSONArray(raw)
                for (i in 0 until games.length()) {
                    val game = games.getJSONObject(i)
                    listOf("id", "platformId", "platformTitle", "title", "fileName", "extension", "uri").forEach { game.getString(it) }
                }
            }
            (launcher["platforms"] as? String)?.let { raw ->
                val platforms = JSONArray(raw)
                for (i in 0 until platforms.length()) {
                    val platform = platforms.getJSONObject(i)
                    platform.getString("id")
                    com.bond.md3elauncher.data.PlatformKind.valueOf(platform.getString("kind"))
                }
            }
            val restored = decoded + (names.first() to launcher)
            val files = stage.walkTopDown().filter { it.isFile && it.name != "manifest.json" }.toList()
            files.forEach { file ->
                val relative = file.relativeTo(stage).invariantSeparatorsPath
                val target = File(context.filesDir, relative)
                require(target.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
                if (target.exists()) {
                    val old = File(rollback, relative)
                    old.parentFile?.mkdirs()
                    target.copyTo(old)
                    old.setLastModified(target.lastModified())
                }
                touched += relative
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
                target.setLastModified(file.lastModified())
            }
            preferencesTouched = true
            names.forEach { name ->
                val retainedSecrets = previous.getValue(name).filterKeys { it in secrets }
                write(prefs(name), restored.getValue(name).filterKeys { it !in secrets } + retainedSecrets)
            }
        } catch (error: Exception) {
            touched.asReversed().forEach { relative ->
                val old = File(rollback, relative)
                val target = File(context.filesDir, relative)
                if (old.exists()) {
                    old.copyTo(target, overwrite = true)
                    target.setLastModified(old.lastModified())
                } else target.delete()
            }
            if (preferencesTouched) names.forEach { write(prefs(it), previous.getValue(it)) }
            throw error
        } finally {
            work.deleteRecursively()
        }
    }

    private fun encode(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            val type = when (value) { is String -> "string"; is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is Set<*> -> "set"; else -> error("Unsupported preference") }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
        }
    }

    private fun decode(json: JSONObject): Map<String, Any> = buildMap {
        json.keys().forEach { key ->
            val entry = json.getJSONObject(key)
            put(key, when (entry.getString("type")) {
                "string" -> entry.getString("value")
                "boolean" -> entry.getBoolean("value")
                "int" -> entry.getInt("value")
                "long" -> entry.getLong("value")
                "float" -> entry.getDouble("value").toFloat()
                "set" -> entry.getJSONArray("value").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> error("Unsupported preference type")
            })
        }
    }

    private fun write(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported preference type")
            }
        }
        check(editor.commit()) { "Could not save restored settings" }
    }
}
