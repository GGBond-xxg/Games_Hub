package com.bond.md3elauncher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.bond.md3elauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class GitHubUpdater(private val context: Context) {
    suspend fun check(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = connect("https://api.github.com/repos/${ReleaseParser.REPO}/releases/latest")
        try {
            if (connection.responseCode == 403 || connection.responseCode == 429) throw UpdateFailure("update.rate_limit")
            if (connection.responseCode == 404) throw UpdateFailure("update.no_release")
            if (connection.responseCode != 200) throw IOException("Release request failed")
            val text = connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(out.length + count <= 2 * 1024 * 1024)
                    out.append(buffer, 0, count)
                }
                out.toString()
            }
            ReleaseParser.parse(text, Build.SUPPORTED_ABIS.toList())
        } finally { connection.disconnect() }
    }

    suspend fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File.createTempFile("update-", ".part", dir)
        val output = File(dir, partial.nameWithoutExtension + ".apk")
        val connection = downloadConnection(asset.url)
        try {
            if (connection.responseCode != 200) throw IOException("Download failed")
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgress = -1
            connection.inputStream.use { input -> partial.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(buffer)
                    if (size < 0) break
                    downloaded += size
                    require(downloaded <= asset.size)
                    digest.update(buffer, 0, size)
                    out.write(buffer, 0, size)
                    val progress = (downloaded * 100 / asset.size).toInt()
                    if (progress != lastProgress) { lastProgress = progress; onProgress(progress) }
                }
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (downloaded != asset.size || hash != asset.sha256) throw UpdateFailure("update.invalid_apk")
            validateApk(partial)
            if (output.exists()) check(output.delete())
            check(partial.renameTo(output))
            output
        } finally { connection.disconnect(); partial.delete() }
    }

    @Suppress("DEPRECATION")
    fun validateApk(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw UpdateFailure("update.invalid_apk")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val nextCode = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        val signers = if (Build.VERSION.SDK_INT >= 28) apk.signingInfo?.apkContentsSigners else apk.signatures
        val currentSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        if (apk.packageName != context.packageName || nextCode <= currentCode || signers.isNullOrEmpty() ||
            currentSigners.isNullOrEmpty() || signers.toSet() != currentSigners.toSet()) throw UpdateFailure("update.invalid_apk")
    }

    fun install(file: File) {
        validateApk(file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "GameHub/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    private fun downloadConnection(initial: String): HttpURLConnection {
        var url = URL(initial)
        repeat(6) {
            if (url.protocol != "https" || !(url.host == "github.com" || url.host.endsWith(".githubusercontent.com"))) throw IOException("Invalid download host")
            val connection = connect(url.toString())
            connection.setRequestProperty("Accept", "application/octet-stream")
            val code = try { connection.responseCode } catch (e: Exception) { connection.disconnect(); throw e }
            if (code !in listOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            url = URL(url, location ?: throw IOException("Missing redirect"))
        }
        throw IOException("Too many redirects")
    }
}

internal class UpdateFailure(val key: String) : IOException(key)
