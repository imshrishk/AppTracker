package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.BatteryHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BatteryHistoryEntity>)

    @Query("SELECT * FROM battery_history WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getHistoryForPackage(packageName: String): Flow<List<BatteryHistoryEntity>>

    @Query("""
        SELECT * FROM battery_history 
        WHERE timestamp >= :since 
        ORDER BY batteryPercent DESC
    """)
    fun getUsageSince(since: Long): Flow<List<BatteryHistoryEntity>>

    @Query("""
        SELECT packageName, AVG(batteryPercent) as avgBattery 
        FROM battery_history 
        WHERE timestamp >= :since 
        GROUP BY packageName 
        ORDER BY avgBattery DESC 
        LIMIT :limit
    """)
    suspend fun getTopBatteryConsumers(since: Long, limit: Int = 10): List<BatteryConsumerSummary>

    @Query("DELETE FROM battery_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class BatteryConsumerSummary(
    val packageName: String,
    val avgBattery: Double
)
