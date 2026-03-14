package com.apptracker.monitor

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
import com.apptracker.data.repository.PermissionRepository
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpsRealtimeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionRepository: PermissionRepository,
    private val securityEventDao: SecurityEventDao,
    private val calculateRiskScore: CalculateRiskScoreUseCase
) {
    private companion object {
        const val READ_CLIPBOARD_OP = "android:read_clipboard"
    }

    private val appOpsManager: AppOpsManager? =
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val activeListener = AppOpsManager.OnOpActiveChangedListener { op, uid, packageName, active ->
        if (!active || packageName.isNullOrBlank()) return@OnOpActiveChangedListener
        if (packageName == context.packageName) return@OnOpActiveChangedListener

        val isSensitive = op == AppOpsManager.OPSTR_CAMERA ||
            op == AppOpsManager.OPSTR_RECORD_AUDIO ||
            op == AppOpsManager.OPSTR_FINE_LOCATION ||
            op == AppOpsManager.OPSTR_COARSE_LOCATION ||
            op == AppOpsManager.OPSTR_BODY_SENSORS ||
            op == READ_CLIPBOARD_OP

        if (!isSensitive) return@OnOpActiveChangedListener

        scope.launch {
            val since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30)
            val existing = securityEventDao.countEventForPackageSince(
                SecurityEventType.SENSITIVE_APPOPS,
                packageName,
                since
            )
            if (existing > 0) return@launch

            val app = permissionRepository.getAppInfo(packageName)
            val appName = app?.appName ?: packageName
            val opLabel = when (op) {
                AppOpsManager.OPSTR_CAMERA -> "Camera"
                AppOpsManager.OPSTR_RECORD_AUDIO -> "Microphone"
                AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.OPSTR_COARSE_LOCATION -> "Location"
                AppOpsManager.OPSTR_BODY_SENSORS -> "Body Sensors"
                READ_CLIPBOARD_OP -> "Clipboard"
                else -> op
            }

            securityEventDao.insert(
                SecurityEventEntity(
                    type = SecurityEventType.SENSITIVE_APPOPS,
                    packageName = packageName,
                    title = "Realtime sensitive access",
                    detail = opLabel
                )
            )

            NotificationHelper.sendSensitiveAppOpsAlert(
                context = context,
                appName = appName,
                packageName = packageName,
                opName = opLabel
            )

            if (app != null) {
                val score = calculateRiskScore(app).overallScore
                if (score >= 70) {
                    NotificationHelper.sendHighRiskAlert(
                        context = context,
                        appName = appName,
                        packageName = packageName,
                        riskScore = score
                    )
                }
            }
        }
    }

    fun start() {
        if (started) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = appOpsManager ?: return
        try {
            manager.startWatchingActive(
                arrayOf(
                    AppOpsManager.OPSTR_CAMERA,
                    AppOpsManager.OPSTR_RECORD_AUDIO,
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    AppOpsManager.OPSTR_BODY_SENSORS,
                    READ_CLIPBOARD_OP
                ),
                context.mainExecutor,
                activeListener
            )
            started = true
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
