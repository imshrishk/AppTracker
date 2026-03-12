package com.apptracker.domain.usecase

import com.apptracker.data.model.AppInfo
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
    suspend operator fun invoke(packageName: String): AppInfo? {
        val app = permissionRepository.getAppInfo(packageName) ?: return null
        val battery = try { batteryRepository.getBatteryUsage(packageName) } catch (_: Exception) { null }
        val network = try { networkRepository.getNetworkUsage(packageName) } catch (_: Exception) { null }

        val enriched = app.copy(
            batteryUsage = battery,
            networkUsage = network
        )
        val riskScore = calculateRiskScore(enriched)
        return enriched.copy(riskScore = riskScore.overallScore)
    }
}
