package com.bond.md3elauncher.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.md3elauncher.data.EmulatorLaunchConfig
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.system.CustomLaunchConfig

@Composable
internal fun CustomLaunchSettings(
    platform: PlatformConfig,
    onDismiss: () -> Unit,
    onSave: (EmulatorLaunchConfig) -> Unit
) {
    val context = LocalContext.current
    fun t(key: String) = I18n.t(context, key, key)
    var enabled by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.enabled) }
    var activity by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.activityName) }
    var action by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.action) }
    var mime by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.mimeType) }
    var data by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.dataTemplate) }
    var extras by rememberSaveable(platform.id) { mutableStateOf(platform.launchConfig.extrasJson) }
    var chooseActivity by remember { mutableStateOf(false) }
    val draft = EmulatorLaunchConfig(enabled, activity.trim(), action.trim(), mime.trim(), data.trim(), extras.trim())
    val error = if (enabled) CustomLaunchConfig.validationError(draft) else null
    @Suppress("DEPRECATION")
    val activities = remember(platform.emulatorPackage) {
        runCatching {
            context.packageManager.getPackageInfo(platform.emulatorPackage.orEmpty(), PackageManager.GET_ACTIVITIES)
                .activities.orEmpty().filter { it.exported && it.enabled }.map { it.name }.distinct().sorted()
        }.getOrDefault(emptyList())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HubSurface(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("launch.custom.title"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(platform.emulatorPackage.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("launch.custom.enable"), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        HubSwitch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Text(t("launch.custom.help"), style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    LaunchConfigField(t("launch.custom.activity"), activity, { activity = it }, enabled)
                    if (activities.isNotEmpty()) {
                        HubTextButton(onClick = { chooseActivity = true }, enabled = enabled) {
                            Text(t("launch.custom.choose_activity"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    LaunchConfigField(t("launch.custom.action"), action, { action = it }, enabled)
                    LaunchConfigField(t("launch.custom.mime"), mime, { mime = it }, enabled)
                    LaunchConfigField(t("launch.custom.data"), data, { data = it }, enabled)
                    LaunchConfigField(t("launch.custom.extras"), extras, { extras = it }, enabled, multiline = true)
                    Text(t("launch.custom.example"), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (error != null) Text(t(error), color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    HubTextButton(onClick = {
                        enabled = false; activity = ""; action = "android.intent.action.VIEW"; mime = ""; data = "{romUri}"; extras = "{}"
                    }, modifier = Modifier.weight(1f)) { Text(t("launch.custom.reset"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    HubTextButton(onClick = onDismiss) { Text(t("common.cancel"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    HubButton(onClick = { onSave(draft); onDismiss() }, enabled = error == null) {
                        Text(t("common.save"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (chooseActivity) {
        HubAlertDialog(
            onDismissRequest = { chooseActivity = false },
            title = { Text(t("launch.custom.choose_activity"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    activities.forEach { name ->
                        HubTextButton(onClick = { activity = name; chooseActivity = false }) {
                            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { HubTextButton(onClick = { chooseActivity = false }) { Text(t("common.cancel"), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }
}

@Composable
private fun LaunchConfigField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, multiline: Boolean = false) {
    HubOutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 6 else 1
    )
}
