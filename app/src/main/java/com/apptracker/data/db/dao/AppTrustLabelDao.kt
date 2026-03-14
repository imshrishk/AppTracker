package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.AppTrustLabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppTrustLabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AppTrustLabelEntity)

    @Query("SELECT * FROM app_trust_labels")
    fun getAll(): Flow<List<AppTrustLabelEntity>>

    @Query("SELECT label FROM app_trust_labels WHERE packageName = :packageName LIMIT 1")
    fun getLabelForPackage(packageName: String): Flow<String?>
}
