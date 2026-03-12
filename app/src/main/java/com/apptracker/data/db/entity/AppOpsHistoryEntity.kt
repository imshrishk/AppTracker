package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_ops_history",
    indices = [
        Index(value = ["packageName", "opName"]),
        Index(value = ["timestamp"])
    ]
)
data class AppOpsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val opName: String,
    val opCode: Int,
    val mode: String,
    val lastAccessTime: Long,
    val lastRejectTime: Long,
    val duration: Long,
    val accessCount: Int,
    val rejectCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
