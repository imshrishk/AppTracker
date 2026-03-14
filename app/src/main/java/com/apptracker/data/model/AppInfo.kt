package com.apptracker.data.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String?,
    val versionCode: Long,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val installTime: Long,
    val lastUpdateTime: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val dataDir: String?,
    val sourceDir: String?,
    val linuxUid: Int = -1,
    val category: AppCategory = AppCategory.OTHER,
    val permissions: List<PermissionDetail>,
    val riskScore: Int = 0,
    val batteryUsage: BatteryUsageInfo? = null,
    val networkUsage: NetworkUsageInfo? = null,
    val appOpsEntries: List<AppOpsEntry> = emptyList(),
    val sensorAccess: List<SensorAccessInfo> = emptyList(),
    val storageUsage: StorageUsageInfo? = null,
    val installSourcePackage: String? = null,
    val installSourceLabel: String = "Unknown",
    val isSideloaded: Boolean = false
)

data class PermissionDetail(
    val permissionName: String,
    val group: String?,
    val protectionLevel: ProtectionLevel,
    val isGranted: Boolean,
    val flags: Int,
    val description: String?,
    val label: String?,
    val isDangerous: Boolean,
    val lastAccessTime: Long? = null,
    val accessCount: Int = 0
)

enum class ProtectionLevel {
    NORMAL,
    DANGEROUS,
    SIGNATURE,
    SIGNATURE_OR_SYSTEM,
    INTERNAL,
    UNKNOWN
}

data class AppOpsEntry(
    val opName: String,
    val opCode: Int,
    val mode: AppOpsMode,
    val lastAccessTime: Long,
    val lastRejectTime: Long,
    val duration: Long,
    val accessCount: Int,
    val rejectCount: Int,
    val proxyPackageName: String?,
    val category: AppOpsCategory
)

enum class AppOpsMode {
    ALLOWED,
    IGNORED,
    ERRORED,
    DEFAULT,
    FOREGROUND,
    UNKNOWN
}

enum class AppOpsCategory {
    LOCATION,
    CAMERA,
    MICROPHONE,
    CONTACTS,
    PHONE,
    STORAGE,
    CALENDAR,
    SMS,
    SENSORS,
    CLIPBOARD,
    NOTIFICATIONS,
    OTHER
}
