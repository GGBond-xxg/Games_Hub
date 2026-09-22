package com.bond.md3elauncher.io

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Extract only into staging. Callers must validate the manifest before installing files. */
internal object BackupArchive {
    fun extract(input: InputStream, stage: File, maxBytes: Long = BackupPaths.MAX_BYTES) {
        var total = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(seen.add(entry.name) && seen.size <= BackupPaths.MAX_ENTRIES && !entry.isDirectory)
                val relative = if (entry.name == "manifest.json") "manifest.json" else {
                    require(entry.name.startsWith("files/"))
                    entry.name.removePrefix("files/").also { require(BackupPaths.allowed(it)) }
                }
                val file = File(stage, relative)
                require(file.canonicalPath.startsWith(stage.canonicalPath + File.separator))
                file.parentFile?.mkdirs()
                var size = 0L
                file.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count
                        total += count
                        require(size <= (if (relative == "manifest.json") 16L * 1024 * 1024 else BackupPaths.MAX_FILE_BYTES) && total <= maxBytes)
                        out.write(buffer, 0, count)
                    }
                }
                if (entry.time > 0) file.setLastModified(entry.time)
                zip.closeEntry()
            }
        }
        require("manifest.json" in seen) { "Missing GameHub manifest" }
    }
}
