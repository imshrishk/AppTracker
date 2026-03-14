package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dns_queries",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["domain"]),
        Index(value = ["isTracker"]),
        Index(value = ["appPackageName"])
    ]
)
data class DnsQueryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val isTracker: Boolean,
    val trackerCategory: String = "",
    val resolverIp: String = "",
    val appPackageName: String = "",
    val appUid: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
)
