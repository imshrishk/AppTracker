package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.DeviceHealthSnapshotEntity

@Dao
interface DeviceHealthSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DeviceHealthSnapshotEntity)

    @Query("SELECT * FROM device_health_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<DeviceHealthSnapshotEntity>

    @Query("SELECT * FROM device_health_snapshots WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getSnapshotsSince(since: Long): List<DeviceHealthSnapshotEntity>

    @Query("SELECT * FROM device_health_snapshots WHERE timestamp < :before ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBefore(before: Long): DeviceHealthSnapshotEntity?

    @Query("DELETE FROM device_health_snapshots WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
