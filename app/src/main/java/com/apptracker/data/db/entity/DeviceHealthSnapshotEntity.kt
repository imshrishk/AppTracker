package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_health_snapshots",
    indices = [Index(value = ["timestamp"])]
)
data class DeviceHealthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val appCount: Int,
    val healthScore: Int,
    val averageRiskScore: Int,
    val highRiskCount: Int,
    val dangerousPermissionCount: Int,
    val heavyBackgroundAppCount: Int
)
