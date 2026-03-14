package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permission_snapshots",
    indices = [Index(value = ["packageName", "capturedAt"])]
)
data class PermissionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val versionCode: Long,
    val dangerousPermissionsCsv: String,
    val addedDangerousCount: Int,
    val riskScore: Int = 0,
    val capturedAt: Long = System.currentTimeMillis()
)
