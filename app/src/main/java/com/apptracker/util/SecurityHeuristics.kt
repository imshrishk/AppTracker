package com.apptracker.util

import com.apptracker.data.model.AppInfo
import java.security.MessageDigest
import java.text.Normalizer

object SecurityHeuristics {

    // ---------- Hidden process scanner ----------
    /**
     * Heuristic: app is likely running a hidden persistent process if it
     * (a) is not a system app, (b) requests BOOT_COMPLETED (to auto-start),
     * (c) holds at least one background execution permission, and
     * (d) is not a known messaging / VPN / media app category.
     */
    fun hiddenProcessRisk(app: AppInfo): Boolean {
        val perms = app.permissions.filter { it.isGranted }.map { it.permissionName.uppercase() }
        val hasBootReceiver = perms.any { it.contains("RECEIVE_BOOT_COMPLETED") }
        val hasBackgroundExecution = perms.any {
            it.contains("FOREGROUND_SERVICE") ||
                it.contains("REQUEST_INSTALL_PACKAGES") ||
                it.contains("USE_EXACT_ALARM") ||
                it.contains("SCHEDULE_EXACT_ALARM")
        }
        val hasSuspiciousName = app.appName.contains("service", ignoreCase = true) ||
            app.appName.contains("daemon", ignoreCase = true) ||
            app.appName.contains("system", ignoreCase = true) ||
            app.appName.contains("helper", ignoreCase = true)
        // high background time with no foreground usage
        val bgTimeMs = app.batteryUsage?.backgroundTimeMs ?: 0L
        val fgTimeMs = app.batteryUsage?.foregroundTimeMs ?: 0L
        val heavyBackgroundNoForeground = bgTimeMs > 2L * 60L * 60L * 1000L && fgTimeMs < 5L * 60L * 1000L
        return hasBootReceiver && hasBackgroundExecution && (hasSuspiciousName || heavyBackgroundNoForeground)
    }

    // ---------- Category baseline comparison ----------
    // Returns a multiplier (e.g. 2.5) if the app has significantly more dangerous
    // permissions than the average for its category, or null if baseline N/A.
    private val categoryAveragePermissions: Map<com.apptracker.data.model.AppCategory, Float> = mapOf(
        com.apptracker.data.model.AppCategory.FINANCE to 2.5f,
        com.apptracker.data.model.AppCategory.COMMUNICATION to 4.5f,
        com.apptracker.data.model.AppCategory.SOCIAL to 5.0f,
        com.apptracker.data.model.AppCategory.MEDIA to 3.0f,
        com.apptracker.data.model.AppCategory.BROWSER to 1.5f,
        com.apptracker.data.model.AppCategory.GAMES to 2.0f,
        com.apptracker.data.model.AppCategory.TOOLS to 2.5f,
        com.apptracker.data.model.AppCategory.HEALTH to 3.5f,
        com.apptracker.data.model.AppCategory.OTHER to 3.0f
    )

    /** Returns the ratio of this app's dangerous permission count vs category avg (null if < 1.5×) */
    fun categoryBaselineExceedance(app: AppInfo): Float? {
        val avg = categoryAveragePermissions[app.category] ?: return null
        val dangerousCount = app.permissions.count { it.isDangerous && it.isGranted }.toFloat()
        if (avg <= 0f) return null
        val ratio = dangerousCount / avg
        return if (ratio >= 1.5f) ratio else null
    }

    private val popularBrandNames = listOf(
        "googlepay", "gpay", "phonepe", "paytm", "paypal", "whatsapp", "telegram",
        "signal", "instagram", "facebook", "gmail", "outlook", "amazon", "flipkart"
    )

    fun isFakeGpsLikely(app: AppInfo): Boolean {
        val perms = app.permissions.filter { it.isGranted }.map { it.permissionName.uppercase() }
        val hasMockLocationPerm = perms.any { it.contains("ACCESS_MOCK_LOCATION") }
        val hasFineLocation = perms.any { it.contains("ACCESS_FINE_LOCATION") || it.contains("ACCESS_COARSE_LOCATION") }
        val hasMockOps = app.appOpsEntries.any { it.opName.contains("MOCK", ignoreCase = true) }
        return hasMockLocationPerm || (hasFineLocation && hasMockOps)
    }

    fun hasAccessibilityWatchdogSignal(app: AppInfo): Boolean {
        val hasAccessibilityPermission = app.permissions.any {
            it.isGranted && it.permissionName.contains("BIND_ACCESSIBILITY_SERVICE", ignoreCase = true)
        }
        val hasAccessibilityOps = app.appOpsEntries.any {
            it.opName.contains("ACCESSIBILITY", ignoreCase = true)
        }
        return hasAccessibilityPermission || hasAccessibilityOps
    }

    fun hasScreenRecordingSignal(app: AppInfo): Boolean {
        val hasProjectionPermission = app.permissions.any {
            it.isGranted && (
                it.permissionName.contains("MEDIA_PROJECTION", ignoreCase = true) ||
                    it.permissionName.contains("CAPTURE_VIDEO_OUTPUT", ignoreCase = true)
                )
        }
        val hasProjectionOps = app.appOpsEntries.any {
            it.opName.contains("PROJECT_MEDIA", ignoreCase = true) ||
                it.opName.contains("MEDIA_PROJECTION", ignoreCase = true)
        }
        return hasProjectionPermission || hasProjectionOps
    }

    fun keyloggerCompositeScore(app: AppInfo): Int {
        val perms = app.permissions.filter { it.isGranted }.map { it.permissionName.uppercase() }
        val hasAccessibility = perms.any { it.contains("BIND_ACCESSIBILITY_SERVICE") } ||
            app.appOpsEntries.any { it.opName.contains("ACCESSIBILITY", ignoreCase = true) }
        val hasOverlay = perms.any { it.contains("SYSTEM_ALERT_WINDOW") || it.contains("OVERLAY") }
        val hasInputMethod = perms.any { it.contains("BIND_INPUT_METHOD") } ||
            app.packageName.contains("keyboard", ignoreCase = true) ||
            app.appName.contains("keyboard", ignoreCase = true)
        val hasReadClipboardLike = app.appOpsEntries.any { it.opName.contains("CLIPBOARD", ignoreCase = true) }

        var score = 0
        if (hasAccessibility) score += 45
        if (hasOverlay) score += 30
        if (hasInputMethod) score += 20
        if (hasReadClipboardLike) score += 15
        return score.coerceIn(0, 100)
    }

    fun looksLikeImpersonation(app: AppInfo): String? {
        val normalizedName = normalize(app.appName)
        val matchedBrand = popularBrandNames.firstOrNull { normalizedName.contains(it) } ?: return null
        val packageNormalized = normalize(app.packageName)
        val nameLooksLeetspeak = containsLeetSubstitution(app.appName)
        val packageMismatch = !packageNormalized.contains(matchedBrand)
        if (packageMismatch || nameLooksLeetspeak) {
            return "Looks similar to '$matchedBrand' (name/package mismatch)"
        }
        return null
    }

    fun certificateDigestSha256(raw: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(input: String): String {
        val leetFixed = input
            .lowercase()
            .replace('0', 'o')
            .replace('1', 'l')
            .replace('3', 'e')
            .replace('5', 's')
            .replace('7', 't')
        return Normalizer.normalize(leetFixed, Normalizer.Form.NFD)
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private fun containsLeetSubstitution(input: String): Boolean {
        return input.any { it in listOf('0', '1', '3', '5', '7') }
    }
}
