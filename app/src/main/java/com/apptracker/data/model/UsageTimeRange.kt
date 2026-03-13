package com.apptracker.data.model

enum class UsageTimeRange(
    val days: Int,
    val label: String
) {
    LAST_24_HOURS(1, "24h"),
    LAST_7_DAYS(7, "7d"),
    LAST_30_DAYS(30, "30d"),
    LAST_90_DAYS(90, "90d");

    fun startTimeMillis(now: Long = System.currentTimeMillis()): Long {
        return now - days * 24L * 60L * 60L * 1000L
    }

    companion object {
        fun fromDays(days: Int): UsageTimeRange {
            return entries.firstOrNull { it.days == days } ?: LAST_24_HOURS
        }
    }
}
