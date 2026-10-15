package com.bond.md3elauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.data.GameMetadata
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.system.BatchScrapeState

@Composable
internal fun GameInformation(metadata: GameMetadata) {
    if (metadata.isEmpty) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("metadata.release_date" to metadata.releaseDate, "metadata.genres" to metadata.genres,
            "metadata.developer" to metadata.developer, "metadata.publisher" to metadata.publisher).forEach { (key, value) ->
            if (value.isNotBlank()) Text("${I18n.t(context, key, key)}: $value", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun GameInfoEditor(metadata: GameMetadata, onDismiss: () -> Unit, onSave: (GameMetadata) -> Unit) {
    val context = LocalContext.current
    fun t(key: String) = I18n.t(context, key, key)
    var date by rememberSaveable { mutableStateOf(metadata.releaseDate) }
    var genres by rememberSaveable { mutableStateOf(metadata.genres) }
    var developer by rememberSaveable { mutableStateOf(metadata.developer) }
    var publisher by rememberSaveable { mutableStateOf(metadata.publisher) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HubSurface(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).heightIn(max = 560.dp).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("metadata.edit"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataField(t("metadata.release_date"), date) { date = it.take(32) }
                    MetadataField(t("metadata.genres"), genres) { genres = it.take(256) }
                    MetadataField(t("metadata.developer"), developer) { developer = it.take(256) }
                    MetadataField(t("metadata.publisher"), publisher) { publisher = it.take(256) }
                    Text(t("metadata.help"), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    HubTextButton(onClick = onDismiss) { Text(t("common.cancel"), maxLines = 1) }
                    HubButton(onClick = { onSave(GameMetadata(date, genres, developer, publisher).cleaned()); onDismiss() }) { Text(t("common.save"), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun MetadataField(label: String, value: String, change: (String) -> Unit) {
    HubOutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true)
}

@Composable
internal fun BatchScrapePanel(platform: PlatformConfig, state: BatchScrapeState, onStart: () -> Unit, onCancel: () -> Unit, scanning: Boolean) {
    val context = LocalContext.current
    fun t(key: String) = I18n.t(context, key, key)
    var confirm by rememberSaveable(platform.id) { mutableStateOf(false) }
    val ownState = state.platformId == platform.id
    HubOutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("batch.title"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t("batch.help"), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (ownState) {
                Text(t(state.messageKey), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(I18n.t(context, "batch.progress", "{done}/{total} · Updated {updated} · No match {missing} · Failed {failed}",
                    "done" to state.completed, "total" to state.total, "updated" to state.updated, "missing" to state.unmatched, "failed" to state.failed),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.running) {
                    LinearProgressIndicator(progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    Text(state.currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.running && ownState) {
                HubTextButton(onClick = onCancel, enabled = !state.cancelling) { Text(t("common.cancel"), maxLines = 1) }
            } else {
                HubButton(onClick = { confirm = true }, enabled = !state.running && !scanning && platform.gameCount > 0) {
                    Text(t("batch.start"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (confirm) HubAlertDialog(onDismissRequest = { confirm = false },
        title = { Text(t("batch.title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Text(t("batch.confirm"), maxLines = 8, overflow = TextOverflow.Ellipsis) },
        confirmButton = { HubTextButton(onClick = { confirm = false; onStart() }) { Text(t("batch.start"), maxLines = 1) } },
        dismissButton = { HubTextButton(onClick = { confirm = false }) { Text(t("common.cancel"), maxLines = 1) } })
}
