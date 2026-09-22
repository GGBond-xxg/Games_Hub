package com.bond.md3elauncher.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.bond.md3elauncher.BuildConfig
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.system.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun UpdateSettingsEntry() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(I18n.t(context, "update.version", "Current version") + "  ${BuildConfig.VERSION_NAME}", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) UpdateDialog { open = false }
}

@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember(context) { GitHubUpdater(context) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var checkAttempt by remember { mutableIntStateOf(0) }
    fun label(key: String) = I18n.t(context, key, key)
    fun installPending() {
        pendingPath?.let { path ->
            try { updater.install(File(path)); error = null }
            catch (e: Exception) { error = (e as? UpdateFailure)?.key ?: "update.install_failed" }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) installPending()
        else error = "update.permission"
    }
    fun requestInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            error = "update.permission"
            try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { error = "update.install_failed" }
        } else installPending()
    }
    LaunchedEffect(checkAttempt) {
        checking = true
        error = null
        try { release = updater.check() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            error = (e as? UpdateFailure)?.key ?: "update.network_error"
        } finally { checking = false }
    }
    val newer = release?.let { ReleaseParser.newer(it.version, BuildConfig.VERSION_NAME) } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label("update.title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    checking -> { Text(label("update.checking")); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    downloading -> { Text(label("update.downloading") + " $progress%"); LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) }
                    error != null -> Text(label(error!!))
                    newer -> {
                        Text(label("update.available") + " ${release!!.version}")
                        if (release!!.asset == null) Text(label("update.no_asset"))
                        Text(release!!.notes)
                    }
                    release != null -> Text(label("update.latest") + " ${BuildConfig.VERSION_NAME}")
                }
            }
        },
        confirmButton = {
            if (!checking && !downloading) {
                when {
                    pendingPath != null -> TextButton(onClick = { requestInstall() }) { Text(label("update.install"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    newer && release?.asset != null -> TextButton(onClick = {
                        downloading = true
                        error = null
                        scope.launch {
                            try {
                                val file = updater.download(release!!.asset!!) { value -> scope.launch { progress = value } }
                                pendingPath = file.absolutePath
                                requestInstall()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                error = (e as? UpdateFailure)?.key ?: "update.network_error"
                            } finally { downloading = false }
                        }
                    }) { Text(label("update.download"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    error != null -> TextButton(onClick = { checkAttempt++ }) { Text(label("update.retry"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                TextButton(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.page ?: ReleaseParser.PAGE))) }
                    catch (_: Exception) { error = "update.network_error" }
                }) { Text(label("update.github"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(label("common.cancel"), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
}
