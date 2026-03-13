package com.apptracker.data.repository

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.AppCategoryDetector
import com.apptracker.data.model.AppOpsCategory
import com.apptracker.data.model.AppOpsEntry
import com.apptracker.data.model.AppOpsMode
import com.apptracker.data.model.PermissionDetail
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.data.model.StorageUsageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    private val appOpsManager: AppOpsManager =
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags)
            }

            packages
                .filter { pkg ->
                    includeSystem || (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0)
                }
                .map { pkg -> buildAppInfo(pkg) }
                .sortedBy { it.appName.lowercase() }
        }

    suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        try {
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
            buildAppInfo(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun buildAppInfo(pkg: PackageInfo): AppInfo {
        val appInfo = pkg.applicationInfo
        val appName = appInfo?.loadLabel(packageManager)?.toString() ?: pkg.packageName
        val isSystemApp = (appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
        val permissions = extractPermissions(pkg)
        val appOps = extractAppOps(pkg.packageName)

        return AppInfo(
            packageName = pkg.packageName,
            appName = appName,
            icon = appInfo?.loadIcon(packageManager),
            versionName = pkg.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            },
            targetSdkVersion = appInfo?.targetSdkVersion ?: 0,
            minSdkVersion = appInfo?.minSdkVersion ?: 0,
            installTime = pkg.firstInstallTime,
            lastUpdateTime = pkg.lastUpdateTime,
            isSystemApp = isSystemApp,
            isEnabled = appInfo?.enabled ?: false,
            dataDir = appInfo?.dataDir,
            sourceDir = appInfo?.sourceDir,
            category = AppCategoryDetector.infer(
                packageName = pkg.packageName,
                appName = appName,
                isSystemApp = isSystemApp
            ),
            permissions = permissions,
            appOpsEntries = appOps,
            storageUsage = queryStorageUsage(pkg.packageName)
        )
    }

    private fun queryStorageUsage(packageName: String): StorageUsageInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = storageStatsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle()
            )
            StorageUsageInfo(
                packageName = packageName,
                appSizeBytes = stats.appBytes,
                dataSizeBytes = stats.dataBytes,
                cacheSizeBytes = stats.cacheBytes,
                totalSizeBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPermissions(pkg: PackageInfo): List<PermissionDetail> {
        val requestedPermissions = pkg.requestedPermissions ?: return emptyList()
        val requestedFlags = pkg.requestedPermissionsFlags ?: IntArray(0)

        return requestedPermissions.mapIndexed { index, permissionName ->
            val isGranted = if (index < requestedFlags.size) {
                (requestedFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false

            val permInfo = try {
                packageManager.getPermissionInfo(permissionName, PackageManager.GET_META_DATA)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            val protectionLevel = getProtectionLevel(permInfo)

            PermissionDetail(
                permissionName = permissionName,
                group = permInfo?.group,
                protectionLevel = protectionLevel,
                isGranted = isGranted,
                flags = if (index < requestedFlags.size) requestedFlags[index] else 0,
                description = permInfo?.loadDescription(packageManager)?.toString(),
                label = permInfo?.loadLabel(packageManager)?.toString(),
                isDangerous = protectionLevel == ProtectionLevel.DANGEROUS
            )
        }
    }

    private fun getProtectionLevel(permInfo: PermissionInfo?): ProtectionLevel {
        if (permInfo == null) return ProtectionLevel.UNKNOWN
        val baseProtection = permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        val flags = permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_FLAGS
        return when {
            baseProtection == PermissionInfo.PROTECTION_NORMAL -> ProtectionLevel.NORMAL
            baseProtection == PermissionInfo.PROTECTION_DANGEROUS -> ProtectionLevel.DANGEROUS
            baseProtection == PermissionInfo.PROTECTION_SIGNATURE &&
                    flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0 ->
                ProtectionLevel.SIGNATURE_OR_SYSTEM
            baseProtection == PermissionInfo.PROTECTION_SIGNATURE -> ProtectionLevel.SIGNATURE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    baseProtection == PermissionInfo.PROTECTION_INTERNAL -> ProtectionLevel.INTERNAL
            else -> ProtectionLevel.UNKNOWN
        }
    }

    @Suppress("DEPRECATION")
    private fun extractAppOps(packageName: String): List<AppOpsEntry> {
        val entries = mutableListOf<AppOpsEntry>()
        val opsToCheck = getOpsToCheck()

        for (opName in opsToCheck) {
            try {
                val uid = getUidForPackage(packageName)
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOpsManager.unsafeCheckOpNoThrow(opName, uid, packageName)
                } else {
                    @Suppress("DEPRECATION")
                    appOpsManager.checkOpNoThrow(opName, uid, packageName)
                }

                entries.add(
                    AppOpsEntry(
                        opName = opName,
                        opCode = opName.hashCode(),
                        mode = mapMode(mode),
                        lastAccessTime = 0, // Requires deeper API access
                        lastRejectTime = 0,
                        duration = 0,
                        accessCount = 0,
                        rejectCount = 0,
                        proxyPackageName = null,
                        category = categorizeOp(opName)
                    )
                )
            } catch (_: Exception) {
                // Skip ops that aren't accessible
            }
        }
        return entries
    }

    private fun getUidForPackage(packageName: String): Int {
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
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    private fun mapMode(mode: Int): AppOpsMode = when (mode) {
        AppOpsManager.MODE_ALLOWED -> AppOpsMode.ALLOWED
        AppOpsManager.MODE_IGNORED -> AppOpsMode.IGNORED
        AppOpsManager.MODE_ERRORED -> AppOpsMode.ERRORED
        AppOpsManager.MODE_DEFAULT -> AppOpsMode.DEFAULT
        AppOpsManager.MODE_FOREGROUND -> AppOpsMode.FOREGROUND
        else -> AppOpsMode.UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun getOpsToCheck(): List<String> = listOf(
        AppOpsManager.OPSTR_CAMERA,
        AppOpsManager.OPSTR_RECORD_AUDIO,
        AppOpsManager.OPSTR_READ_CONTACTS,
        AppOpsManager.OPSTR_WRITE_CONTACTS,
        AppOpsManager.OPSTR_READ_CALL_LOG,
        AppOpsManager.OPSTR_WRITE_CALL_LOG,
        AppOpsManager.OPSTR_FINE_LOCATION,
        AppOpsManager.OPSTR_COARSE_LOCATION,
        AppOpsManager.OPSTR_READ_CALENDAR,
        AppOpsManager.OPSTR_WRITE_CALENDAR,
        AppOpsManager.OPSTR_READ_SMS,
        AppOpsManager.OPSTR_RECEIVE_SMS,
        AppOpsManager.OPSTR_SEND_SMS,
        AppOpsManager.OPSTR_READ_PHONE_STATE,
        AppOpsManager.OPSTR_CALL_PHONE,
        AppOpsManager.OPSTR_BODY_SENSORS,
        AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE,
        AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE,
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
        AppOpsManager.OPSTR_WRITE_SETTINGS
    )

    private fun categorizeOp(opName: String): AppOpsCategory {
        return when {
            opName.contains("LOCATION", ignoreCase = true) ||
                    opName.contains("GPS", ignoreCase = true) -> AppOpsCategory.LOCATION
            opName.contains("CAMERA", ignoreCase = true) -> AppOpsCategory.CAMERA
            opName.contains("AUDIO", ignoreCase = true) ||
                    opName.contains("RECORD", ignoreCase = true) ||
                    opName.contains("MICROPHONE", ignoreCase = true) -> AppOpsCategory.MICROPHONE
            opName.contains("CONTACT", ignoreCase = true) -> AppOpsCategory.CONTACTS
            opName.contains("PHONE", ignoreCase = true) ||
                    opName.contains("CALL", ignoreCase = true) -> AppOpsCategory.PHONE
            opName.contains("STORAGE", ignoreCase = true) ||
                    opName.contains("EXTERNAL", ignoreCase = true) -> AppOpsCategory.STORAGE
            opName.contains("CALENDAR", ignoreCase = true) -> AppOpsCategory.CALENDAR
            opName.contains("SMS", ignoreCase = true) -> AppOpsCategory.SMS
            opName.contains("SENSOR", ignoreCase = true) -> AppOpsCategory.SENSORS
            opName.contains("CLIPBOARD", ignoreCase = true) -> AppOpsCategory.CLIPBOARD
            opName.contains("NOTIFICATION", ignoreCase = true) -> AppOpsCategory.NOTIFICATIONS
            else -> AppOpsCategory.OTHER
        }
    }
}
