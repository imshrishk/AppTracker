package com.apptracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.BatteryHistoryEntity
import com.apptracker.data.db.entity.NetworkHistoryEntity
import com.apptracker.data.db.entity.WatchedAppEntity

@Database(
    entities = [
        AppOpsHistoryEntity::class,
        BatteryHistoryEntity::class,
        NetworkHistoryEntity::class,
        WatchedAppEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppTrackerDatabase : RoomDatabase() {
    abstract fun appOpsDao(): AppOpsDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao
    abstract fun networkHistoryDao(): NetworkHistoryDao
    abstract fun watchedAppDao(): WatchedAppDao
}
