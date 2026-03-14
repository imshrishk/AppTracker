package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.PermissionSnapshotEntity

@Dao
interface PermissionSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PermissionSnapshotEntity)

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestForPackage(packageName: String): PermissionSnapshotEntity?

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName AND versionCode != :currentVersionCode ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestDifferentVersion(packageName: String, currentVersionCode: Long): PermissionSnapshotEntity?

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getRecentForPackage(packageName: String, limit: Int = 8): List<PermissionSnapshotEntity>

    @Query("SELECT COUNT(*) FROM permission_snapshots WHERE addedDangerousCount > 0 AND capturedAt >= :since")
    suspend fun countPermissionDeltaSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(addedDangerousCount), 0) FROM permission_snapshots WHERE packageName = :packageName")
    suspend fun getPermissionCreepIndex(packageName: String): Int

    @Query("SELECT COALESCE(SUM(addedDangerousCount), 0) FROM permission_snapshots WHERE packageName = :packageName AND capturedAt >= :since")
    suspend fun getSumAddedCountSince(packageName: String, since: Long): Int

    @Query("DELETE FROM permission_snapshots WHERE capturedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
