package com.keymouse.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.keymouse.launcher.KeyMouseApp
import com.keymouse.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
    private val sessionRecentPackages = LinkedHashSet<String>()

    companion object {
        const val PREF_FAVORITES = "pref_favorites_list"
        const val PREF_DOCK = "pref_dock_list"

        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        val favSet = getFavoritePackages().toSet()
        val dockSet = getDockPackages().toSet()

        val list = mutableListOf<AppInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue

            val label = info.loadLabel(packageManager).toString()
            val icon = info.loadIcon(packageManager)
            val activity = info.activityInfo.name
            val isFav = favSet.contains(pkg)
            val isDock = dockSet.contains(pkg)

            list.add(AppInfo(label, pkg, activity, icon, isFavorite = isFav, isDocked = isDock))
        }

        list.sortBy { it.label.lowercase() }
        list
    }

    // --- Pinned Favorites (Ordered List) ---
    fun getFavoritePackages(): MutableList<String> {
        val raw = prefs.getString(PREF_FAVORITES, null)
        if (raw != null) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        }
        return mutableListOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "com.instagram.android"
        )
    }

    fun saveFavoritePackages(list: List<String>) {
        prefs.edit().putString(PREF_FAVORITES, list.joinToString(",")).apply()
    }

    fun setFavorite(packageName: String, favorite: Boolean) {
        val list = getFavoritePackages()
        if (favorite) {
            if (!list.contains(packageName)) list.add(packageName)
        } else {
            list.remove(packageName)
        }
        saveFavoritePackages(list)
    }

    // --- Bottom Dock Apps (Ordered List) ---
    fun getDockPackages(): MutableList<String> {
        val raw = prefs.getString(PREF_DOCK, null)
        if (raw != null) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        }
        return mutableListOf(
            "com.google.android.dialer",
            "com.android.contacts",
            "com.android.chrome",
            "com.google.android.apps.messaging",
            "com.whatsapp",
            "com.android.camera"
        )
    }

    fun saveDockPackages(list: List<String>) {
        prefs.edit().putString(PREF_DOCK, list.joinToString(",")).apply()
    }

    fun setDock(packageName: String, docked: Boolean) {
        val list = getDockPackages()
        if (docked) {
            if (!list.contains(packageName)) list.add(packageName)
        } else {
            list.remove(packageName)
        }
        saveDockPackages(list)
    }

    fun recordAppLaunch(packageName: String) {
        sessionRecentPackages.remove(packageName)
        sessionRecentPackages.add(packageName)
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Retrieves actual recently active background applications in chronological order
     */
    suspend fun getRecentApps(installedApps: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        val appsByPackage = installedApps.associateBy { it.packageName }
        val recentList = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        // 1. Session recently launched apps first (most recent)
        for (pkg in sessionRecentPackages.reversed()) {
            appsByPackage[pkg]?.let { app ->
                if (seenPackages.add(app.packageName)) {
                    recentList.add(app)
                }
            }
        }

        // 2. Query UsageEvents to get real background active apps from Android
        if (hasUsageStatsPermission()) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (1000 * 60 * 60 * 12) // Past 12 hours

                try {
                    val events = usageStatsManager.queryEvents(startTime, endTime)
                    val event = UsageEvents.Event()
                    val lastResumedMap = LinkedHashMap<String, Long>()

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val type = event.eventType
                        if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                            type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        ) {
                            val pkg = event.packageName
                            if (pkg != context.packageName && appsByPackage.containsKey(pkg)) {
                                lastResumedMap[pkg] = event.timeStamp
                            }
                        }
                    }

                    // Sort descending by timestamp
                    val sorted = lastResumedMap.entries.sortedByDescending { it.value }
                    for (entry in sorted) {
                        appsByPackage[entry.key]?.let { app ->
                            if (seenPackages.add(app.packageName)) {
                                recentList.add(app.copy(lastTimeUsed = entry.value))
                            }
                        }
                        if (recentList.size >= 12) break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // If queryEvents was empty, fall back to queryUsageStats
                if (recentList.isEmpty()) {
                    try {
                        val stats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_BEST,
                            startTime,
                            endTime
                        )
                        if (!stats.isNullOrEmpty()) {
                            val sortedStats = stats
                                .filter { it.packageName != context.packageName && appsByPackage.containsKey(it.packageName) && it.lastTimeUsed > 0 }
                                .sortedByDescending { it.lastTimeUsed }

                            for (stat in sortedStats) {
                                appsByPackage[stat.packageName]?.let { app ->
                                    if (seenPackages.add(app.packageName)) {
                                        recentList.add(app.copy(lastTimeUsed = stat.lastTimeUsed))
                                    }
                                }
                                if (recentList.size >= 12) break
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // If completely empty (e.g. freshly installed or no permissions yet), supply favorite apps
        if (recentList.isEmpty()) {
            val favs = getFavoritePackages().mapNotNull { appsByPackage[it] }
            for (fav in favs) {
                if (seenPackages.add(fav.packageName)) {
                    recentList.add(fav)
                }
            }
        }

        // Assign keyboard shortcut slots (1, 2, 3... 9)
        recentList.forEachIndexed { index, app ->
            app.shortcutSlot = if (index < 9) index + 1 else null
        }

        recentList.take(12)
    }

    fun launchApp(packageName: String) {
        recordAppLaunch(packageName)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}
