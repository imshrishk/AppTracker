package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.WatchedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun watch(app: WatchedAppEntity)

    @Query("DELETE FROM watched_apps WHERE packageName = :packageName")
    suspend fun unwatch(packageName: String)

    @Query("SELECT * FROM watched_apps ORDER BY addedAt DESC")
    fun getAllWatched(): Flow<List<WatchedAppEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_apps WHERE packageName = :packageName)")
    fun isWatched(packageName: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM watched_apps")
    suspend fun getWatchCount(): Int

    @Query("SELECT * FROM watched_apps")
    suspend fun getWatchedList(): List<WatchedAppEntity>
}
