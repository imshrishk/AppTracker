package com.apptracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.AppTrustLabelDao
import com.apptracker.data.db.dao.ApkSnapshotDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.CustomRuleDao
import com.apptracker.data.db.dao.DeviceHealthSnapshotDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.PermissionSnapshotDao
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.entity.AppTrustLabelEntity
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.ApkSnapshotEntity
import com.apptracker.data.db.entity.BatteryHistoryEntity
import com.apptracker.data.db.entity.CustomRuleEntity
import com.apptracker.data.db.entity.DeviceHealthSnapshotEntity
import com.apptracker.data.db.entity.DnsQueryEntity
import com.apptracker.data.db.entity.NetworkHistoryEntity
import com.apptracker.data.db.entity.PermissionSnapshotEntity
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.WatchedAppEntity

@Database(
    entities = [
        AppOpsHistoryEntity::class,
        BatteryHistoryEntity::class,
        NetworkHistoryEntity::class,
        WatchedAppEntity::class,
        PermissionSnapshotEntity::class,
        SecurityEventEntity::class,
        AppTrustLabelEntity::class,
        DnsQueryEntity::class,
        DeviceHealthSnapshotEntity::class,
        ApkSnapshotEntity::class,
        CustomRuleEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppTrackerDatabase : RoomDatabase() {
    abstract fun appOpsDao(): AppOpsDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao
    abstract fun networkHistoryDao(): NetworkHistoryDao
    abstract fun watchedAppDao(): WatchedAppDao
    abstract fun permissionSnapshotDao(): PermissionSnapshotDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun appTrustLabelDao(): AppTrustLabelDao
    abstract fun dnsQueryDao(): DnsQueryDao
    abstract fun deviceHealthSnapshotDao(): DeviceHealthSnapshotDao
    abstract fun apkSnapshotDao(): ApkSnapshotDao
    abstract fun customRuleDao(): CustomRuleDao
}
