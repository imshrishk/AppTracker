package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "security_events",
    indices = [Index(value = ["type", "timestamp"]), Index(value = ["packageName"])]
)
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val packageName: String,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SecurityEventType {
    const val PERMISSION_DELTA = "permission_delta"
    const val NIGHT_ACTIVITY = "night_activity"
    const val SENSITIVE_APPOPS = "sensitive_appops"
    const val AUTO_REVOKE = "auto_revoke"
    const val DARK_PATTERN = "dark_pattern"
    const val WATCHLIST_CHANGE = "watchlist_change"
    const val PERMISSION_SPIKE = "permission_spike"
    const val APP_INSTALL = "app_install"
    const val DORMANT_APP = "dormant_app"
    const val BURST_NETWORK = "burst_network"
    const val HEALTH_DROP = "health_drop"
    const val FAKE_GPS = "fake_gps"
    const val ACCESSIBILITY_WATCHDOG = "accessibility_watchdog"
    const val SCREEN_RECORDING = "screen_recording"
    const val KEYLOGGER_RISK = "keylogger_risk"
    const val APP_IMPERSONATION = "app_impersonation"
    const val CROSS_APP_COLLUSION = "cross_app_collusion"
    const val SENSITIVE_FILE_DETECTED = "sensitive_file_detected"
    const val DUPLICATE_FILES_DETECTED = "duplicate_files_detected"
    const val SECURE_DELETE = "secure_delete"
    const val CUSTOM_RULE_TRIGGERED = "custom_rule_triggered"
    const val FILE_ACCESS_AUDIT = "file_access_audit"
    const val HIDDEN_PROCESS = "hidden_process"
    const val CERTIFICATE_PINNING = "certificate_pinning"
    const val DNS_LEAK = "dns_leak"
}
