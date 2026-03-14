package com.apptracker.di

import android.content.Context
import androidx.room.Room
import com.apptracker.data.db.AppTrackerDatabase
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.AppTrustLabelDao
import com.apptracker.data.db.dao.ApkSnapshotDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.CustomRuleDao
import com.apptracker.data.db.dao.DeviceHealthSnapshotDao
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.PermissionSnapshotDao
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.repository.TrackerDomainRepository
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

    @Provides
    fun providePermissionSnapshotDao(db: AppTrackerDatabase): PermissionSnapshotDao = db.permissionSnapshotDao()

    @Provides
    fun provideSecurityEventDao(db: AppTrackerDatabase): SecurityEventDao = db.securityEventDao()

    @Provides
    fun provideAppTrustLabelDao(db: AppTrackerDatabase): AppTrustLabelDao = db.appTrustLabelDao()

    @Provides
    fun provideDnsQueryDao(db: AppTrackerDatabase): DnsQueryDao = db.dnsQueryDao()

    @Provides
    fun provideDeviceHealthSnapshotDao(db: AppTrackerDatabase): DeviceHealthSnapshotDao = db.deviceHealthSnapshotDao()

    @Provides
    fun provideApkSnapshotDao(db: AppTrackerDatabase): ApkSnapshotDao = db.apkSnapshotDao()

    @Provides
    fun provideCustomRuleDao(db: AppTrackerDatabase): CustomRuleDao = db.customRuleDao()

    @Provides
    @Singleton
    fun provideTrackerDomainRepository(): TrackerDomainRepository = TrackerDomainRepository()
}
