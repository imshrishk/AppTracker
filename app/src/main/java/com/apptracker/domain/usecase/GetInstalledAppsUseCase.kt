package com.apptracker.domain.usecase

import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.data.repository.PermissionRepository
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val calculateRiskScore: CalculateRiskScoreUseCase
) {
    suspend operator fun invoke(
        includeSystem: Boolean = false,
        enrichWithUsageData: Boolean = true,
        usageTimeRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
        customStartTimeMillis: Long? = null
    ): List<AppInfo> {
        val apps = permissionRepository.getInstalledApps(includeSystem)

        if (!enrichWithUsageData) return apps

        val startTime = customStartTimeMillis ?: usageTimeRange.startTimeMillis()

        val batteryUsages = try {
            batteryRepository.getAllBatteryUsage(
                startTime = startTime
            )
        } catch (_: Exception) {
            emptyMap()
        }

        val networkUsages = try {
            networkRepository.getAllNetworkUsage(
                startTime = startTime
            )
        } catch (_: Exception) {
            emptyMap()
        }

        return apps.map { app ->
            val enriched = app.copy(
                batteryUsage = batteryUsages[app.packageName],
                networkUsage = networkUsages[app.packageName]
            )
            val riskScore = calculateRiskScore(enriched)
            enriched.copy(riskScore = riskScore.overallScore)
        }
    }
}
