package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.ApkSnapshotEntity

@Dao
interface ApkSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ApkSnapshotEntity)

    @Query("SELECT * FROM apk_snapshots WHERE packageName = :packageName ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestForPackage(packageName: String): ApkSnapshotEntity?

    @Query("SELECT * FROM apk_snapshots WHERE packageName = :packageName AND versionCode != :currentVersionCode ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestDifferentVersion(packageName: String, currentVersionCode: Long): ApkSnapshotEntity?

    @Query("DELETE FROM apk_snapshots WHERE capturedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
