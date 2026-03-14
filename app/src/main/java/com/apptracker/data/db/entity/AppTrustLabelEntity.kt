package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_trust_labels")
data class AppTrustLabelEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object TrustLabel {
    const val TRUSTED = "Trusted"
    const val SUSPICIOUS = "Suspicious"
    const val UNKNOWN = "Unknown"
}
