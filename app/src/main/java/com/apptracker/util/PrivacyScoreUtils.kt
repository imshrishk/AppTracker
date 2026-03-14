package com.apptracker.util

import com.apptracker.data.model.PermissionDetail

object PrivacyScoreUtils {

    /**
     * Computes a "data hoarding score" (0–100) based on the combination of
     * sensitive permissions an app holds. Higher score = more data access potential.
     * All computation is local — no data leaves the device.
     */
    fun dataHoardingScore(permissions: List<PermissionDetail>): Int {
        val granted = permissions
            .filter { it.isDangerous && it.isGranted }
            .map { it.permissionName.uppercase() }

        var score = 0
        val hasContacts = granted.any { "CONTACTS" in it }
        val hasFineLocation = granted.any { "ACCESS_FINE_LOCATION" in it }
        val hasCoarseLocation = !hasFineLocation && granted.any { "ACCESS_COARSE_LOCATION" in it || "LOCATION" in it }
        val hasCamera = granted.any { "CAMERA" in it }
        val hasMic = granted.any { "RECORD_AUDIO" in it }
        val hasCallLog = granted.any { "CALL_LOG" in it || "PROCESS_OUTGOING_CALLS" in it }
        val hasSms = granted.any { "READ_SMS" in it || "RECEIVE_SMS" in it || "SEND_SMS" in it }
        val hasBodySensors = granted.any { "BODY_SENSORS" in it }
        val hasCalendar = granted.any { "READ_CALENDAR" in it }
        val hasStorage = granted.any {
            "READ_EXTERNAL_STORAGE" in it || "WRITE_EXTERNAL_STORAGE" in it || "READ_MEDIA" in it
        }

        if (hasContacts) score += 15
        if (hasFineLocation) score += 20 else if (hasCoarseLocation) score += 10
        if (hasCamera) score += 15
        if (hasMic) score += 15
        if (hasCallLog) score += 20
        if (hasSms) score += 20
        if (hasBodySensors) score += 10
        if (hasCalendar) score += 10
        if (hasStorage) score += 5

        // Combo bonuses — sensitive combination multipliers
        if (hasContacts && (hasFineLocation || hasCoarseLocation)) score += 10
        if (hasContacts && hasSms) score += 15
        if ((hasFineLocation || hasCoarseLocation) && hasMic) score += 10
        if (hasCamera && hasMic) score += 5

        return score.coerceAtMost(100)
    }

    fun dataHoardingLabel(score: Int): String = when {
        score >= 80 -> "Extreme Data Hoarder"
        score >= 60 -> "Heavy Data Collector"
        score >= 40 -> "Moderate Data Collector"
        score >= 20 -> "Light Data Access"
        else -> "Minimal Data Access"
    }
}
