package com.bond.md3elauncher.ui

import android.content.Context
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.i18n.I18n

internal enum class BeaconTab(val label: String) {
    NOW("★"),
    ANDROID("Android"),
    NS("NS"),
    PSP("PSP"),
    GBA("GBA"),
    GB("GB"),
    NES("FC"),
    SETTINGS("Settings")
}


internal fun BeaconTab.localizedLabel(context: Context): String = when (this) {
    BeaconTab.NOW -> "★"
    BeaconTab.ANDROID -> I18n.t(context, "tab.android", "安卓")
    BeaconTab.NS -> "NS"
    BeaconTab.PSP -> "PSP"
    BeaconTab.GBA -> "GBA"
    BeaconTab.GB -> "GB"
    BeaconTab.NES -> "FC"
    BeaconTab.SETTINGS -> I18n.t(context, "tab.settings", "设置")
}

internal val emulatorTabsDefault: List<BeaconTab> = listOf(BeaconTab.PSP, BeaconTab.NS, BeaconTab.GBA, BeaconTab.GB, BeaconTab.NES)

internal val sortableTabs: List<BeaconTab> = emulatorTabsDefault + BeaconTab.ANDROID

internal fun normalizedTabOrder(raw: List<String>): List<BeaconTab> {
    val parsed = raw.mapNotNull { key -> runCatching { BeaconTab.valueOf(key) }.getOrNull() }
        .filter { it in sortableTabs }
        .distinct()
    val androidIndex = parsed.indexOf(BeaconTab.ANDROID)
    val firstEmulatorIndex = parsed.indexOfFirst { it in emulatorTabsDefault }
    val androidFirst = androidIndex >= 0 && firstEmulatorIndex >= 0 && androidIndex < firstEmulatorIndex
    val emulators = (parsed.filter { it in emulatorTabsDefault } + emulatorTabsDefault.filter { it !in parsed }).distinct()
    return if (androidFirst) {
        listOf(BeaconTab.ANDROID) + emulators
    } else {
        emulators + BeaconTab.ANDROID
    }
}

internal fun tabOrderKeys(tabs: List<BeaconTab>): List<String> = tabs.filter { it in sortableTabs }.map { it.name }

internal data class FavoriteEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val typeLabel: String,
    val game: GameItem? = null,
    val app: InstalledApp? = null
)

internal data class EditTarget(
    val key: String,
    val defaultTitle: String,
    val currentTitle: String,
    val currentImagePath: String?,
    val typeLabel: String
)

internal fun nextTab(tab: BeaconTab, visibleTabs: List<BeaconTab>): BeaconTab {
    if (visibleTabs.isEmpty()) return BeaconTab.NOW
    val index = visibleTabs.indexOf(tab).takeIf { it >= 0 } ?: 0
    return visibleTabs[(index + 1) % visibleTabs.size]
}

internal fun previousTab(tab: BeaconTab, visibleTabs: List<BeaconTab>): BeaconTab {
    if (visibleTabs.isEmpty()) return BeaconTab.NOW
    val index = visibleTabs.indexOf(tab).takeIf { it >= 0 } ?: 0
    return visibleTabs[(index - 1 + visibleTabs.size) % visibleTabs.size]
}
