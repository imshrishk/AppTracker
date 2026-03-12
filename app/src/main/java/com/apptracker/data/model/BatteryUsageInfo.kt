package com.apptracker.data.model

data class BatteryUsageInfo(
    val packageName: String,
    val totalBatteryPercent: Double,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val foregroundServiceTimeMs: Long,
    val visibleTimeMs: Long,
    val cachedTimeMs: Long,
    val lastTimeUsed: Long,
    val wakelockTimeMs: Long,
    val alarmWakeups: Int,
    val cpuForegroundMs: Long,
    val cpuBackgroundMs: Long,
    val wifiUsageMah: Double,
    val gpsUsageMah: Double,
    val screenOnDrainMah: Double,
    val screenOffDrainMah: Double,
    val isBatteryOptimized: Boolean,
    val isDozeWhitelisted: Boolean,
    val isBackgroundRestricted: Boolean
) {
    val totalForegroundTimeFormatted: String
        get() = formatDuration(foregroundTimeMs)

    val totalBackgroundTimeFormatted: String
        get() = formatDuration(backgroundTimeMs)

    companion object {
        fun formatDuration(ms: Long): String {
            val seconds = ms / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
    }
}
