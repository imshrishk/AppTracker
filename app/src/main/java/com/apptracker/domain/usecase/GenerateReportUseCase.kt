package com.apptracker.domain.usecase

import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.BatteryUsageInfo
import com.apptracker.data.model.NetworkUsageInfo
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.data.util.PermissionDescriptions
import com.apptracker.domain.model.RiskScore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class GenerateReportUseCase @Inject constructor() {

    operator fun invoke(app: AppInfo, riskScore: RiskScore?): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("   AppTracker Report")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        // App Info
        sb.appendLine("▶ App: ${app.appName}")
        sb.appendLine("  Package: ${app.packageName}")
        sb.appendLine("  Version: ${app.versionName ?: "N/A"} (${app.versionCode})")
        sb.appendLine("  Target SDK: ${app.targetSdkVersion} | Min SDK: ${app.minSdkVersion}")
        sb.appendLine("  System App: ${if (app.isSystemApp) "Yes" else "No"}")
        sb.appendLine("  Installed: ${sdf.format(Date(app.installTime))}")
        sb.appendLine("  Updated: ${sdf.format(Date(app.lastUpdateTime))}")
        sb.appendLine()

        // Risk Score
        if (riskScore != null) {
            sb.appendLine("▶ Risk Assessment")
            sb.appendLine("  Overall Score: ${riskScore.overallScore}/100 (${riskScore.riskLevel.label})")
            sb.appendLine("  Permission: ${riskScore.permissionScore} | Behavior: ${riskScore.behaviorScore}")
            sb.appendLine("  Network: ${riskScore.networkScore} | Battery: ${riskScore.batteryScore}")
            if (riskScore.flags.isNotEmpty()) {
                sb.appendLine("  Flags:")
                riskScore.flags.forEach { flag ->
                    sb.appendLine("    ⚠ [${flag.severity}] ${flag.title}")
                    sb.appendLine("      ${flag.description}")
                }
            }
            sb.appendLine()
        }

        // Permissions
        val dangerous = app.permissions.filter { it.isDangerous }
        val granted = app.permissions.filter { it.isGranted }
        sb.appendLine("▶ Permissions (${app.permissions.size} total)")
        sb.appendLine("  Dangerous: ${dangerous.size} | Granted: ${granted.size}")
        sb.appendLine()

        if (dangerous.isNotEmpty()) {
            sb.appendLine("  Dangerous Permissions:")
            dangerous.forEach { perm ->
                val friendlyName = PermissionDescriptions.getFriendlyName(perm.permissionName)
                val status = if (perm.isGranted) "✓ GRANTED" else "✗ DENIED"
                sb.appendLine("    $status  $friendlyName")
                sb.appendLine("           ${perm.permissionName}")
                val desc = PermissionDescriptions.getDescription(perm.permissionName)
                if (desc != "No description available for this permission.") {
                    sb.appendLine("           $desc")
                }
            }
            sb.appendLine()
        }

        // App Ops
        if (app.appOpsEntries.isNotEmpty()) {
            sb.appendLine("▶ App Operations (${app.appOpsEntries.size} tracked)")
            val allowed = app.appOpsEntries.filter {
                it.mode.name == "ALLOWED" || it.mode.name == "FOREGROUND"
            }
            val denied = app.appOpsEntries.filter {
                it.mode.name == "IGNORED" || it.mode.name == "ERRORED"
            }
            sb.appendLine("  Allowed: ${allowed.size} | Denied: ${denied.size}")
            app.appOpsEntries.forEach { op ->
                sb.appendLine("    [${op.mode}] ${op.opName.removePrefix("android:")} (${op.category})")
            }
            sb.appendLine()
        }

        // Battery
        app.batteryUsage?.let { battery ->
            sb.appendLine("▶ Battery Usage")
            sb.appendLine("  Foreground: ${battery.totalForegroundTimeFormatted}")
            sb.appendLine("  Background: ${battery.totalBackgroundTimeFormatted}")
            sb.appendLine("  Optimized: ${if (battery.isBatteryOptimized) "Yes" else "No"}")
            sb.appendLine("  Doze Whitelisted: ${if (battery.isDozeWhitelisted) "Yes" else "No"}")
            sb.appendLine()
        }

        // Network
        app.networkUsage?.let { network ->
            sb.appendLine("▶ Network Usage")
            sb.appendLine("  Total: ${network.formattedTotal}")
            sb.appendLine("  WiFi: ${network.formattedWifi} | Mobile: ${network.formattedMobile}")
            sb.appendLine("  Foreground: ${NetworkUsageInfo.formatBytes(network.foregroundBytes)}")
            sb.appendLine("  Background: ${NetworkUsageInfo.formatBytes(network.backgroundBytes)}")
            sb.appendLine("  Send/Receive Ratio: ${"%.2f".format(network.sendReceiveRatio)}")
            if (network.sendReceiveRatio > 2.0) {
                sb.appendLine("  ⚠ ANOMALY: App sends significantly more data than it receives")
            }
            sb.appendLine()
        }

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("Generated by AppTracker on ${sdf.format(Date())}")

        return sb.toString()
    }
}
