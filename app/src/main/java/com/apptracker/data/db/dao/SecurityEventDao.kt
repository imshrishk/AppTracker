package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SecurityEventEntity)

    @Query("SELECT * FROM security_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getRecentEvents(since: Long): Flow<List<SecurityEventEntity>>

    @Query("SELECT COUNT(*) FROM security_events WHERE type = :type AND timestamp >= :since")
    suspend fun countByTypeSince(type: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM security_events WHERE timestamp >= :since")
    suspend fun countAllSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM security_events WHERE type = :type AND packageName = :packageName AND timestamp >= :since")
    suspend fun countEventForPackageSince(type: String, packageName: String, since: Long): Int

    @Query("DELETE FROM security_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
