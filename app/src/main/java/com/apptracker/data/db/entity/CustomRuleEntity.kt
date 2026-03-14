package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val metric: String,
    val comparator: String,
    val threshold: Float,
    val severity: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object CustomRuleMetric {
    const val RISK_SCORE = "risk_score"
    const val DANGEROUS_PERMISSIONS = "dangerous_permissions"
    const val BACKGROUND_HOURS = "background_hours"
    const val MOBILE_MB = "mobile_mb"
}

object CustomRuleComparator {
    const val GT = ">"
    const val GTE = ">="
}
