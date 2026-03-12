package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_history",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"])
    ]
)
data class BatteryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val batteryPercent: Double,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val foregroundServiceTimeMs: Long,
    val wakelockTimeMs: Long,
    val alarmWakeups: Int,
    val timestamp: Long = System.currentTimeMillis()
)
