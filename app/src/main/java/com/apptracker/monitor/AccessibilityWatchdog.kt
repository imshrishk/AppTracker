package com.apptracker.monitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
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
class AccessibilityWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityEventDao: SecurityEventDao
) {
    private val accessibilityManager: AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val listener = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
        if (!enabled) return@AccessibilityStateChangeListener
        evaluateEnabledServices()
    }

    fun start() {
        if (started) return
        val manager = accessibilityManager ?: return
        try {
            manager.addAccessibilityStateChangeListener(listener)
            started = true
            evaluateEnabledServices()
        } catch (_: Exception) {
        }
    }

    private fun evaluateEnabledServices() {
        val manager = accessibilityManager ?: return
        val services = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ) ?: return

        services.forEach { serviceInfo ->
            val serviceId = serviceInfo.id ?: return@forEach
            val packageName = serviceId.substringBefore('/').ifBlank { return@forEach }
            if (packageName == context.packageName) return@forEach

            scope.launch {
                val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12)
                val existing = securityEventDao.countEventForPackageSince(
                    SecurityEventType.ACCESSIBILITY_WATCHDOG,
                    packageName,
                    since
                )
                if (existing > 0) return@launch

                val detail = "Accessibility service enabled: $serviceId"
                securityEventDao.insert(
                    SecurityEventEntity(
                        type = SecurityEventType.ACCESSIBILITY_WATCHDOG,
                        packageName = packageName,
                        title = "Accessibility service active",
                        detail = detail
                    )
                )

                NotificationHelper.sendSecurityHeuristicAlert(
                    context = context,
                    title = "Accessibility Watchdog",
                    detail = detail,
                    packageName = packageName
                )
            }
        }
    }
}
