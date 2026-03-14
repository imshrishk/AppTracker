package com.apptracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "apk_snapshots",
    indices = [
        Index(value = ["packageName", "capturedAt"]),
        Index(value = ["packageName", "versionCode"])
    ]
)
data class ApkSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val versionCode: Long,
    val apkSizeBytes: Long,
    val signingDigestSha256: String,
    val capturedAt: Long = System.currentTimeMillis()
)
