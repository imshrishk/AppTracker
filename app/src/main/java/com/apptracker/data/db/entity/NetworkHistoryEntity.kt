package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "network_history",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"])
    ]
)
data class NetworkHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
)
