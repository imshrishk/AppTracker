package com.apptracker.domain.model

data class RiskScore(
    val packageName: String,
    val overallScore: Int, // 0-100
    val permissionScore: Int,
    val behaviorScore: Int,
    val networkScore: Int,
    val batteryScore: Int,
    val riskLevel: RiskLevel,
    val flags: List<RiskFlag>
)

enum class RiskLevel(val label: String, val color: Long) {
    LOW("Low Risk", 0xFF4CAF50),
    MEDIUM("Medium Risk", 0xFFFF9800),
    HIGH("High Risk", 0xFFF44336),
    CRITICAL("Critical", 0xFF9C27B0)
}

data class RiskFlag(
    val type: RiskFlagType,
    val severity: RiskSeverity,
    val title: String,
    val description: String
)

enum class RiskFlagType {
    EXCESSIVE_PERMISSIONS,
    UNUSED_DANGEROUS_PERMISSION,
    BACKGROUND_LOCATION,
    CAMERA_AND_MIC_ACCESS,
    HIGH_BACKGROUND_ACTIVITY,
    NIGHT_ACTIVITY,
    DATA_EXFILTRATION_PATTERN,
    BATTERY_DRAIN,
    PERMISSION_CREEP,
    OVERLAY_PERMISSION,
    ACCESSIBILITY_SERVICE,
    DEVICE_ADMIN,
    READS_CONTACTS_AND_CALL_LOG,
    SMS_ACCESS,
    CLIPBOARD_ACCESS
}

enum class RiskSeverity {
    INFO,
    WARNING,
    DANGER,
    CRITICAL
}
