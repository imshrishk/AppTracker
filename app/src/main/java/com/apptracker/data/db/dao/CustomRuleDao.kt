package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.CustomRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CustomRuleEntity)

    @Delete
    suspend fun delete(rule: CustomRuleEntity)

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<CustomRuleEntity>

    @Query("UPDATE custom_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
