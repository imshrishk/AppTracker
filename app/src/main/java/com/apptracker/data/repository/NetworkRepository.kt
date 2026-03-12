package com.apptracker.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.entity.NetworkHistoryEntity
import com.apptracker.data.model.NetworkUsageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkHistoryDao: NetworkHistoryDao
) {
    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    suspend fun getNetworkUsage(
        packageName: String,
        startTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
        endTime: Long = System.currentTimeMillis()
    ): NetworkUsageInfo? = withContext(Dispatchers.IO) {
        val uid = getUidForPackage(packageName) ?: return@withContext null

        var wifiRx = 0L; var wifiTx = 0L
        var mobileRx = 0L; var mobileTx = 0L
        var wifiRxPkt = 0L; var wifiTxPkt = 0L
        var mobileRxPkt = 0L; var mobileTxPkt = 0L
        var fgBytes = 0L; var bgBytes = 0L

        // WiFi stats
        try {
            val wifiStats = networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_WIFI, null, startTime, endTime, uid
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                wifiRx += bucket.rxBytes
                wifiTx += bucket.txBytes
                wifiRxPkt += bucket.rxPackets
                wifiTxPkt += bucket.txPackets
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                    fgBytes += bucket.rxBytes + bucket.txBytes
                } else {
                    bgBytes += bucket.rxBytes + bucket.txBytes
                }
            }
            wifiStats.close()
        } catch (_: Exception) { }

        // Mobile data stats
        try {
            val subscriberId = getSubscriberId()
            val mobileStats = networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE, subscriberId, startTime, endTime, uid
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                mobileRx += bucket.rxBytes
                mobileTx += bucket.txBytes
                mobileRxPkt += bucket.rxPackets
                mobileTxPkt += bucket.txPackets
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                    fgBytes += bucket.rxBytes + bucket.txBytes
                } else {
                    bgBytes += bucket.rxBytes + bucket.txBytes
                }
            }
            mobileStats.close()
        } catch (_: Exception) { }

        val total = wifiRx + wifiTx + mobileRx + mobileTx
        if (total == 0L) return@withContext null

        NetworkUsageInfo(
            packageName = packageName,
            wifiRxBytes = wifiRx,
            wifiTxBytes = wifiTx,
            mobileRxBytes = mobileRx,
            mobileTxBytes = mobileTx,
            wifiRxPackets = wifiRxPkt,
            wifiTxPackets = wifiTxPkt,
            mobileRxPackets = mobileRxPkt,
            mobileTxPackets = mobileTxPkt,
            foregroundBytes = fgBytes,
            backgroundBytes = bgBytes,
            startTime = startTime,
            endTime = endTime
        )
    }

    @Suppress("DEPRECATION")
    suspend fun getAllNetworkUsage(
        startTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    ): Map<String, NetworkUsageInfo> = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        val result = mutableMapOf<String, NetworkUsageInfo>()

        // Query WiFi summary
        try {
            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI, null, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                val pkgName = getPackageForUid(bucket.uid) ?: continue
                val existing = result[pkgName]
                result[pkgName] = NetworkUsageInfo(
                    packageName = pkgName,
                    wifiRxBytes = (existing?.wifiRxBytes ?: 0) + bucket.rxBytes,
                    wifiTxBytes = (existing?.wifiTxBytes ?: 0) + bucket.txBytes,
                    mobileRxBytes = existing?.mobileRxBytes ?: 0,
                    mobileTxBytes = existing?.mobileTxBytes ?: 0,
                    wifiRxPackets = (existing?.wifiRxPackets ?: 0) + bucket.rxPackets,
                    wifiTxPackets = (existing?.wifiTxPackets ?: 0) + bucket.txPackets,
                    mobileRxPackets = existing?.mobileRxPackets ?: 0,
                    mobileTxPackets = existing?.mobileTxPackets ?: 0,
                    foregroundBytes = (existing?.foregroundBytes ?: 0) +
                            if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND)
                                bucket.rxBytes + bucket.txBytes else 0,
                    backgroundBytes = (existing?.backgroundBytes ?: 0) +
                            if (bucket.state != NetworkStats.Bucket.STATE_FOREGROUND)
                                bucket.rxBytes + bucket.txBytes else 0,
                    startTime = startTime,
                    endTime = endTime
                )
            }
            wifiStats.close()
        } catch (_: Exception) { }

        result
    }

    suspend fun saveNetworkSnapshot(usages: Map<String, NetworkUsageInfo>) {
        val entities = usages.map { (_, usage) ->
            NetworkHistoryEntity(
                packageName = usage.packageName,
                wifiRxBytes = usage.wifiRxBytes,
                wifiTxBytes = usage.wifiTxBytes,
                mobileRxBytes = usage.mobileRxBytes,
                mobileTxBytes = usage.mobileTxBytes,
                foregroundBytes = usage.foregroundBytes,
                backgroundBytes = usage.backgroundBytes
            )
        }
        networkHistoryDao.insertAll(entities)
    }

    fun getNetworkHistory(packageName: String): Flow<List<NetworkHistoryEntity>> =
        networkHistoryDao.getHistoryForPackage(packageName)

    private fun getUidForPackage(packageName: String): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                ).uid
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0).uid
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getPackageForUid(uid: Int): String? {
        val packages = packageManager.getPackagesForUid(uid)
        return packages?.firstOrNull()
    }

    @Suppress("MissingPermission")
    private fun getSubscriberId(): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            tm.subscriberId
        } catch (_: Exception) {
            null
        }
    }
}
