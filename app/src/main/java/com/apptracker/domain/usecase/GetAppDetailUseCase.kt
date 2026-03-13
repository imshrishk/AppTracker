package com.apptracker.domain.usecase

import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.data.repository.PermissionRepository
import javax.inject.Inject

class GetAppDetailUseCase @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val calculateRiskScore: CalculateRiskScoreUseCase
) {
    suspend operator fun invoke(
        packageName: String,
        usageTimeRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
        customStartTimeMillis: Long? = null
    ): AppInfo? {
        val app = permissionRepository.getAppInfo(packageName) ?: return null
        val startTime = customStartTimeMillis ?: usageTimeRange.startTimeMillis()
        val battery = try {
            batteryRepository.getBatteryUsage(
                packageName = packageName,
                startTime = startTime
            )
        } catch (_: Exception) {
            null
        }
        val network = try {
            networkRepository.getNetworkUsage(
                packageName = packageName,
                startTime = startTime
            )
        } catch (_: Exception) {
            null
        }

        val enriched = app.copy(
            batteryUsage = battery,
            networkUsage = network
        )
        val riskScore = calculateRiskScore(enriched)
        return enriched.copy(riskScore = riskScore.overallScore)
    }
}
