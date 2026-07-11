package com.bond.md3elauncher.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.bond.md3elauncher.data.InstalledApp
import com.bond.md3elauncher.emulator.fc.FcExternalEmulatorProfiles
import java.util.Locale

class AndroidAppRepository(private val context: Context) {
    fun loadLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val result = linkedMapOf<String, InstalledApp>()

        // Important: Do NOT use MATCH_DEFAULT_ONLY here.
        // Many real launcher entries only declare MAIN + LAUNCHER, not DEFAULT.
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryLauncherActivities(pm, launcherIntent).forEach { resolveInfo ->
            toInstalledApp(pm, resolveInfo)?.let { app ->
                if (app.packageName != context.packageName) result[app.packageName] = app
            }
        }

        // Fallback: if QUERY_ALL_PACKAGES is granted/allowed, this catches launchable packages
        // that some vendors hide from queryIntentActivities.
        getInstalledApplicationsCompat(pm).forEach { appInfo ->
            val packageName = appInfo.packageName ?: return@forEach
            if (packageName == context.packageName || result.containsKey(packageName)) return@forEach
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return@forEach
            val label = appInfo.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
            result[packageName] = InstalledApp(
                label = label,
                packageName = packageName,
                isGame = isGame(appInfo),
                isLikelyEmulator = isLikelyEmulator(label, packageName) || isLikelyEmulator("", launchIntent.`package`.orEmpty())
            )
        }

        return result.values
            .sortedWith(
                compareByDescending<InstalledApp> { it.isLikelyEmulator }
                    .thenByDescending { it.isGame }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
            )
    }

    private fun toInstalledApp(pm: PackageManager, resolveInfo: ResolveInfo): InstalledApp? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val packageName = activityInfo.packageName ?: return null
        val label = resolveInfo.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName }
        val appInfo = activityInfo.applicationInfo
        return InstalledApp(
            label = label,
            packageName = packageName,
            isGame = isGame(appInfo),
            isLikelyEmulator = isLikelyEmulator(label, packageName)
        )
    }

    private fun isGame(appInfo: ApplicationInfo?): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            appInfo?.category == ApplicationInfo.CATEGORY_GAME
    }

    private fun isLikelyEmulator(label: String, packageName: String): Boolean {
        val text = (label + " " + packageName).lowercase(Locale.ROOT)
        return listOf(
            "ppsspp",
            "retroarch",
            "aethersx2",
            "nethersx2",
            "dolphin",
            "citra",
            "yuzu",
            "suyu",
            "sudachi",
            "skyline",
            "eden",
            "citron",
            "strato",
            "egg ns",
            "my boy",
            "myboy",
            "pizza boy",
            "pizzaboy",
            "john gba",
            "johngba",
            "johnemulators",
            "gba.emu",
            "gbaemu",
            "nostalgia.gba",
            "nostalgiaemulators",
            "game boy advance",
            "gba",
            "mgba",
            "fastemulator",
            "com.fastemulator.gba",
            "rocket psp",
            "rocketpsp",
            "enjoy psp",
            "enjoypsp",
            "mypsp",
            "retroarch",
            "com.retroarch",
            "emulator",
            "emu"
        ).plus(FcExternalEmulatorProfiles.recommendedKeywords).any { it in text }
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplicationsCompat(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
    }
}
