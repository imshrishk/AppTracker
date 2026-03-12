package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.NetworkHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NetworkHistoryEntity>)

    @Query("SELECT * FROM network_history WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getHistoryForPackage(packageName: String): Flow<List<NetworkHistoryEntity>>

    @Query("""
        SELECT * FROM network_history 
        WHERE timestamp >= :since 
        ORDER BY (wifiRxBytes + wifiTxBytes + mobileRxBytes + mobileTxBytes) DESC
    """)
    fun getUsageSince(since: Long): Flow<List<NetworkHistoryEntity>>

    @Query("DELETE FROM network_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
