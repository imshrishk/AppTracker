package com.apptracker.worker

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.ApkSnapshotDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.CustomRuleDao
import com.apptracker.data.db.dao.DeviceHealthSnapshotDao
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.PermissionSnapshotDao
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.ApkSnapshotEntity
import com.apptracker.data.db.entity.BatteryHistoryEntity
import com.apptracker.data.db.entity.CustomRuleComparator
import com.apptracker.data.db.entity.CustomRuleEntity
import com.apptracker.data.db.entity.CustomRuleMetric
import com.apptracker.data.db.entity.DeviceHealthSnapshotEntity
import com.apptracker.data.db.entity.NetworkHistoryEntity
import com.apptracker.data.db.entity.PermissionSnapshotEntity
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
import com.apptracker.data.model.AppCategory
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.data.repository.PermissionRepository
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.util.NotificationHelper
import com.apptracker.util.OnboardingPreferences
import com.apptracker.util.ApkFingerprintScanner
import com.apptracker.util.CertificatePinningStatus
import com.apptracker.util.DnsResolverCatalog
import com.apptracker.util.SecurityHeuristics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@HiltWorker
class DataRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val permissionRepository: PermissionRepository,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val appOpsDao: AppOpsDao,
    private val apkSnapshotDao: ApkSnapshotDao,
    private val batteryHistoryDao: BatteryHistoryDao,
    private val customRuleDao: CustomRuleDao,
    private val deviceHealthSnapshotDao: DeviceHealthSnapshotDao,
    private val dnsQueryDao: DnsQueryDao,
    private val networkHistoryDao: NetworkHistoryDao,
    private val permissionSnapshotDao: PermissionSnapshotDao,
    private val securityEventDao: SecurityEventDao,
    private val watchedAppDao: WatchedAppDao,
    private val calculateRiskScore: CalculateRiskScoreUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val pm = applicationContext.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val watchedPackageSet = watchedAppDao.getWatchedList().map { it.packageName }.toSet()
            val lastScanTimestamp = OnboardingPreferences.getLastScanTimestamp(applicationContext)
            val lastWeeklyDigestTimestamp = OnboardingPreferences.getLastWeeklyDigestTimestamp(applicationContext)
            val dnsLeakSensitivity = OnboardingPreferences.getDnsLeakSensitivity(applicationContext)
            val enabledRules = customRuleDao.getEnabledRules()

            // Snapshot battery usage
            val batteryMap = batteryRepository.getAllBatteryUsage()
            val batteryEntities = batteryMap.map { (pkg, battery) ->
                BatteryHistoryEntity(
                    packageName = pkg,
                    timestamp = now,
                    batteryPercent = battery.totalBatteryPercent,
                    foregroundTimeMs = battery.foregroundTimeMs,
                    backgroundTimeMs = battery.backgroundTimeMs,
                    foregroundServiceTimeMs = battery.foregroundServiceTimeMs,
                    wakelockTimeMs = battery.wakelockTimeMs,
                    alarmWakeups = battery.alarmWakeups
                )
            }
            if (batteryEntities.isNotEmpty()) {
                batteryHistoryDao.insertAll(batteryEntities)
            }

            // Snapshot network usage
            val networkMap = networkRepository.getAllNetworkUsage()
            val networkEntities = networkMap.map { (pkg, network) ->
                NetworkHistoryEntity(
                    packageName = pkg,
                    timestamp = now,
                    wifiRxBytes = network.wifiRxBytes,
                    wifiTxBytes = network.wifiTxBytes,
                    mobileRxBytes = network.mobileRxBytes,
                    mobileTxBytes = network.mobileTxBytes,
                    foregroundBytes = network.foregroundBytes,
                    backgroundBytes = network.backgroundBytes
                )
            }
            if (networkEntities.isNotEmpty()) {
                networkHistoryDao.insertAll(networkEntities)
            }

            // Snapshot App Ops
            val allOpsEntities = mutableListOf<AppOpsHistoryEntity>()
            for (pkgInfo in packages) {
                val appInfo = permissionRepository.getAppInfo(pkgInfo.packageName)
                if (appInfo != null) {
                    for (op in appInfo.appOpsEntries) {
                        val previous = appOpsDao.getLatestOp(pkgInfo.packageName, op.opName)
                        val changed = previous == null ||
                            op.lastAccessTime > previous.lastAccessTime ||
                            op.accessCount > previous.accessCount
                        val isSensitive = op.opName.contains("CAMERA", ignoreCase = true) ||
                            op.opName.contains("RECORD_AUDIO", ignoreCase = true) ||
                            op.opName.contains("LOCATION", ignoreCase = true) ||
                            op.opName.contains("SENSOR", ignoreCase = true) ||
                            op.opName.contains("CLIPBOARD", ignoreCase = true)
                        val recentlyAccessed = op.lastAccessTime >= now - TimeUnit.HOURS.toMillis(6)

                        if (changed && isSensitive && recentlyAccessed && !op.mode.name.equals("FOREGROUND", ignoreCase = true)) {
                            NotificationHelper.sendSensitiveAppOpsAlert(
                                context = applicationContext,
                                appName = appInfo.appName,
                                packageName = pkgInfo.packageName,
                                opName = op.opName
                            )
                            securityEventDao.insert(
                                SecurityEventEntity(
                                    type = SecurityEventType.SENSITIVE_APPOPS,
                                    packageName = pkgInfo.packageName,
                                    title = "Sensitive App Ops event",
                                    detail = op.opName
                                )
                            )
                        }

                        allOpsEntities.add(
                            AppOpsHistoryEntity(
                                packageName = pkgInfo.packageName,
                                opName = op.opName,
                                opCode = op.opCode,
                                mode = op.mode.name,
                                lastAccessTime = op.lastAccessTime,
                                lastRejectTime = op.lastRejectTime,
                                duration = op.duration,
                                accessCount = op.accessCount,
                                rejectCount = op.rejectCount,
                                timestamp = now
                            )
                        )
                    }
                }
            }
            if (allOpsEntities.isNotEmpty()) {
                appOpsDao.insertAll(allOpsEntities)
            }

            // Cleanup old data (keep 90 days)
            val cutoff = now - TimeUnit.DAYS.toMillis(90)
            appOpsDao.deleteOlderThan(cutoff)
            batteryHistoryDao.deleteOlderThan(cutoff)
            networkHistoryDao.deleteOlderThan(cutoff)

            // Risk score alerts — notify for newly high-risk apps
            NotificationHelper.createChannels(applicationContext)
            val highRiskApps = packages.mapNotNull { pkgInfo ->
                permissionRepository.getAppInfo(pkgInfo.packageName)
            }

            val certDigestToApps = highRiskApps
                .filter { !it.isSystemApp }
                .mapNotNull { app ->
                    getSigningDigest(pm, app.packageName)?.let { digest -> digest to app }
                }
                .groupBy({ it.first }, { it.second })

            val highRiskFiltered = highRiskApps.filter { appInfo ->
                calculateRiskScore(appInfo).overallScore >= 70
            }

            for (appInfo in highRiskApps) {
                val dangerousGranted = appInfo.permissions
                    .filter { it.isDangerous && it.isGranted }
                    .map { it.permissionName }
                    .sorted()

                val previous = permissionSnapshotDao.getLatestForPackage(appInfo.packageName)
                var addedCount = 0
                if (previous != null) {
                    val previousSet = previous.dangerousPermissionsCsv
                        .split("|")
                        .filter { it.isNotBlank() }
                        .toSet()
                    val currentSet = dangerousGranted.toSet()
                    val added = (currentSet - previousSet).toList()
                    val removed = (previousSet - currentSet).toList()
                    addedCount = added.size

                    if (added.isNotEmpty()) {
                        NotificationHelper.sendPermissionDeltaAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName,
                            addedDangerousPermissions = added
                        )
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.PERMISSION_DELTA,
                                packageName = appInfo.packageName,
                                title = "Dangerous permissions added",
                                detail = added.joinToString(", ")
                            )
                        )

                        if (previous.versionCode == appInfo.versionCode) {
                            securityEventDao.insert(
                                SecurityEventEntity(
                                    type = SecurityEventType.DARK_PATTERN,
                                    packageName = appInfo.packageName,
                                    title = "Permission re-request pattern",
                                    detail = "Permissions re-granted without app update: ${added.joinToString(", ")}"
                                )
                            )
                        }
                    }

                    if (removed.isNotEmpty() && previous.versionCode == appInfo.versionCode) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.AUTO_REVOKE,
                                packageName = appInfo.packageName,
                                title = "Permissions auto-revoked/reset",
                                detail = removed.joinToString(", ")
                            )
                        )
                    }
                }

                val currentRiskScore = calculateRiskScore(appInfo).overallScore

                // Custom rules engine
                enabledRules.forEach { rule ->
                    if (isRuleTriggered(rule, appInfo, currentRiskScore)) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.CUSTOM_RULE_TRIGGERED,
                                packageName = appInfo.packageName,
                                title = "Custom rule triggered: ${rule.name}",
                                detail = "${rule.metric} ${rule.comparator} ${rule.threshold}"
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Custom Rule Match",
                            detail = "${appInfo.appName}: ${rule.name}",
                            packageName = appInfo.packageName
                        )
                    }
                }

                // Watchlist change detection
                if (previous != null && watchedPackageSet.contains(appInfo.packageName)) {
                    val prevRisk = previous.riskScore
                    val prevPermCount = previous.dangerousPermissionsCsv.split("|").count { it.isNotBlank() }
                    val currentPermCount = dangerousGranted.size
                    if (abs(currentRiskScore - prevRisk) >= 5 || currentPermCount != prevPermCount) {
                        val changeDetail = buildString {
                            if (abs(currentRiskScore - prevRisk) >= 5)
                                append("Risk $prevRisk→$currentRiskScore")
                            if (currentPermCount != prevPermCount) {
                                if (isNotEmpty()) append(", ")
                                append("Perms $prevPermCount→$currentPermCount")
                            }
                        }
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.WATCHLIST_CHANGE,
                                packageName = appInfo.packageName,
                                title = "Watched app state changed",
                                detail = changeDetail
                            )
                        )
                        NotificationHelper.sendWatchlistChangeAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName,
                            changeDetail = changeDetail
                        )
                    }
                }

                // Permission spike: 3+ dangerous permissions added in 24h
                val twentyFourHoursAgo = now - TimeUnit.HOURS.toMillis(24)
                val addedIn24h = permissionSnapshotDao.getSumAddedCountSince(appInfo.packageName, twentyFourHoursAgo) + addedCount
                if (addedIn24h >= 3) {
                    val recentSpike = securityEventDao.countEventForPackageSince(
                        SecurityEventType.PERMISSION_SPIKE, appInfo.packageName, twentyFourHoursAgo
                    )
                    if (recentSpike == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.PERMISSION_SPIKE,
                                packageName = appInfo.packageName,
                                title = "Permission spike",
                                detail = "$addedIn24h dangerous permissions added in 24h"
                            )
                        )
                        NotificationHelper.sendPermissionSpikeAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName,
                            count = addedIn24h
                        )
                    }
                }

                // Burst network detection
                val sixHoursAgo = now - TimeUnit.HOURS.toMillis(6)
                val currentNetBytes = (networkMap[appInfo.packageName]?.totalBytes ?: 0L)
                val prevNetHistory = networkHistoryDao.getLatestBeforeTimestamp(appInfo.packageName, sixHoursAgo)
                val prevNetBytes = prevNetHistory?.let {
                    it.wifiRxBytes + it.wifiTxBytes + it.mobileRxBytes + it.mobileTxBytes
                } ?: 0L
                val isLikelyStreamingApp = appInfo.category == AppCategory.MEDIA ||
                    appInfo.category == AppCategory.BROWSER
                if (!isLikelyStreamingApp && prevNetBytes > 0 && currentNetBytes > prevNetBytes * 10 && currentNetBytes > 50L * 1024 * 1024) {
                    val recentBurst = securityEventDao.countEventForPackageSince(
                        SecurityEventType.BURST_NETWORK, appInfo.packageName, sixHoursAgo
                    )
                    if (recentBurst == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.BURST_NETWORK,
                                packageName = appInfo.packageName,
                                title = "Burst network activity",
                                detail = "${currentNetBytes / 1_048_576}MB vs ${prevNetBytes / 1_048_576}MB"
                            )
                        )
                        NotificationHelper.sendBurstNetworkAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName,
                            currentBytes = currentNetBytes,
                            previousBytes = prevNetBytes
                        )
                    }
                }

                // Fake GPS detection (heuristic)
                if (SecurityHeuristics.isFakeGpsLikely(appInfo)) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.FAKE_GPS,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(7)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.FAKE_GPS,
                                packageName = appInfo.packageName,
                                title = "Mock location signal detected",
                                detail = "Potential fake GPS capability or mock provider usage"
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Fake GPS Risk",
                            detail = "${appInfo.appName} shows mock-location signals.",
                            packageName = appInfo.packageName
                        )
                    }
                }

                // Accessibility abuse watchdog (heuristic)
                if (SecurityHeuristics.hasAccessibilityWatchdogSignal(appInfo)) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.ACCESSIBILITY_WATCHDOG,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(3)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.ACCESSIBILITY_WATCHDOG,
                                packageName = appInfo.packageName,
                                title = "Accessibility capability active",
                                detail = "App can leverage AccessibilityService-level interaction"
                            )
                        )
                    }
                }

                // Screen recording detector (capability heuristic)
                if (SecurityHeuristics.hasScreenRecordingSignal(appInfo)) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.SCREEN_RECORDING,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(7)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.SCREEN_RECORDING,
                                packageName = appInfo.packageName,
                                title = "Screen-capture capability",
                                detail = "App exposes MediaProjection / screen capture signals"
                            )
                        )
                    }
                }

                // Keylogger composite risk scorer
                val keyloggerScore = SecurityHeuristics.keyloggerCompositeScore(appInfo)
                if (keyloggerScore >= 75) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.KEYLOGGER_RISK,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(7)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.KEYLOGGER_RISK,
                                packageName = appInfo.packageName,
                                title = "Critical keylogger-like permission mix",
                                detail = "Composite risk score: $keyloggerScore/100"
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Critical Input-Capture Risk",
                            detail = "${appInfo.appName} has accessibility/overlay/input signals typical of keylogging abuse.",
                            packageName = appInfo.packageName
                        )
                    }
                }

                // Hidden process scanner (heuristic)
                if (SecurityHeuristics.hiddenProcessRisk(appInfo)) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.HIDDEN_PROCESS,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(7)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.HIDDEN_PROCESS,
                                packageName = appInfo.packageName,
                                title = "Hidden background process detected",
                                detail = "Boot auto-start + background execution + low foreground usage pattern"
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Hidden Process Risk",
                            detail = "${appInfo.appName} appears to run a persistent hidden background process.",
                            packageName = appInfo.packageName
                        )
                    }
                }

                val shouldInspectPinning =
                    !appInfo.isSystemApp &&
                        dangerousGranted.size >= 3 &&
                        (appInfo.networkUsage?.totalBytes ?: 0L) > 10_485_760L
                if (shouldInspectPinning) {
                    val pinningInspection = ApkFingerprintScanner.inspectCertificatePinning(appInfo.sourceDir)
                    if (pinningInspection.status == CertificatePinningStatus.NO_EVIDENCE) {
                        val seen = securityEventDao.countEventForPackageSince(
                            SecurityEventType.CERTIFICATE_PINNING,
                            appInfo.packageName,
                            now - TimeUnit.DAYS.toMillis(14)
                        )
                        if (seen == 0) {
                            securityEventDao.insert(
                                SecurityEventEntity(
                                    type = SecurityEventType.CERTIFICATE_PINNING,
                                    packageName = appInfo.packageName,
                                    title = "Certificate pinning not verified",
                                    detail = "Quick APK scan found no certificate pinning evidence"
                                )
                            )
                            NotificationHelper.sendSecurityHeuristicAlert(
                                context = applicationContext,
                                title = "Certificate Pinning Unverified",
                                detail = "${appInfo.appName} handles significant network traffic but the quick APK scan found no pinning evidence.",
                                packageName = appInfo.packageName
                            )
                        }
                    }
                }

                // App impersonation detector (brand mimic heuristic)
                val impersonationReason = SecurityHeuristics.looksLikeImpersonation(appInfo)
                if (impersonationReason != null) {
                    val seen = securityEventDao.countEventForPackageSince(
                        SecurityEventType.APP_IMPERSONATION,
                        appInfo.packageName,
                        now - TimeUnit.DAYS.toMillis(14)
                    )
                    if (seen == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.APP_IMPERSONATION,
                                packageName = appInfo.packageName,
                                title = "Potential app impersonation",
                                detail = impersonationReason
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Impersonation Warning",
                            detail = "${appInfo.appName}: $impersonationReason",
                            packageName = appInfo.packageName
                        )
                    }
                }

                permissionSnapshotDao.insert(
                    PermissionSnapshotEntity(
                        packageName = appInfo.packageName,
                        versionCode = appInfo.versionCode,
                        dangerousPermissionsCsv = dangerousGranted.joinToString("|"),
                        addedDangerousCount = addedCount,
                        riskScore = currentRiskScore,
                        capturedAt = now
                    )
                )

                val signingDigest = getSigningDigest(pm, appInfo.packageName)
                val apkSizeBytes = appInfo.sourceDir?.let { path ->
                    runCatching { File(path).length() }.getOrDefault(0L)
                } ?: 0L
                if (signingDigest != null && apkSizeBytes > 0L) {
                    apkSnapshotDao.insert(
                        ApkSnapshotEntity(
                            packageName = appInfo.packageName,
                            versionCode = appInfo.versionCode,
                            apkSizeBytes = apkSizeBytes,
                            signingDigestSha256 = signingDigest,
                            capturedAt = now
                        )
                    )
                }
            }

            // Cross-app collusion detection (shared signing cert + overlapping sensitive perms)
            val collusionWindow = now - TimeUnit.DAYS.toMillis(7)
            certDigestToApps.values.forEach { sameSignerApps ->
                if (sameSignerApps.size < 2) return@forEach
                for (i in 0 until sameSignerApps.lastIndex) {
                    for (j in i + 1 until sameSignerApps.size) {
                        val a = sameSignerApps[i]
                        val b = sameSignerApps[j]
                        val overlap = overlappingSensitivePerms(a, b)
                        if (overlap.size >= 2) {
                            val existingA = securityEventDao.countEventForPackageSince(
                                SecurityEventType.CROSS_APP_COLLUSION,
                                a.packageName,
                                collusionWindow
                            )
                            if (existingA == 0) {
                                val detailA = "Shared signer with ${b.appName}; overlap: ${overlap.take(4).joinToString(", ")}" 
                                securityEventDao.insert(
                                    SecurityEventEntity(
                                        type = SecurityEventType.CROSS_APP_COLLUSION,
                                        packageName = a.packageName,
                                        title = "Cross-app collusion signal",
                                        detail = detailA
                                    )
                                )
                            }

                            val existingB = securityEventDao.countEventForPackageSince(
                                SecurityEventType.CROSS_APP_COLLUSION,
                                b.packageName,
                                collusionWindow
                            )
                            if (existingB == 0) {
                                val detailB = "Shared signer with ${a.appName}; overlap: ${overlap.take(4).joinToString(", ")}" 
                                securityEventDao.insert(
                                    SecurityEventEntity(
                                        type = SecurityEventType.CROSS_APP_COLLUSION,
                                        packageName = b.packageName,
                                        title = "Cross-app collusion signal",
                                        detail = detailB
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..4) {
                highRiskApps.forEach { appInfo ->
                    val networkTotal = appInfo.networkUsage?.totalBytes ?: 0L
                    val backgroundMs = appInfo.batteryUsage?.backgroundTimeMs ?: 0L
                    if (networkTotal >= 25L * 1024 * 1024 || backgroundMs >= 45L * 60L * 1000L) {
                        NotificationHelper.sendNightActivityAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName,
                            networkBytes = networkTotal,
                            backgroundMs = backgroundMs
                        )
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.NIGHT_ACTIVITY,
                                packageName = appInfo.packageName,
                                title = "Night activity anomaly",
                                detail = "Network=${networkTotal}B, Background=${backgroundMs}ms"
                            )
                        )
                    }
                }
            }

            highRiskFiltered.take(3).forEach { appInfo ->
                val score = calculateRiskScore(appInfo)
                NotificationHelper.sendHighRiskAlert(
                    context = applicationContext,
                    appName = appInfo.appName,
                    packageName = appInfo.packageName,
                    riskScore = score.overallScore
                )
            }
            NotificationHelper.sendRefreshSummary(
                context = applicationContext,
                appsScanned = packages.size,
                highRiskCount = highRiskFiltered.size
            )

            // New app install alerts — apps installed since last scan
            if (lastScanTimestamp > 0) {
                val newInstalls = highRiskApps.filter {
                    it.installTime in (lastScanTimestamp + 1)..now && !it.isSystemApp
                }
                for (appInfo in newInstalls) {
                    val alreadyLogged = securityEventDao.countEventForPackageSince(
                        SecurityEventType.APP_INSTALL, appInfo.packageName,
                        now - TimeUnit.HOURS.toMillis(7)
                    )
                    if (alreadyLogged == 0) {
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.APP_INSTALL,
                                packageName = appInfo.packageName,
                                title = "New app installed",
                                detail = appInfo.installSourceLabel
                            )
                        )
                        NotificationHelper.sendNewAppInstallAlert(
                            context = applicationContext,
                            appName = appInfo.appName,
                            packageName = appInfo.packageName
                        )
                    }
                }
            }

            // Dormant app detection — installed 60+ days, no usage in 90 days, holds dangerous perms
            val ninetyDaysAgo = now - TimeUnit.DAYS.toMillis(90)
            val sixtyDaysAgo = now - TimeUnit.DAYS.toMillis(60)
            val usageMap90d = batteryRepository.getAllBatteryUsage(startTime = ninetyDaysAgo)
            val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
            for (appInfo in highRiskApps) {
                if (appInfo.isSystemApp) continue
                val installedBefore60Days = appInfo.installTime < sixtyDaysAgo
                val hasNoRecentUsage = !usageMap90d.containsKey(appInfo.packageName)
                val hasDangerousPerms = appInfo.permissions.any { it.isDangerous && it.isGranted }
                if (installedBefore60Days && hasNoRecentUsage && hasDangerousPerms) {
                    val recentDormant = securityEventDao.countEventForPackageSince(
                        SecurityEventType.DORMANT_APP, appInfo.packageName, sevenDaysAgo
                    )
                    if (recentDormant == 0) {
                        val permCount = appInfo.permissions.count { it.isDangerous && it.isGranted }
                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.DORMANT_APP,
                                packageName = appInfo.packageName,
                                title = "Dormant app with permissions",
                                detail = "$permCount dangerous permissions, no usage in 90+ days"
                            )
                        )
                    }
                }
            }

            // Device health degradation alert
            val appCount = highRiskApps.size
            if (appCount > 0) {
                val avgRisk = highRiskApps.sumOf { calculateRiskScore(it).overallScore } / appCount
                val highRiskCount = highRiskFiltered.size
                val dangerousCount = highRiskApps.sumOf { a -> a.permissions.count { it.isDangerous && it.isGranted } }
                val heavyBgCount = highRiskApps.count { (it.batteryUsage?.backgroundTimeMs ?: 0L) > TimeUnit.HOURS.toMillis(1) }
                val currentHealth = computeHealthScore(appCount, avgRisk, highRiskCount, dangerousCount, heavyBgCount)
                val previousHealth = OnboardingPreferences.getLastHealthScore(applicationContext)
                if (previousHealth >= 0 && (previousHealth - currentHealth) >= 10) {
                    securityEventDao.insert(
                        SecurityEventEntity(
                            type = SecurityEventType.HEALTH_DROP,
                            packageName = "",
                            title = "Device health dropped",
                            detail = "Score: $previousHealth\u2192$currentHealth"
                        )
                    )
                    NotificationHelper.sendHealthDegradationAlert(applicationContext, previousHealth, currentHealth)
                }
                deviceHealthSnapshotDao.insert(
                    DeviceHealthSnapshotEntity(
                        timestamp = now,
                        appCount = appCount,
                        healthScore = currentHealth,
                        averageRiskScore = avgRisk,
                        highRiskCount = highRiskCount,
                        dangerousPermissionCount = dangerousCount,
                        heavyBackgroundAppCount = heavyBgCount
                    )
                )
                OnboardingPreferences.captureBaselineIfMissing(
                    context = applicationContext,
                    healthScore = currentHealth,
                    appCount = appCount,
                    highRiskCount = highRiskCount,
                    dangerousPermissionCount = dangerousCount,
                    capturedAt = now
                )
                OnboardingPreferences.setLastHealthScore(applicationContext, currentHealth)

                val weeklyWindowStart = now - TimeUnit.DAYS.toMillis(7)
                val weeklyDigestDue =
                    lastWeeklyDigestTimestamp == 0L ||
                        now - lastWeeklyDigestTimestamp >= TimeUnit.DAYS.toMillis(7)
                if (weeklyDigestDue) {
                    val weeklyEventCount = securityEventDao.countAllSince(weeklyWindowStart)
                    val weeklyTrackerHits = dnsQueryDao.countTrackerHitsSince(weeklyWindowStart)
                    val weeklyDnsLeakSignals = securityEventDao.countByTypeSince(
                        SecurityEventType.DNS_LEAK,
                        weeklyWindowStart
                    )
                    val weeklyNewApps = securityEventDao.countByTypeSince(
                        SecurityEventType.APP_INSTALL,
                        weeklyWindowStart
                    )
                    val hasDigestContent =
                        weeklyEventCount > 0 || weeklyTrackerHits > 0 || weeklyDnsLeakSignals > 0 || weeklyNewApps > 0 || highRiskCount > 0
                    if (hasDigestContent) {
                        NotificationHelper.sendWeeklyDigest(
                            context = applicationContext,
                            eventCount = weeklyEventCount,
                            trackerHits = weeklyTrackerHits,
                            dnsLeakSignals = weeklyDnsLeakSignals,
                            newApps = weeklyNewApps,
                            highRiskApps = highRiskCount,
                            healthScore = currentHealth
                        )
                        OnboardingPreferences.setLastWeeklyDigestTimestamp(applicationContext, now)
                    }
                }

                val twentyFourHoursAgo = now - TimeUnit.DAYS.toMillis(1)
                val unattributedDnsQueries = dnsQueryDao.countUnattributedSince(twentyFourHoursAgo)
                val totalDnsQueries = dnsQueryDao.countTotalQueriesSince(twentyFourHoursAgo)
                val distinctResolvers = dnsQueryDao.countDistinctResolversSince(twentyFourHoursAgo)
                val nonMonitoredResolverQueries = dnsQueryDao.countNonMonitoredResolverQueriesSince(
                    twentyFourHoursAgo,
                    DnsResolverCatalog.MONITORED_RESOLVERS
                )
                val unattributedRatio = if (totalDnsQueries > 0) {
                    (unattributedDnsQueries * 100) / totalDnsQueries
                } else {
                    0
                }

                val (unattributedMin, ratioMin, nonMonitoredMin, distinctResolverMin) = when (dnsLeakSensitivity) {
                    1 -> listOf(8, 45, 3, 5)
                    3 -> listOf(3, 20, 1, 3)
                    else -> listOf(5, 35, 1, 4)
                }
                val sensitivityLabel = when (dnsLeakSensitivity) {
                    1 -> "Low"
                    3 -> "High"
                    else -> "Balanced"
                }

                val dnsLeakLikely =
                    (unattributedDnsQueries >= unattributedMin && unattributedRatio >= ratioMin) ||
                        nonMonitoredResolverQueries >= nonMonitoredMin ||
                        distinctResolvers >= distinctResolverMin

                if (dnsLeakLikely) {
                    val existingLeakEvents = securityEventDao.countByTypeSince(
                        SecurityEventType.DNS_LEAK,
                        twentyFourHoursAgo
                    )
                    if (existingLeakEvents == 0) {
                        val signalParts = mutableListOf<String>()
                        if (unattributedDnsQueries > 0) {
                            signalParts += "$unattributedDnsQueries unattributed (${unattributedRatio}% of DNS)"
                        }
                        if (nonMonitoredResolverQueries > 0) {
                            signalParts += "$nonMonitoredResolverQueries non-monitored resolver queries"
                        }
                        if (distinctResolvers >= distinctResolverMin) {
                            signalParts += "$distinctResolvers distinct resolvers"
                        }
                        signalParts += "sensitivity=$sensitivityLabel"

                        securityEventDao.insert(
                            SecurityEventEntity(
                                type = SecurityEventType.DNS_LEAK,
                                packageName = "",
                                title = "Potential DNS leak or bypass",
                                detail = signalParts.joinToString(" • ")
                            )
                        )
                        NotificationHelper.sendSecurityHeuristicAlert(
                            context = applicationContext,
                            title = "Potential DNS leak or bypass",
                            detail = "DNS leak signals detected: ${signalParts.joinToString(", ")}. Review DNS Activity for resolver bypass, encrypted DNS, or competing VPN traffic."
                        )
                    }
                }
            }

            OnboardingPreferences.setLastScanTimestamp(applicationContext, now)

            permissionSnapshotDao.deleteOlderThan(cutoff)
            securityEventDao.deleteOlderThan(cutoff)
            apkSnapshotDao.deleteOlderThan(cutoff)
            deviceHealthSnapshotDao.deleteOlderThan(cutoff)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun computeHealthScore(
        appCount: Int,
        avgRisk: Int,
        highRiskCount: Int,
        dangerousCount: Int,
        heavyBgCount: Int
    ): Int {
        if (appCount <= 0) return 100
        val riskPenalty = (avgRisk * 0.45f).toInt()
        val highRiskPenalty = ((highRiskCount.toFloat() / appCount) * 30f).toInt()
        val dangerousPenalty = (dangerousCount / appCount).coerceAtMost(15)
        val backgroundPenalty = ((heavyBgCount.toFloat() / appCount) * 10f).toInt()
        return (100 - (riskPenalty + highRiskPenalty + dangerousPenalty + backgroundPenalty)).coerceIn(0, 100)
    }

    private fun overlappingSensitivePerms(
        first: com.apptracker.data.model.AppInfo,
        second: com.apptracker.data.model.AppInfo
    ): Set<String> {
        val firstSensitive = first.permissions
            .filter { it.isDangerous && it.isGranted }
            .map { it.permissionName }
            .toSet()
        val secondSensitive = second.permissions
            .filter { it.isDangerous && it.isGranted }
            .map { it.permissionName }
            .toSet()
        return firstSensitive.intersect(secondSensitive)
    }

    private fun getSigningDigest(pm: PackageManager, packageName: String): String? {
        return try {
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            SecurityHeuristics.certificateDigestSha256(signatureBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun isRuleTriggered(rule: CustomRuleEntity, appInfo: com.apptracker.data.model.AppInfo, riskScore: Int): Boolean {
        val metricValue = when (rule.metric) {
            CustomRuleMetric.RISK_SCORE -> riskScore.toFloat()
            CustomRuleMetric.DANGEROUS_PERMISSIONS -> appInfo.permissions.count { it.isDangerous && it.isGranted }.toFloat()
            CustomRuleMetric.BACKGROUND_HOURS -> ((appInfo.batteryUsage?.backgroundTimeMs ?: 0L) / 3_600_000f)
            CustomRuleMetric.MOBILE_MB -> ((appInfo.networkUsage?.totalMobileBytes ?: 0L) / 1_048_576f)
            else -> return false
        }
        return when (rule.comparator) {
            CustomRuleComparator.GT -> metricValue > rule.threshold
            CustomRuleComparator.GTE -> metricValue >= rule.threshold
            else -> false
        }
    }

    companion object {
        private const val WORK_NAME = "app_tracker_data_refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataRefreshWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
