package com.apptracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.apptracker.MainActivity
import com.apptracker.R

object NotificationHelper {

    const val CHANNEL_ID = "apptracker_alerts"
    const val CHANNEL_RISK_ID = "apptracker_risk_alerts"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val generalChannel = NotificationChannel(
            CHANNEL_ID,
            "AppTracker Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General alerts from AppTracker"
        }

        val riskChannel = NotificationChannel(
            CHANNEL_RISK_ID,
            "High Risk App Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when apps with high risk scores are detected"
        }

        manager.createNotificationChannel(generalChannel)
        manager.createNotificationChannel(riskChannel)
    }

    fun sendHighRiskAlert(context: Context, appName: String, packageName: String, riskScore: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Risk App Detected")
            .setContentText("$appName has a risk score of $riskScore")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$appName has been flagged with a risk score of $riskScore/100. Tap to review its permissions and activity."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip
        }
    }

    fun sendRefreshSummary(context: Context, appsScanned: Int, highRiskCount: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val summaryText = if (highRiskCount > 0) {
            "$appsScanned apps scanned — $highRiskCount high risk"
        } else {
            "$appsScanned apps scanned — no high-risk apps detected"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("AppTracker Scan Complete")
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) { }
    }

    fun sendWeeklyDigest(
        context: Context,
        eventCount: Int,
        trackerHits: Int,
        dnsLeakSignals: Int,
        newApps: Int,
        highRiskApps: Int,
        healthScore: Int
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val summaryText = buildString {
            append("$eventCount alerts")
            append(" • $trackerHits tracker hits")
            if (dnsLeakSignals > 0) append(" • $dnsLeakSignals DNS leak signals")
            if (newApps > 0) append(" • $newApps new apps")
        }

        val detailText = buildString {
            append("Past 7 days: $eventCount local alerts, $trackerHits tracker DNS hits")
            if (dnsLeakSignals > 0) append(", $dnsLeakSignals DNS leak/bypass signals")
            append(", $highRiskApps high-risk apps currently flagged, device health $healthScore/100")
            if (newApps > 0) append(", $newApps newly installed apps")
            append('.')
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("AppTracker Weekly Digest")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1002, notification)
        } catch (_: SecurityException) { }
    }

    fun sendPermissionDeltaAlert(
        context: Context,
        appName: String,
        packageName: String,
        addedDangerousPermissions: List<String>
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val preview = addedDangerousPermissions.take(3).joinToString(", ")
        val text = if (addedDangerousPermissions.size > 3) {
            "$preview +${addedDangerousPermissions.size - 3} more"
        } else preview

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (packageName + "_perm").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Permission Change Detected")
            .setContentText("$appName added dangerous permissions")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$appName added dangerous permissions: $text"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((packageName + "_perm").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendNightActivityAlert(
        context: Context,
        appName: String,
        packageName: String,
        networkBytes: Long,
        backgroundMs: Long
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val mb = networkBytes / 1_048_576
        val mins = backgroundMs / 60_000
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (packageName + "_night").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Night Activity Anomaly")
            .setContentText("$appName active during 12am–5am")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$appName showed unusual night activity ($mb MB network, $mins min background)."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((packageName + "_night").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendSensitiveAppOpsAlert(
        context: Context,
        appName: String,
        packageName: String,
        opName: String
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (packageName + "_ops_" + opName).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val readable = when {
            opName.contains("CLIPBOARD", ignoreCase = true) -> "Clipboard"
            else -> opName.replace("OPSTR_", "").replace('_', ' ')
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sensitive Access Event")
            .setContentText("$appName accessed $readable")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$appName triggered a sensitive operation: $readable"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((packageName + "_ops_" + opName).hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendWatchlistChangeAlert(
        context: Context,
        appName: String,
        packageName: String,
        changeDetail: String
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (packageName + "_watch").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Watched App Changed")
            .setContentText("$appName: $changeDetail")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Changes detected in watched app $appName: $changeDetail"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((packageName + "_watch").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendPermissionSpikeAlert(
        context: Context,
        appName: String,
        packageName: String,
        count: Int
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (packageName + "_spike").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Permission Spike Detected")
            .setContentText("$appName gained $count dangerous permissions in 24h")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((packageName + "_spike").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendNewAppInstallAlert(context: Context, appName: String, packageName: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (packageName + "_install").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("New App Installed")
            .setContentText("$appName was installed since last scan")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((packageName + "_install").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendHealthDegradationAlert(context: Context, previousScore: Int, currentScore: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val drop = previousScore - currentScore
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Device Health Dropped")
            .setContentText("Health score fell $drop points (now $currentScore/100)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Your device health score dropped $drop points from $previousScore to $currentScore. Open AppTracker to review recent changes."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2001, notification)
        } catch (_: SecurityException) { }
    }

    fun sendBurstNetworkAlert(
        context: Context,
        appName: String,
        packageName: String,
        currentBytes: Long,
        previousBytes: Long
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val current = currentBytes / 1_048_576
        val previous = previousBytes / 1_048_576
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_package", packageName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (packageName + "_burst").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Burst Network Activity")
            .setContentText("$appName used ${current}MB (was ${previous}MB)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$appName sent/received ${current}MB this scan cycle, up from ${previous}MB — a ${current / (previous.coerceAtLeast(1))}× spike."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((packageName + "_burst").hashCode(), notification)
        } catch (_: SecurityException) { }
    }

    fun sendSecurityHeuristicAlert(
        context: Context,
        title: String,
        detail: String,
        packageName: String? = null
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            packageName?.let { putExtra("navigate_to_package", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (title + (packageName ?: "global")).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RISK_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify((title + (packageName ?: "global")).hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
