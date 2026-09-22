package com.bond.md3elauncher.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.BuildConfig
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.io.LauncherBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val sponsorshipAddresses = listOf(
    "Solana (SOL)" to "GseMb4yCgfhyMvkA7jP6QhMe4e5nMPnxgJqqrA8Aq7eJ",
    "Ethereum (ETH / ERC-20)" to "0xcB2f6fc5eF905e89cDeE7F2eB59faD9A93043324",
    "TON" to "UQATTF8wVv_Q8x42OYyDOUcM1Ti0HA-VdZ0b5zUd78_lEYqj",
    "TRON (TRX / TRC-20)" to "TXDFyQKRSLt6dmbs3tcJbEJbcHsokbgKSn",
    "Sui (SUI)" to "0xd61db83d28fc0da34e55b9488d3927fc5b6515cc96c6109c0f8ed451980af4bb",
    "Bitcoin (BTC)" to "1BhMBUVLySJg3qNgFNxYPFMGbcd4KFxkrK",
    "Dogecoin (DOGE)" to "DEJ6MMqAjX55YfXXtFQVeYrCbadntYwZCs"
)

@Composable
private fun translated(key: String): String = I18n.t(LocalContext.current, key, key)

@Composable
internal fun AboutAndBackupSettings(isScanning: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    fun runBackup(uri: Uri, restore: Boolean) {
        if (busy || isScanning) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val backup = LauncherBackup(context)
                    if (restore) backup.restore(uri) else backup.export(uri)
                }
                if (restore) {
                    clearLauncherImageCache()
                    I18n.setLanguageOverride(context, com.bond.md3elauncher.data.LauncherStore(context).loadLanguageMode())
                }
                Toast.makeText(context, I18n.t(context, if (restore) "settings.backup.restored" else "settings.backup.saved", "Completed"), Toast.LENGTH_LONG).show()
                if (restore) (context as? Activity)?.recreate()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(context, I18n.t(context, "settings.backup.failed", "Backup failed"), Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runBackup(uri, false)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri?.toString() }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { showBackup = true }) { Text(translated("settings.backup.title"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            TextButton(onClick = { showAbout = true }) { Text(translated("settings.about.title"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            UpdateSettingsEntry()
        }
    }
    if (showBackup) AlertDialog(
        onDismissRequest = { showBackup = false },
        title = { Text(translated("settings.backup.title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Text(translated("settings.backup.description"), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = { showBackup = false; export.launch("GameHub-backup.zip") }, enabled = !busy && !isScanning) {
                Text(translated("settings.backup.export"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { showBackup = false; restore.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy && !isScanning) {
                Text(translated("settings.backup.restore"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = { TextButton(onClick = { showBackup = false }) { Text(translated("common.cancel"), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
    if (restoreUri != null) AlertDialog(
        onDismissRequest = { restoreUri = null },
        title = { Text(translated("settings.backup.restore"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Text(translated("settings.backup.confirm"), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = {
            val uri = restoreUri
            restoreUri = null
            uri?.let { runBackup(Uri.parse(it), true) }
        }) { Text(translated("settings.backup.restore"), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(translated("common.cancel"), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
    if (busy) AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(translated("settings.backup.working"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = {}
    )
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var document by remember { mutableStateOf<String?>(null) }
    var documentText by remember { mutableStateOf("") }
    LaunchedEffect(document) {
        documentText = ""
        document?.let { name -> documentText = withContext(Dispatchers.IO) { context.assets.open("legal/$name").bufferedReader().use { it.readText() } } }
    }
    Dialog(onDismissRequest = { if (document != null) document = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 840.dp).fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(translated("settings.about.title"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { if (document != null) document = null else onDismiss() }) {
                        Text(translated("common.back"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                key(document) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (document != null) {
                            SelectionContainer { Text(documentText) }
                        } else {
                            Text("GameHub ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(translated("settings.about.story"))
                            Text(translated("settings.about.license"))
                            TextButton(onClick = { document = "LICENSE" }) { Text(translated("settings.about.license_button"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            TextButton(onClick = { document = "THIRD_PARTY_NOTICES.md" }) { Text(translated("settings.about.notices"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            TextButton(onClick = { document = "DISCLAIMER.md" }) { Text(translated("settings.about.disclaimer"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/GGBond-xxg/Games_Hub"))) }
                            }) { Text(translated("settings.about.project"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Text(translated("settings.about.sponsor"), style = MaterialTheme.typography.titleMedium)
                            Text(translated("settings.about.sponsor_description"))
                            sponsorshipAddresses.forEach { (network, address) ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(network, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        SelectionContainer { Text(address) }
                                        TextButton(onClick = {
                                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(network, address))
                                            Toast.makeText(context, I18n.t(context, "settings.about.copied", "Address copied"), Toast.LENGTH_SHORT).show()
                                        }) { Text(translated("settings.about.copy"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
