package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppOpsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AppOpsHistoryEntity>)

    @Query("SELECT * FROM app_ops_history WHERE packageName = :packageName ORDER BY lastAccessTime DESC")
    fun getOpsForPackage(packageName: String): Flow<List<AppOpsHistoryEntity>>

    @Query("SELECT * FROM app_ops_history ORDER BY lastAccessTime DESC LIMIT :limit")
    fun getRecentOps(limit: Int = 100): Flow<List<AppOpsHistoryEntity>>

    @Query("""
        SELECT * FROM app_ops_history 
        WHERE lastAccessTime >= :since 
        ORDER BY lastAccessTime DESC
    """)
    fun getOpsSince(since: Long): Flow<List<AppOpsHistoryEntity>>

    @Query("""
        SELECT * FROM app_ops_history 
        WHERE packageName = :packageName AND opName = :opName 
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun getLatestOp(packageName: String, opName: String): AppOpsHistoryEntity?

    @Query("DELETE FROM app_ops_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT DISTINCT packageName FROM app_ops_history")
    suspend fun getAllTrackedPackages(): List<String>
}
