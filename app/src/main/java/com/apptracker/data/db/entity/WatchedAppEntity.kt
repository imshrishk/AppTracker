package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watched_apps",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class WatchedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
