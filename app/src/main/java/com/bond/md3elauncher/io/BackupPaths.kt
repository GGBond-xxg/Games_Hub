package com.bond.md3elauncher.io

internal object BackupPaths {
    const val MAX_BYTES = 512L * 1024 * 1024
    const val MAX_FILE_BYTES = 64L * 1024 * 1024
    const val MAX_ENTRIES = 10000
    private val runtimes = setOf("internal_gba", "internal_fc", "internal_sfc", "internal_md", "internal_ps1", "internal_n64", "internal_arcade")

    fun allowed(path: String): Boolean {
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || '\\' in it || ':' in it }) return false
        return when {
            parts.size == 2 && parts[0] in setOf("custom_icons", "psp_meta") -> parts[1].endsWith(".png")
            parts.size == 4 && parts[0] in runtimes && parts[1] == "game_data" ->
                parts[3] == "save_ram.srm" || parts[3] == "quick.state" || parts[3].matches(Regex("slot_[1-5]\\.state"))
            else -> false
        }
    }
}
