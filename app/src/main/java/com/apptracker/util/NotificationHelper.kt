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
        if (highRiskCount == 0) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("AppTracker Scan Complete")
            .setContentText("$appsScanned apps scanned — $highRiskCount high risk")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) { }
    }
}
