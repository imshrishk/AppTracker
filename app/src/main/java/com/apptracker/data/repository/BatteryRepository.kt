package com.apptracker.data.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.entity.BatteryHistoryEntity
import com.apptracker.data.model.BatteryUsageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryHistoryDao: BatteryHistoryDao
) {
    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    suspend fun getBatteryUsage(
        packageName: String,
        startTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
        endTime: Long = System.currentTimeMillis()
    ): BatteryUsageInfo? = withContext(Dispatchers.IO) {
        val stats = getUsageStatsForPackage(packageName, startTime, endTime)
            ?: return@withContext null

        val isOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true

        BatteryUsageInfo(
            packageName = packageName,
            totalBatteryPercent = 0.0, // Requires BatteryStatsManager (API 31+)
            foregroundTimeMs = stats.totalTimeInForeground,
            backgroundTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stats.totalTimeForegroundServiceUsed
            } else 0L,
            foregroundServiceTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stats.totalTimeForegroundServiceUsed
            } else 0L,
            visibleTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stats.totalTimeVisible
            } else stats.totalTimeInForeground,
            cachedTimeMs = 0L,
            lastTimeUsed = stats.lastTimeUsed,
            wakelockTimeMs = 0L,
            alarmWakeups = 0,
            cpuForegroundMs = stats.totalTimeInForeground,
            cpuBackgroundMs = 0L,
            wifiUsageMah = 0.0,
            gpsUsageMah = 0.0,
            screenOnDrainMah = 0.0,
            screenOffDrainMah = 0.0,
            isBatteryOptimized = isOptimized,
            isDozeWhitelisted = !isOptimized,
            isBackgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isBackgroundRestricted(packageName)
            } else false
        )
    }

    suspend fun getAllBatteryUsage(
        startTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    ): Map<String, BatteryUsageInfo> = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

        statsMap.mapNotNull { (packageName, stats) ->
            if (stats.totalTimeInForeground > 0 || hasBackgroundUsage(stats)) {
                val isOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    !powerManager.isIgnoringBatteryOptimizations(packageName)
                } else true

                packageName to BatteryUsageInfo(
                    packageName = packageName,
                    totalBatteryPercent = 0.0,
                    foregroundTimeMs = stats.totalTimeInForeground,
                    backgroundTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stats.totalTimeForegroundServiceUsed
                    } else 0L,
                    foregroundServiceTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stats.totalTimeForegroundServiceUsed
                    } else 0L,
                    visibleTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stats.totalTimeVisible
                    } else stats.totalTimeInForeground,
                    cachedTimeMs = 0L,
                    lastTimeUsed = stats.lastTimeUsed,
                    wakelockTimeMs = 0L,
                    alarmWakeups = 0,
                    cpuForegroundMs = stats.totalTimeInForeground,
                    cpuBackgroundMs = 0L,
                    wifiUsageMah = 0.0,
                    gpsUsageMah = 0.0,
                    screenOnDrainMah = 0.0,
                    screenOffDrainMah = 0.0,
                    isBatteryOptimized = isOptimized,
                    isDozeWhitelisted = !isOptimized,
                    isBackgroundRestricted = false
                )
            } else null
        }.toMap()
    }

    suspend fun saveBatterySnapshot(usages: Map<String, BatteryUsageInfo>) {
        val entities = usages.map { (_, usage) ->
            BatteryHistoryEntity(
                packageName = usage.packageName,
                batteryPercent = usage.totalBatteryPercent,
                foregroundTimeMs = usage.foregroundTimeMs,
                backgroundTimeMs = usage.backgroundTimeMs,
                foregroundServiceTimeMs = usage.foregroundServiceTimeMs,
                wakelockTimeMs = usage.wakelockTimeMs,
                alarmWakeups = usage.alarmWakeups
            )
        }
        batteryHistoryDao.insertAll(entities)
    }

    fun getBatteryHistory(packageName: String): Flow<List<BatteryHistoryEntity>> =
        batteryHistoryDao.getHistoryForPackage(packageName)

    private fun getUsageStatsForPackage(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): UsageStats? {
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        return stats[packageName]
    }

    private fun hasBackgroundUsage(stats: UsageStats): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stats.totalTimeForegroundServiceUsed > 0
        } else false
    }

    private fun isBackgroundRestricted(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                activityManager.isBackgroundRestricted
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
