package com.apptracker.ui.screens.appdetail

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.AppTrustLabelDao
import com.apptracker.data.db.dao.ApkSnapshotDao
import com.apptracker.data.db.dao.PermissionSnapshotDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.AppTrustLabelEntity
import com.apptracker.data.db.entity.TrustLabel
import com.apptracker.data.db.entity.WatchedAppEntity
import com.apptracker.data.model.AppCategory
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.domain.usecase.GenerateReportUseCase
import com.apptracker.domain.usecase.GetAppDetailUseCase
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.ApkFingerprintScanner
import com.apptracker.util.CertificatePinningInspection
import com.apptracker.util.LogcatReader
import com.apptracker.util.OnboardingPreferences
import com.apptracker.util.PrivacyScoreUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAppDetail: GetAppDetailUseCase,
    private val calculateRiskScore: CalculateRiskScoreUseCase,
    private val generateReport: GenerateReportUseCase,
    private val watchedAppDao: WatchedAppDao,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val appOpsDao: AppOpsDao,
    private val permissionSnapshotDao: PermissionSnapshotDao,
    private val appTrustLabelDao: AppTrustLabelDao,
    private val apkSnapshotDao: ApkSnapshotDao,
    private val getInstalledApps: GetInstalledAppsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName") ?: ""

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    val isWatched: StateFlow<Boolean> = watchedAppDao.isWatched(packageName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        observePreferences()
        loadAppDetail()
        loadAllApps()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.beginnerMode(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(isBeginnerMode = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.onDeviceOnly(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(onDeviceOnly = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.defaultUsageRange(context).collectLatest { range ->
                val previous = _uiState.value.selectedRange
                _uiState.value = _uiState.value.copy(selectedRange = range)
                if (previous != range) {
                    loadAppDetail(range)
                }
            }
        }
        viewModelScope.launch {
            appTrustLabelDao.getLabelForPackage(packageName).collectLatest { label ->
                _uiState.value = _uiState.value.copy(trustLabel = label ?: TrustLabel.UNKNOWN)
            }
        }
    }

    fun loadAppDetail(range: UsageTimeRange = _uiState.value.selectedRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val customStartTime = _uiState.value.customRangeDays?.let { days ->
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                }
                val app = getAppDetail(
                    packageName = packageName,
                    usageTimeRange = range,
                    customStartTimeMillis = customStartTime
                )
                if (app != null) {
                    val riskScore = calculateRiskScore(app)
                    val since = customStartTime ?: range.startTimeMillis()
                    val batteryHistoryDeferred = async { batteryRepository.getBatteryHistory(packageName).first() }
                    val networkHistoryDeferred = async { networkRepository.getNetworkHistory(packageName).first() }
                    val auditEntriesDeferred = async { appOpsDao.getOpsForPackage(packageName).first() }
                    val creepIndexDeferred = async { permissionSnapshotDao.getPermissionCreepIndex(packageName) }
                    val allAppsDeferred = async { getInstalledApps(includeSystem = true) }
                    val sdkFingerprintDeferred = async { ApkFingerprintScanner.scanKnownSdkMarkers(app.sourceDir) }
                    val certificatePinningDeferred = async { ApkFingerprintScanner.inspectCertificatePinning(app.sourceDir) }
                    val inspectDeferred = async { inspectPackage(app) }
                    val logcatDeferred = async { LogcatReader.readRecentLines(packageName = app.packageName, maxLines = 80) }

                    val batteryHistory = batteryHistoryDeferred.await()
                    val networkHistory = networkHistoryDeferred.await()
                    val auditEntries = auditEntriesDeferred.await()
                    val creepIndex = creepIndexDeferred.await()
                    val allApps = allAppsDeferred.await()
                    val sdkFingerprints = sdkFingerprintDeferred.await()
                    val certificatePinning = certificatePinningDeferred.await()
                    val inspection = inspectDeferred.await()
                    val logcatLines = logcatDeferred.await()
                    val sharedUidPeers = if (app.linuxUid >= 0) {
                        allApps.filter { it.packageName != app.packageName && it.linuxUid == app.linuxUid }
                    } else {
                        emptyList()
                    }
                    val threatSimulationItems = buildThreatSimulation(app)
                    val perAppRadarValues = buildPerAppRadarValues(app, riskScore)
                    val dataFlowSummary = buildDataFlowSummary(app)
                    val safeAlternatives = buildSafeAlternatives(app, riskScore)

                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        app = app,
                        riskScore = riskScore,
                        permissionCreepIndex = creepIndex,
                        dataHoardingScore = PrivacyScoreUtils.dataHoardingScore(app.permissions),
                        sharedUidPeers = sharedUidPeers,
                        sdkFingerprints = sdkFingerprints,
                        certificatePinningInspection = certificatePinning,
                        perAppRadarValues = perAppRadarValues,
                        threatSimulationItems = threatSimulationItems,
                        safeAlternatives = safeAlternatives,
                        dataFlowSummary = dataFlowSummary,
                        logcatLines = logcatLines,
                        batteryTrend = batteryHistory
                            .filter { it.timestamp >= since }
                            .take(7)
                            .reversed()
                            .map {
                                val value = ((it.foregroundTimeMs + it.backgroundTimeMs) / 60_000f)
                                formatDay(it.timestamp) to value
                            },
                        networkTrend = networkHistory
                            .filter { it.timestamp >= since }
                            .take(7)
                            .reversed()
                            .map {
                                val value = ((it.wifiRxBytes + it.wifiTxBytes + it.mobileRxBytes + it.mobileTxBytes) / 1_048_576f)
                                formatDay(it.timestamp) to value
                            },
                        permissionAuditEntries = auditEntries
                            .filter { it.lastAccessTime >= since || it.timestamp >= since }
                            .take(25),
                        selectedRange = range,
                        customRangeDays = _uiState.value.customRangeDays,
                        isBeginnerMode = _uiState.value.isBeginnerMode,
                        onDeviceOnly = _uiState.value.onDeviceOnly,
                        trustLabel = _uiState.value.trustLabel,
                        exportedComponents = inspection.exportedComponents,
                        signerInfos = inspection.signerInfos,
                        apkDiffSummary = inspection.apkDiffSummary,
                        permissionDiffSummary = inspection.permissionDiffSummary,
                        permissionDiffTimeline = inspection.permissionDiffTimeline
                    )
                } else {
                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        error = "App not found",
                        selectedRange = range,
                        customRangeDays = _uiState.value.customRangeDays,
                        isBeginnerMode = _uiState.value.isBeginnerMode,
                        onDeviceOnly = _uiState.value.onDeviceOnly
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AppDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load app details",
                    selectedRange = range,
                    customRangeDays = _uiState.value.customRangeDays,
                    isBeginnerMode = _uiState.value.isBeginnerMode,
                    onDeviceOnly = _uiState.value.onDeviceOnly
                )
            }
        }
    }

    private suspend fun inspectPackage(app: AppInfo): PackageInspectionData {
        val pm = context.packageManager
        val pkgInfo = getFullPackageInfo(pm, packageName) ?: return PackageInspectionData()
        val exportedComponents = buildExportedComponents(pkgInfo)
        val signerInfos = buildSignerInfos(pkgInfo)
        val currentApkSize = app.sourceDir?.let { path -> runCatching { File(path).length() }.getOrDefault(0L) } ?: 0L
        val currentSigningDigest = signerInfos.firstOrNull()?.sha256 ?: ""
        val previousSnapshot = apkSnapshotDao.getLatestDifferentVersion(packageName, app.versionCode)
        val apkDiffSummary = previousSnapshot?.let {
            ApkDiffSummary(
                previousVersionCode = it.versionCode,
                previousCapturedAt = it.capturedAt,
                previousSizeBytes = it.apkSizeBytes,
                currentSizeBytes = currentApkSize,
                sizeDeltaBytes = currentApkSize - it.apkSizeBytes,
                signatureChanged = currentSigningDigest.isNotBlank() && it.signingDigestSha256 != currentSigningDigest
            )
        }

        val currentDangerous = app.permissions
            .filter { it.isDangerous && it.isGranted }
            .map { it.permissionName }
            .toSet()
        val previousPermissionSnapshot = permissionSnapshotDao.getLatestDifferentVersion(packageName, app.versionCode)
        val permissionDiffSummary = previousPermissionSnapshot?.let { previous ->
            val previousDangerous = previous.dangerousPermissionsCsv
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            PermissionDiffSummary(
                previousVersionCode = previous.versionCode,
                previousCapturedAt = previous.capturedAt,
                addedPermissions = (currentDangerous - previousDangerous).sorted(),
                removedPermissions = (previousDangerous - currentDangerous).sorted()
            )
        }

        val permissionDiffTimeline = buildPermissionDiffTimeline(
            snapshots = permissionSnapshotDao.getRecentForPackage(packageName, limit = 8)
        )

        return PackageInspectionData(
            exportedComponents = exportedComponents,
            signerInfos = signerInfos,
            apkDiffSummary = apkDiffSummary,
            permissionDiffSummary = permissionDiffSummary,
            permissionDiffTimeline = permissionDiffTimeline
        )
    }

    private fun buildPermissionDiffTimeline(
        snapshots: List<com.apptracker.data.db.entity.PermissionSnapshotEntity>
    ): List<PermissionDiffTimelineEntry> {
        if (snapshots.size < 2) return emptyList()
        val timeline = mutableListOf<PermissionDiffTimelineEntry>()
        snapshots.zipWithNext().forEach { (newer, older) ->
            val newerSet = newer.dangerousPermissionsCsv
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val olderSet = older.dangerousPermissionsCsv
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val added = (newerSet - olderSet).sorted()
            val removed = (olderSet - newerSet).sorted()
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                timeline += PermissionDiffTimelineEntry(
                    fromVersionCode = older.versionCode,
                    toVersionCode = newer.versionCode,
                    capturedAt = newer.capturedAt,
                    addedCount = added.size,
                    removedCount = removed.size,
                    addedPermissions = added,
                    removedPermissions = removed
                )
            }
        }
        return timeline.take(5)
    }

    private fun getFullPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildExportedComponents(pkgInfo: PackageInfo): List<AppComponentInspection> {
        val items = mutableListOf<AppComponentInspection>()
        pkgInfo.activities?.forEach { info ->
            if (info.exported) {
                items += AppComponentInspection(
                    name = info.name,
                    kind = "Activity",
                    exported = true,
                    enabled = info.enabled,
                    permission = info.permission
                )
            }
        }
        pkgInfo.services?.forEach { info ->
            if (info.exported) {
                items += AppComponentInspection(
                    name = info.name,
                    kind = "Service",
                    exported = true,
                    enabled = info.enabled,
                    permission = info.permission
                )
            }
        }
        pkgInfo.receivers?.forEach { info ->
            if (info.exported) {
                items += AppComponentInspection(
                    name = info.name,
                    kind = "Receiver",
                    exported = true,
                    enabled = info.enabled,
                    permission = info.permission
                )
            }
        }
        pkgInfo.providers?.forEach { info ->
            if (info.exported) {
                items += AppComponentInspection(
                    name = info.name,
                    kind = "Provider",
                    exported = true,
                    enabled = info.enabled,
                    permission = info.readPermission ?: info.writePermission
                )
            }
        }
        return items.sortedWith(compareBy<AppComponentInspection> { it.kind }.thenBy { it.name })
    }

    private fun buildSignerInfos(pkgInfo: PackageInfo): List<ApkSignerInspection> {
        val rawSigners: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        return rawSigners.mapIndexedNotNull { index, bytes ->
            parseSigner(bytes, chainPosition = index + 1, chainSize = rawSigners.size)
        }
    }

    private fun parseSigner(bytes: ByteArray, chainPosition: Int, chainSize: Int): ApkSignerInspection? {
        return runCatching {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val cert = certificateFactory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            val now = System.currentTimeMillis()
            val expiryMs = cert.notAfter.time - now
            val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(expiryMs)
            val isExpired = expiryMs < 0

            val keyBits = when (val key = cert.publicKey) {
                is RSAPublicKey -> key.modulus.bitLength()
                is ECPublicKey -> key.params.order.bitLength()
                else -> key.encoded.size * 8
            }
            val weakSigAlgo = cert.sigAlgName.contains("MD5", ignoreCase = true) ||
                cert.sigAlgName.contains("SHA1", ignoreCase = true)
            val keyStrength = when {
                weakSigAlgo -> "Weak"
                keyBits >= 3072 -> "Strong"
                keyBits >= 2048 -> "Moderate"
                else -> "Weak"
            }
            val expiryRisk = when {
                isExpired -> "Expired"
                daysUntilExpiry < 30 -> "Expiring Soon"
                else -> "Healthy"
            }

            ApkSignerInspection(
                sha256 = sha256(bytes),
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                validFrom = formatDateTime(cert.notBefore.time),
                validTo = formatDateTime(cert.notAfter.time),
                signatureAlgorithm = cert.sigAlgName,
                publicKeyAlgorithm = cert.publicKey.algorithm,
                publicKeyBits = keyBits,
                chainPosition = chainPosition,
                chainSize = chainSize,
                keyStrength = keyStrength,
                expiryRisk = expiryRisk,
                daysUntilExpiry = daysUntilExpiry
            )
        }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun loadAllApps() {
        viewModelScope.launch {
            try {
                _allApps.value = getInstalledApps(includeSystem = false)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleWatch() {
        viewModelScope.launch {
            val app = _uiState.value.app ?: return@launch
            if (isWatched.value) {
                watchedAppDao.unwatch(packageName)
            } else {
                watchedAppDao.watch(
                    WatchedAppEntity(
                        packageName = packageName,
                        appName = app.appName
                    )
                )
            }
        }
    }

    fun getReport(): String? {
        if (_uiState.value.onDeviceOnly) return null
        val app = _uiState.value.app ?: return null
        return generateReport(app, _uiState.value.riskScore)
    }

    fun onRangeSelected(range: UsageTimeRange) {
        if (range == _uiState.value.selectedRange) return
        _uiState.value = _uiState.value.copy(selectedRange = range, customRangeDays = null)
        viewModelScope.launch {
            OnboardingPreferences.setDefaultUsageRange(context, range)
        }
        loadAppDetail(range)
    }

    fun onCustomRangeDaysSelected(days: Int) {
        if (days <= 0) return
        _uiState.value = _uiState.value.copy(customRangeDays = days)
        loadAppDetail(_uiState.value.selectedRange)
    }

    fun setTrustLabel(label: String) {
        viewModelScope.launch {
            appTrustLabelDao.upsert(
                AppTrustLabelEntity(
                    packageName = packageName,
                    label = label
                )
            )
        }
    }

    private fun formatDay(timestamp: Long): String {
        return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun buildPerAppRadarValues(app: AppInfo, riskScore: RiskScore): List<Pair<String, Float>> {
        val permission = riskScore.permissionScore.toFloat().coerceIn(0f, 100f)
        val behavior = riskScore.behaviorScore.toFloat().coerceIn(0f, 100f)
        val network = riskScore.networkScore.toFloat().coerceIn(0f, 100f)
        val battery = riskScore.batteryScore.toFloat().coerceIn(0f, 100f)
        val trust = when (_uiState.value.trustLabel) {
            TrustLabel.TRUSTED -> 10f
            TrustLabel.SUSPICIOUS -> 85f
            else -> riskScore.overallScore.toFloat().coerceIn(0f, 100f)
        }
        return listOf(
            "Permission" to permission,
            "Behavior" to behavior,
            "Network" to network,
            "Battery" to battery,
            "Trust" to trust
        )
    }

    private fun buildThreatSimulation(app: AppInfo): List<String> {
        val granted = app.permissions.filter { it.isGranted }.map { it.permissionName.uppercase() }
        val items = mutableListOf<String>()
        if (granted.any { it.contains("READ_CONTACTS") || it.contains("WRITE_CONTACTS") }) {
            items.add("Read and export your contacts")
        }
        if (granted.any { it.contains("READ_SMS") || it.contains("RECEIVE_SMS") || it.contains("SEND_SMS") }) {
            items.add("Read or send SMS messages")
        }
        if (granted.any { it.contains("RECORD_AUDIO") || it.contains("CAMERA") }) {
            items.add("Capture microphone or camera data")
        }
        if (granted.any { it.contains("ACCESS_FINE_LOCATION") || it.contains("ACCESS_COARSE_LOCATION") }) {
            items.add("Track precise location in background")
        }
        if ((app.networkUsage?.totalBytes ?: 0L) > 1_048_576L) {
            items.add("Transmit collected data over the network")
        }
        if (items.isEmpty()) {
            items.add("No obvious high-impact abuse paths from currently observed permissions")
        }
        return items.take(5)
    }

    private fun buildDataFlowSummary(app: AppInfo): List<String> {
        val inputs = mutableListOf<String>()
        val granted = app.permissions.filter { it.isGranted }.map { it.permissionName.uppercase() }
        if (granted.any { it.contains("CONTACT") }) inputs.add("Contacts")
        if (granted.any { it.contains("LOCATION") }) inputs.add("Location")
        if (granted.any { it.contains("SMS") }) inputs.add("SMS")
        if (granted.any { it.contains("CAMERA") || it.contains("RECORD_AUDIO") }) inputs.add("Media")
        if (inputs.isEmpty()) inputs.add("Limited local data")

        val hasNetwork = (app.networkUsage?.totalBytes ?: 0L) > 0L
        val transfer = if (hasNetwork) "Network (internet)" else "No observed transfer"
        return listOf(
            "Sources: ${inputs.joinToString(", ")}",
            "Processing: App internal logic and SDK components",
            "Outbound: $transfer"
        )
    }

    private fun buildSafeAlternatives(app: AppInfo, riskScore: RiskScore): List<String> {
        if (riskScore.overallScore < 70) return emptyList()
        val candidate = when (app.category) {
            AppCategory.COMMUNICATION -> "Signal"
            AppCategory.BROWSER -> "Firefox or Brave"
            AppCategory.SOCIAL -> "Mastodon / privacy-first clients"
            AppCategory.MEDIA -> "VLC"
            AppCategory.TOOLS -> "Simple Mobile Tools alternatives"
            AppCategory.FINANCE -> "Bank official app or web PWA with strict permissions"
            else -> null
        }
        return listOfNotNull(candidate)
    }
}

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val app: AppInfo? = null,
    val riskScore: RiskScore? = null,
    val batteryTrend: List<Pair<String, Float>> = emptyList(),
    val networkTrend: List<Pair<String, Float>> = emptyList(),
    val permissionAuditEntries: List<AppOpsHistoryEntity> = emptyList(),
    val selectedRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val customRangeDays: Int? = null,
    val isBeginnerMode: Boolean = true,
    val onDeviceOnly: Boolean = true,
    val trustLabel: String = TrustLabel.UNKNOWN,
    val permissionCreepIndex: Int = 0,
    val dataHoardingScore: Int = 0,
    val perAppRadarValues: List<Pair<String, Float>> = emptyList(),
    val threatSimulationItems: List<String> = emptyList(),
    val safeAlternatives: List<String> = emptyList(),
    val dataFlowSummary: List<String> = emptyList(),
    val logcatLines: List<String> = emptyList(),
    val sharedUidPeers: List<AppInfo> = emptyList(),
    val sdkFingerprints: List<String> = emptyList(),
    val certificatePinningInspection: CertificatePinningInspection? = null,
    val exportedComponents: List<AppComponentInspection> = emptyList(),
    val signerInfos: List<ApkSignerInspection> = emptyList(),
    val apkDiffSummary: ApkDiffSummary? = null,
    val permissionDiffSummary: PermissionDiffSummary? = null,
    val permissionDiffTimeline: List<PermissionDiffTimelineEntry> = emptyList(),
    val selectedTab: DetailTab = DetailTab.PERMISSIONS
)

enum class DetailTab(val label: String) {
    PERMISSIONS("Permissions"),
    APP_OPS("Activity Log"),
    BATTERY("Battery"),
    NETWORK("Network"),
    RISK("Risk Analysis"),
    INFO("App Info"),
    INSPECTOR("Inspector"),
    LOGCAT("Logcat")
}

data class AppComponentInspection(
    val name: String,
    val kind: String,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?
)

data class ApkSignerInspection(
    val sha256: String,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val signatureAlgorithm: String,
    val publicKeyAlgorithm: String,
    val publicKeyBits: Int,
    val chainPosition: Int,
    val chainSize: Int,
    val keyStrength: String,
    val expiryRisk: String,
    val daysUntilExpiry: Long
)

data class ApkDiffSummary(
    val previousVersionCode: Long,
    val previousCapturedAt: Long,
    val previousSizeBytes: Long,
    val currentSizeBytes: Long,
    val sizeDeltaBytes: Long,
    val signatureChanged: Boolean
)

data class PermissionDiffSummary(
    val previousVersionCode: Long,
    val previousCapturedAt: Long,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>
)

data class PermissionDiffTimelineEntry(
    val fromVersionCode: Long,
    val toVersionCode: Long,
    val capturedAt: Long,
    val addedCount: Int,
    val removedCount: Int,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>
)

private data class PackageInspectionData(
    val exportedComponents: List<AppComponentInspection> = emptyList(),
    val signerInfos: List<ApkSignerInspection> = emptyList(),
    val apkDiffSummary: ApkDiffSummary? = null,
    val permissionDiffSummary: PermissionDiffSummary? = null,
    val permissionDiffTimeline: List<PermissionDiffTimelineEntry> = emptyList()
)
