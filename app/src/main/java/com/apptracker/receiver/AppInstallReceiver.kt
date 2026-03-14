package com.apptracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
import com.apptracker.data.repository.PermissionRepository
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class AppInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var securityEventDao: SecurityEventDao

    @Inject
    lateinit var calculateRiskScore: CalculateRiskScoreUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        scope.launch {
            val app = permissionRepository.getAppInfo(packageName)
            val appName = app?.appName ?: packageName

            val already = securityEventDao.countEventForPackageSince(
                SecurityEventType.APP_INSTALL,
                packageName,
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)
            )
            if (already == 0) {
                securityEventDao.insert(
                    SecurityEventEntity(
                        type = SecurityEventType.APP_INSTALL,
                        packageName = packageName,
                        title = "New app installed",
                        detail = app?.installSourceLabel ?: "Unknown source"
                    )
                )
            }

            NotificationHelper.createChannels(context)
            NotificationHelper.sendNewAppInstallAlert(
                context = context,
                appName = appName,
                packageName = packageName
            )

            if (app != null) {
                val risk = calculateRiskScore(app).overallScore
                if (risk >= 70) {
                    NotificationHelper.sendHighRiskAlert(
                        context = context,
                        appName = appName,
                        packageName = packageName,
                        riskScore = risk
                    )
                }
            }
        }
    }
}
