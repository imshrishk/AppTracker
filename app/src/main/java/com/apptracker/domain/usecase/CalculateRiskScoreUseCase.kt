package com.apptracker.domain.usecase

import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.AppOpsMode
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.domain.model.RiskFlag
import com.apptracker.domain.model.RiskFlagType
import com.apptracker.domain.model.RiskLevel
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.model.RiskSeverity
import com.apptracker.util.SecurityHeuristics
import javax.inject.Inject

class CalculateRiskScoreUseCase @Inject constructor() {

    operator fun invoke(app: AppInfo): RiskScore {
        val flags = mutableListOf<RiskFlag>()

        // --- Permission analysis ---
        val dangerousGranted = app.permissions.filter { it.isDangerous && it.isGranted }
        val dangerousCount = dangerousGranted.size

        if (dangerousCount > 5) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.EXCESSIVE_PERMISSIONS,
                    severity = RiskSeverity.WARNING,
                    title = "Excessive Dangerous Permissions",
                    description = "$dangerousCount dangerous permissions granted. " +
                            "Most apps need 2-3 at most."
                )
            )
        }

        // Camera + Mic combo
        val hasCamera = dangerousGranted.any { it.permissionName.contains("CAMERA") }
        val hasMic = dangerousGranted.any { it.permissionName.contains("RECORD_AUDIO") }
        if (hasCamera && hasMic) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.CAMERA_AND_MIC_ACCESS,
                    severity = RiskSeverity.WARNING,
                    title = "Camera + Microphone Access",
                    description = "App can access both camera and microphone simultaneously."
                )
            )
        }

        // Background location
        val hasBackgroundLocation = app.permissions.any {
            it.isGranted && it.permissionName.contains("ACCESS_BACKGROUND_LOCATION")
        }
        if (hasBackgroundLocation) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.BACKGROUND_LOCATION,
                    severity = RiskSeverity.DANGER,
                    title = "Background Location Access",
                    description = "App can track your location even when not in use."
                )
            )
        }

        // Contacts + Call log combo
        val hasContacts = dangerousGranted.any {
            it.permissionName.contains("READ_CONTACTS") || it.permissionName.contains("WRITE_CONTACTS")
        }
        val hasCallLog = dangerousGranted.any {
            it.permissionName.contains("READ_CALL_LOG") || it.permissionName.contains("WRITE_CALL_LOG")
        }
        if (hasContacts && hasCallLog) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.READS_CONTACTS_AND_CALL_LOG,
                    severity = RiskSeverity.WARNING,
                    title = "Contacts & Call Log Access",
                    description = "App can read your contacts and call history."
                )
            )
        }

        // SMS access
        val hasSms = dangerousGranted.any {
            it.permissionName.contains("SMS") || it.permissionName.contains("RECEIVE_MMS")
        }
        if (hasSms) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.SMS_ACCESS,
                    severity = RiskSeverity.WARNING,
                    title = "SMS Access",
                    description = "App can read, send, or intercept SMS messages."
                )
            )
        }

        val hasClipboardAccess = app.appOpsEntries.any {
            it.opName.contains("CLIPBOARD", ignoreCase = true) &&
                (it.mode == AppOpsMode.ALLOWED || it.mode == AppOpsMode.FOREGROUND)
        }
        if (hasClipboardAccess) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.CLIPBOARD_ACCESS,
                    severity = RiskSeverity.INFO,
                    title = "Clipboard Access",
                    description = "App can read clipboard contents through App Ops. " +
                            "Review whether it truly needs access to copied text or codes."
                )
            )
        }

        // Overlay permission
        val hasOverlay = app.permissions.any {
            it.isGranted && it.permissionName.contains("SYSTEM_ALERT_WINDOW")
        }
        if (hasOverlay) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.OVERLAY_PERMISSION,
                    severity = RiskSeverity.DANGER,
                    title = "Draw Over Other Apps",
                    description = "App can display content over other apps. " +
                            "Can be used for clickjacking."
                )
            )
        }

        // Accessibility service
        val hasAccessibility = app.permissions.any {
            it.permissionName.contains("BIND_ACCESSIBILITY_SERVICE")
        }
        if (hasAccessibility) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.ACCESSIBILITY_SERVICE,
                    severity = RiskSeverity.DANGER,
                    title = "Accessibility Service",
                    description = "App declares an accessibility service. " +
                            "Can monitor all screen content and user actions."
                )
            )
        }

        // Device admin
        val hasDeviceAdmin = app.permissions.any {
            it.permissionName.contains("BIND_DEVICE_ADMIN")
        }
        if (hasDeviceAdmin) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.DEVICE_ADMIN,
                    severity = RiskSeverity.CRITICAL,
                    title = "Device Administrator",
                    description = "App can act as device admin — can lock device, wipe data, enforce policies."
                )
            )
        }

        // Permission creep — granted but never used via AppOps
        val grantedWithNoOps = dangerousGranted.filter { perm ->
            val matchingOp = app.appOpsEntries.find { op ->
                op.opName.contains(perm.permissionName.substringAfterLast("."), ignoreCase = true)
            }
            matchingOp == null || matchingOp.mode == AppOpsMode.DEFAULT
        }
        if (grantedWithNoOps.size > 2) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.PERMISSION_CREEP,
                    severity = RiskSeverity.INFO,
                    title = "Unused Permissions (${grantedWithNoOps.size})",
                    description = "App has ${grantedWithNoOps.size} dangerous permissions " +
                            "granted but shows no recorded usage. Consider revoking."
                )
            )
        }

        // --- Battery analysis ---
        val batteryScore = app.batteryUsage?.let { battery ->
            var score = 0
            if (battery.backgroundTimeMs > 4 * 60 * 60 * 1000) { // > 4 hours background
                flags.add(
                    RiskFlag(
                        type = RiskFlagType.HIGH_BACKGROUND_ACTIVITY,
                        severity = RiskSeverity.WARNING,
                        title = "High Background Activity",
                        description = "App spent ${battery.totalBackgroundTimeFormatted} " +
                                "in background in last 24h."
                    )
                )
                score += 15
            }
            if (!battery.isBatteryOptimized) {
                flags.add(
                    RiskFlag(
                        type = RiskFlagType.BATTERY_DRAIN,
                        severity = RiskSeverity.INFO,
                        title = "Battery Optimization Exempted",
                        description = "App is exempt from battery optimization, " +
                                "allowing unrestricted background activity."
                    )
                )
                score += 5
            }
            score
        } ?: 0

        // --- Network analysis ---
        val networkScore = app.networkUsage?.let { net ->
            var score = 0
            if (net.sendReceiveRatio > 2.0 && net.totalTxBytes > 10_485_760) { // > 10MB sent, ratio > 2x
                flags.add(
                    RiskFlag(
                        type = RiskFlagType.DATA_EXFILTRATION_PATTERN,
                        severity = RiskSeverity.DANGER,
                        title = "Unusual Data Upload Pattern",
                        description = "App sends ${net.sendReceiveRatio.format(1)}x more data " +
                                "than it receives (${NetworkUsageFormatHelper.formatBytes(net.totalTxBytes)} sent). " +
                                "May indicate data exfiltration."
                    )
                )
                score += 25
            }
            if (net.backgroundBytes > net.foregroundBytes * 2 && net.backgroundBytes > 52_428_800) {
                score += 10
            }
            score
        } ?: 0

        // --- Hidden process scanner ---
        if (SecurityHeuristics.hiddenProcessRisk(app)) {
            flags.add(
                RiskFlag(
                    type = RiskFlagType.HIDDEN_PROCESS,
                    severity = RiskSeverity.DANGER,
                    title = "Hidden Background Process",
                    description = "App shows signals of running a persistent hidden background process " +
                            "(boot receiver + foreground service + suspicious name or excessive bg usage)."
                )
            )
        }

        // --- Certificate pinning absent (heuristic: app uses network but has no pinning config) ---
        val usesNetwork = app.networkUsage?.let { it.totalBytes > 0 } ?: false
        val hasNetworkPermission = app.permissions.any {
            it.isGranted && it.permissionName.contains("INTERNET", ignoreCase = true)
        }
        if (usesNetwork && hasNetworkPermission && dangerousCount >= 3 && !app.isSystemApp) {
            // Only flag high-data apps as needing cert pinning (heuristic)
            val highDataUse = (app.networkUsage?.totalBytes ?: 0L) > 10_485_760L
            if (highDataUse) {
                flags.add(
                    RiskFlag(
                        type = RiskFlagType.CERTIFICATE_PINNING_ABSENT,
                        severity = RiskSeverity.INFO,
                        title = "Certificate Pinning Unverified",
                        description = "High-data app with sensitive permissions. " +
                                "Use the App Info quick APK scan to check for local certificate pinning evidence."
                    )
                )
            }
        }

        // --- Category baseline comparison ---
        SecurityHeuristics.categoryBaselineExceedance(app)?.let { ratio ->
            flags.add(
                RiskFlag(
                    type = RiskFlagType.CATEGORY_BASELINE_EXCEEDED,
                    severity = RiskSeverity.WARNING,
                    title = "Exceeds Category Baseline",
                    description = "This ${app.category.label} app holds ${"%.1f".format(ratio)}× more " +
                            "dangerous permissions than the average ${app.category.label} app."
                )
            )
        }

        // --- Calculate scores ---
        val permissionScore = minOf(
            dangerousCount * 8 +
                    (if (hasBackgroundLocation) 15 else 0) +
                    (if (hasOverlay) 10 else 0) +
                    (if (hasAccessibility) 15 else 0) +
                    (if (hasDeviceAdmin) 20 else 0),
            100
        )

        val behaviorScore = minOf(batteryScore + networkScore, 100)

        val overallScore = minOf(
            (permissionScore * 0.4 + behaviorScore * 0.3 +
                    networkScore * 0.2 + batteryScore * 0.1).toInt(),
            100
        )

        val riskLevel = when {
            overallScore >= 70 -> RiskLevel.CRITICAL
            overallScore >= 45 -> RiskLevel.HIGH
            overallScore >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return RiskScore(
            packageName = app.packageName,
            overallScore = overallScore,
            permissionScore = permissionScore,
            behaviorScore = behaviorScore,
            networkScore = networkScore,
            batteryScore = batteryScore,
            riskLevel = riskLevel,
            flags = flags
        )
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}

private object NetworkUsageFormatHelper {
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
