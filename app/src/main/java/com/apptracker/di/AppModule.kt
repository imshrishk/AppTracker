package com.apptracker.di

import android.content.Context
import androidx.room.Room
import com.apptracker.data.db.AppTrackerDatabase
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.WatchedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppTrackerDatabase {
        return Room.databaseBuilder(
            context,
            AppTrackerDatabase::class.java,
            "apptracker.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideAppOpsDao(db: AppTrackerDatabase): AppOpsDao = db.appOpsDao()

    @Provides
    fun provideBatteryHistoryDao(db: AppTrackerDatabase): BatteryHistoryDao = db.batteryHistoryDao()

    @Provides
    fun provideNetworkHistoryDao(db: AppTrackerDatabase): NetworkHistoryDao = db.networkHistoryDao()

    @Provides
    fun provideWatchedAppDao(db: AppTrackerDatabase): WatchedAppDao = db.watchedAppDao()
}
