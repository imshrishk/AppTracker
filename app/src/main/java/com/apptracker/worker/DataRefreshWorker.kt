package com.apptracker.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.BatteryHistoryEntity
import com.apptracker.data.db.entity.NetworkHistoryEntity
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.data.repository.PermissionRepository
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DataRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val permissionRepository: PermissionRepository,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val appOpsDao: AppOpsDao,
    private val batteryHistoryDao: BatteryHistoryDao,
    private val networkHistoryDao: NetworkHistoryDao,
    private val calculateRiskScore: CalculateRiskScoreUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val pm = applicationContext.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            // Snapshot battery usage
            val batteryMap = batteryRepository.getAllBatteryUsage()
            val batteryEntities = batteryMap.map { (pkg, battery) ->
                BatteryHistoryEntity(
                    packageName = pkg,
                    timestamp = now,
                    batteryPercent = battery.totalBatteryPercent,
                    foregroundTimeMs = battery.foregroundTimeMs,
                    backgroundTimeMs = battery.backgroundTimeMs,
                    foregroundServiceTimeMs = battery.foregroundServiceTimeMs,
                    wakelockTimeMs = battery.wakelockTimeMs,
                    alarmWakeups = battery.alarmWakeups
                )
            }
            if (batteryEntities.isNotEmpty()) {
                batteryHistoryDao.insertAll(batteryEntities)
            }

            // Snapshot network usage
            val networkMap = networkRepository.getAllNetworkUsage()
            val networkEntities = networkMap.map { (pkg, network) ->
                NetworkHistoryEntity(
                    packageName = pkg,
                    timestamp = now,
                    wifiRxBytes = network.wifiRxBytes,
                    wifiTxBytes = network.wifiTxBytes,
                    mobileRxBytes = network.mobileRxBytes,
                    mobileTxBytes = network.mobileTxBytes,
                    foregroundBytes = network.foregroundBytes,
                    backgroundBytes = network.backgroundBytes
                )
            }
            if (networkEntities.isNotEmpty()) {
                networkHistoryDao.insertAll(networkEntities)
            }

            // Snapshot App Ops
            val allOpsEntities = mutableListOf<AppOpsHistoryEntity>()
            for (pkgInfo in packages) {
                val appInfo = permissionRepository.getAppInfo(pkgInfo.packageName)
                if (appInfo != null) {
                    for (op in appInfo.appOpsEntries) {
                        allOpsEntities.add(
                            AppOpsHistoryEntity(
                                packageName = pkgInfo.packageName,
                                opName = op.opName,
                                opCode = op.opCode,
                                mode = op.mode.name,
                                lastAccessTime = op.lastAccessTime,
                                lastRejectTime = op.lastRejectTime,
                                duration = op.duration,
                                accessCount = op.accessCount,
                                rejectCount = op.rejectCount,
                                timestamp = now
                            )
                        )
                    }
                }
            }
            if (allOpsEntities.isNotEmpty()) {
                appOpsDao.insertAll(allOpsEntities)
            }

            // Cleanup old data (keep 90 days)
            val cutoff = now - TimeUnit.DAYS.toMillis(90)
            appOpsDao.deleteOlderThan(cutoff)
            batteryHistoryDao.deleteOlderThan(cutoff)
            networkHistoryDao.deleteOlderThan(cutoff)

            // Risk score alerts — notify for newly high-risk apps
            NotificationHelper.createChannels(applicationContext)
            val highRiskApps = packages.mapNotNull { pkgInfo ->
                permissionRepository.getAppInfo(pkgInfo.packageName)
            }.filter { appInfo ->
                val score = calculateRiskScore(appInfo)
                score.overallScore >= 70
            }
            highRiskApps.take(3).forEach { appInfo ->
                val score = calculateRiskScore(appInfo)
                NotificationHelper.sendHighRiskAlert(
                    context = applicationContext,
                    appName = appInfo.appName,
                    packageName = appInfo.packageName,
                    riskScore = score.overallScore
                )
            }
            NotificationHelper.sendRefreshSummary(
                context = applicationContext,
                appsScanned = packages.size,
                highRiskCount = highRiskApps.size
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "app_tracker_data_refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataRefreshWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
