package com.apptracker.ui.screens.filemanager

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.regex.Pattern
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FileCategory(val label: String) {
    ALL("All"),
    FOLDER("Folder"),
    DOCUMENT("Document"),
    MEDIA("Media"),
    ARCHIVE("Archive"),
    APK("APK"),
    CODE("Code"),
    HIDDEN("Hidden"),
    OTHER("Other")
}

enum class FileSort(val label: String) {
    NAME_ASC("Name A-Z"),
    SIZE_DESC("Largest"),
    DATE_DESC("Recent")
}

data class FileItemUi(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory,
    val isSensitive: Boolean = false,
    val duplicateGroupId: String? = null
)

data class FilePreviewUi(
    val name: String,
    val path: String,
    val category: FileCategory,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentPreview: String?
)

data class FileTelemetrySummary(
    val totalFileEvents24h: Int = 0,
    val directoryOpenEvents24h: Int = 0,
    val previewEvents24h: Int = 0,
    val sensitivePreviewEvents24h: Int = 0,
    val deleteEvents24h: Int = 0,
    val mostAccessedDirectories: List<Pair<String, Int>> = emptyList(),
    val burstDetected: Boolean = false
)

data class FileManagerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPath: String = "",
    val canGoUp: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val includeHidden: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: FileCategory = FileCategory.ALL,
    val sortBy: FileSort = FileSort.NAME_ASC,
    val showSensitiveOnly: Boolean = false,
    val showDuplicatesOnly: Boolean = false,
    val isScanningSensitive: Boolean = false,
    val isScanningDuplicates: Boolean = false,
    val sensitiveCount: Int = 0,
    val duplicateClusterCount: Int = 0,
    val allItems: List<FileItemUi> = emptyList(),
    val filteredItems: List<FileItemUi> = emptyList(),
    val storageSummary: String = "",
    val pendingDelete: FileItemUi? = null,
    val previewItem: FilePreviewUi? = null,
    val deleteSecurely: Boolean = false,
    val recentFileEvents: List<SecurityEventEntity> = emptyList(),
    val telemetrySummary: FileTelemetrySummary = FileTelemetrySummary()
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityEventDao: SecurityEventDao
) : ViewModel() {

    private val lastActionLoggedAt = mutableMapOf<String, Long>()

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        val startPath = defaultRootPath()
        _uiState.value = _uiState.value.copy(currentPath = startPath)
        refreshPermissionState()
        refreshRecentFileEvents()
        loadDirectory(startPath)
    }

    fun refreshRecentFileEvents() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
            val events = securityEventDao.getRecentEvents(since).first()
                .filter {
                    it.type == SecurityEventType.FILE_ACCESS_AUDIT ||
                        it.type == SecurityEventType.SENSITIVE_FILE_DETECTED ||
                        it.type == SecurityEventType.DUPLICATE_FILES_DETECTED ||
                        it.type == SecurityEventType.SECURE_DELETE
                }
            val recentEvents = events.take(8)

            val events24h = events.filter { it.timestamp >= now - TimeUnit.DAYS.toMillis(1) }
            val directoryOpenCount = events24h.count { it.title.startsWith("Directory opened") }
            val previewCount = events24h.count {
                it.title.startsWith("File previewed") ||
                    it.title.startsWith("Sensitive file previewed") ||
                    it.title.startsWith("APK inspector opened")
            }
            val sensitivePreviewCount = events24h.count { it.title.startsWith("Sensitive file previewed") }
            val deleteCount = events24h.count {
                it.title.startsWith("File deleted") || it.type == SecurityEventType.SECURE_DELETE
            }
            val directoryFrequency = events24h
                .filter { it.title.startsWith("Directory opened") || it.title.startsWith("File previewed") || it.title.startsWith("Sensitive file previewed") }
                .mapNotNull { extractDirectoryFromDetail(it.detail) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key to it.value }

            val burstDetected = (previewCount + directoryOpenCount) >= 25 || sensitivePreviewCount >= 5
            if (burstDetected) {
                val seenBurst = events24h.any { it.title == "High file access burst" }
                if (!seenBurst) {
                    securityEventDao.insert(
                        SecurityEventEntity(
                            type = SecurityEventType.FILE_ACCESS_AUDIT,
                            packageName = "",
                            title = "High file access burst",
                            detail = "${previewCount + directoryOpenCount} navigation/preview actions and $sensitivePreviewCount sensitive previews in the last 24h"
                        )
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                recentFileEvents = recentEvents,
                telemetrySummary = FileTelemetrySummary(
                    totalFileEvents24h = events24h.size,
                    directoryOpenEvents24h = directoryOpenCount,
                    previewEvents24h = previewCount,
                    sensitivePreviewEvents24h = sensitivePreviewCount,
                    deleteEvents24h = deleteCount,
                    mostAccessedDirectories = directoryFrequency,
                    burstDetected = burstDetected
                )
            )
        }
    }

    fun refreshPermissionState() {
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(hasAllFilesAccess = hasAccess)
    }

    fun loadDirectory(path: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val directory = File(path)
                if (!directory.exists() || !directory.isDirectory) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Directory not found",
                        allItems = emptyList(),
                        filteredItems = emptyList()
                    )
                    return@launch
                }

                val items = (directory.listFiles()?.toList() ?: emptyList())
                    .map { file ->
                        FileItemUi(
                            path = file.absolutePath,
                            name = file.name.ifBlank { file.absolutePath },
                            isDirectory = file.isDirectory,
                            isHidden = file.isHidden || file.name.startsWith("."),
                            sizeBytes = if (file.isDirectory) directorySizeQuick(file) else file.length(),
                            lastModified = file.lastModified(),
                            category = classify(file)
                        )
                    }

                val state = _uiState.value.copy(
                    isLoading = false,
                    currentPath = directory.absolutePath,
                    canGoUp = directory.parentFile != null,
                    allItems = items,
                    storageSummary = calculateStorageSummary(items),
                    error = null
                )
                _uiState.value = state.copy(filteredItems = applyFilters(state))

                logFileAccessAudit(
                    title = "Directory opened",
                    detail = directory.absolutePath,
                    throttleKey = "dir:${directory.absolutePath}",
                    minIntervalMs = TimeUnit.MINUTES.toMillis(5)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load files"
                )
            }
        }
    }

    fun openItem(item: FileItemUi) {
        if (item.isDirectory) {
            loadDirectory(item.path)
        } else {
            openPreview(item)
        }
    }

    fun goUp() {
        val parent = File(_uiState.value.currentPath).parentFile ?: return
        loadDirectory(parent.absolutePath)
    }

    fun onSearchQueryChange(query: String) {
        val state = _uiState.value.copy(searchQuery = query)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun onIncludeHiddenChange(include: Boolean) {
        val state = _uiState.value.copy(includeHidden = include)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun onCategorySelected(category: FileCategory) {
        val state = _uiState.value.copy(selectedCategory = category)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun onSortSelected(sort: FileSort) {
        val state = _uiState.value.copy(sortBy = sort)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun promptDelete(item: FileItemUi) {
        _uiState.value = _uiState.value.copy(pendingDelete = item, deleteSecurely = false)
    }

    fun clearDeletePrompt() {
        _uiState.value = _uiState.value.copy(pendingDelete = null, deleteSecurely = false)
    }

    fun setDeleteSecurely(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(deleteSecurely = enabled)
    }

    fun setSensitiveOnly(enabled: Boolean) {
        val state = _uiState.value.copy(showSensitiveOnly = enabled)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun setDuplicatesOnly(enabled: Boolean) {
        val state = _uiState.value.copy(showDuplicatesOnly = enabled)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun clearScanFilters() {
        val state = _uiState.value.copy(showSensitiveOnly = false, showDuplicatesOnly = false)
        _uiState.value = state.copy(filteredItems = applyFilters(state))
    }

    fun runSensitiveScan() {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.value = currentState.copy(isScanningSensitive = true, error = null)
            val scanned = currentState.allItems.map { item ->
                if (item.isDirectory) {
                    item
                } else {
                    val sensitiveByName = looksSensitive(item.name)
                    val sensitiveByContent = if (sensitiveByName) false else containsSensitiveContent(File(item.path), item.category)
                    item.copy(isSensitive = sensitiveByName || sensitiveByContent)
                }
            }
            val sensitiveCount = scanned.count { it.isSensitive }
            if (sensitiveCount > 0) {
                securityEventDao.insert(
                    SecurityEventEntity(
                        type = SecurityEventType.SENSITIVE_FILE_DETECTED,
                        packageName = "",
                        title = "Sensitive files detected",
                        detail = "$sensitiveCount potential sensitive files in ${_uiState.value.currentPath}"
                    )
                )
            }
            refreshRecentFileEvents()
            val state = _uiState.value.copy(
                isScanningSensitive = false,
                allItems = scanned,
                sensitiveCount = sensitiveCount,
                showSensitiveOnly = sensitiveCount > 0 || _uiState.value.showSensitiveOnly
            )
            _uiState.value = state.copy(filteredItems = applyFilters(state))
        }
    }

    fun runDuplicateScan() {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.value = currentState.copy(isScanningDuplicates = true, error = null)

            val candidates = currentState.allItems.filter { !it.isDirectory && it.sizeBytes > 0 }
            val bySize = candidates.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
            val duplicateMap = mutableMapOf<String, String>()
            var clusterId = 0

            bySize.values.forEach { sameSizeItems ->
                val byHash = sameSizeItems.groupBy { quickHash(it.path, it.sizeBytes) }
                byHash.values.filter { it.size > 1 }.forEach { duplicates ->
                    clusterId += 1
                    val groupTag = "dup-$clusterId"
                    duplicates.forEach { duplicateMap[it.path] = groupTag }
                }
            }

            val duplicateClusterCount = duplicateMap.values.distinct().size
            if (duplicateClusterCount > 0) {
                val duplicateFileCount = duplicateMap.size
                securityEventDao.insert(
                    SecurityEventEntity(
                        type = SecurityEventType.DUPLICATE_FILES_DETECTED,
                        packageName = "",
                        title = "Duplicate files detected",
                        detail = "$duplicateFileCount files across $duplicateClusterCount duplicate clusters"
                    )
                )
            }
            refreshRecentFileEvents()

            val scanned = currentState.allItems.map { item ->
                item.copy(duplicateGroupId = duplicateMap[item.path])
            }
            val state = _uiState.value.copy(
                isScanningDuplicates = false,
                allItems = scanned,
                duplicateClusterCount = duplicateClusterCount,
                showDuplicatesOnly = duplicateClusterCount > 0 || _uiState.value.showDuplicatesOnly
            )
            _uiState.value = state.copy(filteredItems = applyFilters(state))
        }
    }

    fun deletePendingItem() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            runCatching {
                val secure = _uiState.value.deleteSecurely
                if (secure && !target.isDirectory) {
                    secureDeleteFile(File(target.path))
                } else {
                    File(target.path).deleteRecursively()
                }
            }.onSuccess { success ->
                if (success && !_uiState.value.deleteSecurely) {
                    securityEventDao.insert(
                        SecurityEventEntity(
                            type = SecurityEventType.FILE_ACCESS_AUDIT,
                            packageName = "",
                            title = "File deleted",
                            detail = target.path
                        )
                    )
                }
                if (success && _uiState.value.deleteSecurely) {
                    securityEventDao.insert(
                        SecurityEventEntity(
                            type = SecurityEventType.SECURE_DELETE,
                            packageName = "",
                            title = "Secure delete completed",
                            detail = target.path
                        )
                    )
                }
                refreshRecentFileEvents()
                _uiState.value = _uiState.value.copy(
                    pendingDelete = null,
                    deleteSecurely = false,
                    error = if (success) null else "Delete failed"
                )
                loadDirectory(_uiState.value.currentPath)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    pendingDelete = null,
                    deleteSecurely = false,
                    error = e.message ?: "Delete failed"
                )
            }
        }
    }

    fun closePreview() {
        _uiState.value = _uiState.value.copy(previewItem = null)
    }

    private fun openPreview(item: FileItemUi) {
        val file = File(item.path)
        val sensitive = item.isSensitive || looksSensitive(item.name)
        val previewText = if (item.category == FileCategory.APK) {
            inspectApk(file)
        } else if (isTextPreviewable(file, item.category)) {
            runCatching {
                file.bufferedReader().useLines { lines -> lines.take(200).joinToString("\n") }
            }.getOrNull()
        } else null

        when {
            item.category == FileCategory.APK -> logFileAccessAudit(
                title = "APK inspector opened",
                detail = item.path,
                throttleKey = "preview-apk:${item.path}",
                minIntervalMs = TimeUnit.MINUTES.toMillis(1)
            )

            sensitive -> logFileAccessAudit(
                title = "Sensitive file previewed",
                detail = item.path,
                throttleKey = "preview-sensitive:${item.path}",
                minIntervalMs = TimeUnit.MINUTES.toMillis(1)
            )

            else -> logFileAccessAudit(
                title = "File previewed",
                detail = item.path,
                throttleKey = "preview:${item.path}",
                minIntervalMs = TimeUnit.MINUTES.toMillis(1)
            )
        }

        _uiState.value = _uiState.value.copy(
            previewItem = FilePreviewUi(
                name = item.name,
                path = item.path,
                category = item.category,
                sizeBytes = item.sizeBytes,
                lastModified = item.lastModified,
                contentPreview = previewText
            )
        )
    }

    private fun applyFilters(state: FileManagerUiState): List<FileItemUi> {
        var result = state.allItems

        if (!state.includeHidden) {
            result = result.filter { !it.isHidden }
        }

        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(query) || it.path.lowercase().contains(query)
            }
        }

        if (state.selectedCategory != FileCategory.ALL) {
            result = result.filter { it.category == state.selectedCategory }
        }

        if (state.showSensitiveOnly) {
            result = result.filter { it.isSensitive }
        }

        if (state.showDuplicatesOnly) {
            result = result.filter { !it.duplicateGroupId.isNullOrBlank() }
        }

        result = when (state.sortBy) {
            FileSort.NAME_ASC -> result.sortedWith(compareBy<FileItemUi> { !it.isDirectory }.thenBy { it.name.lowercase() })
            FileSort.SIZE_DESC -> result.sortedWith(compareByDescending<FileItemUi> { it.isDirectory }.thenByDescending { it.sizeBytes })
            FileSort.DATE_DESC -> result.sortedWith(compareByDescending<FileItemUi> { it.isDirectory }.thenByDescending { it.lastModified })
        }

        return result
    }

    private fun defaultRootPath(): String {
        val extRoot = Environment.getExternalStorageDirectory()
        if (extRoot != null && extRoot.exists()) return extRoot.absolutePath
        return context.filesDir.absolutePath
    }

    private fun classify(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        if (file.name.startsWith(".") || file.isHidden) return FileCategory.HIDDEN

        val ext = file.extension.lowercase()
        return when {
            ext in setOf("txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "md") -> FileCategory.DOCUMENT
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "mp4", "mkv", "mp3", "wav", "flac") -> FileCategory.MEDIA
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVE
            ext == "apk" -> FileCategory.APK
            ext in setOf("kt", "java", "xml", "json", "js", "ts", "py", "cpp", "c", "h", "gradle", "kts", "yml", "yaml", "properties") -> FileCategory.CODE
            else -> FileCategory.OTHER
        }
    }

    private fun isTextPreviewable(file: File, category: FileCategory): Boolean {
        if (file.length() > 512 * 1024) return false
        if (category == FileCategory.CODE || category == FileCategory.DOCUMENT) return true
        return file.extension.lowercase() in setOf("txt", "log", "json", "xml", "csv", "md")
    }

    private fun directorySizeQuick(directory: File): Long {
        val children = directory.listFiles() ?: return 0L
        return children.take(120).sumOf {
            if (it.isFile) it.length() else 0L
        }
    }

    private fun calculateStorageSummary(items: List<FileItemUi>): String {
        val total = items.sumOf { it.sizeBytes }
        val hiddenCount = items.count { it.isHidden }
        return "${items.size} items • ${hiddenCount} hidden • ${humanSize(total)} visible size"
    }

    private fun looksSensitive(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val sensitiveTokens = listOf(
            "password", "passwd", "credential", "secret", "private_key", "keystore", "wallet",
            "seed", "mnemonic", "recovery", "backup", "otp", "2fa", "token", "auth",
            "bank", "statement", "invoice", "salary", "tax", "ssn", "passport", "aadhaar",
            "license", "id_card", "medical", "health", "insurance", "contract", "confidential"
        )
        return sensitiveTokens.any { lower.contains(it) }
    }

    private fun containsSensitiveContent(file: File, category: FileCategory): Boolean {
        if (!file.exists() || !file.isFile) return false
        if (!isTextPreviewable(file, category)) return false

        val content = runCatching {
            file.bufferedReader().useLines { lines -> lines.take(400).joinToString("\n") }
        }.getOrNull() ?: return false

        val patterns = listOf(
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"][^'\"]{4,}['\"]"),
            Pattern.compile("(?i)(api[_-]?key|token|secret|private[_-]?key)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-\\.=]{8,}['\"]"),
            Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+[A-Za-z0-9\\-_.=]+)"),
            Pattern.compile("(?i)(aws_secret_access_key|github_pat_|xox[baprs]-[A-Za-z0-9-]{10,})")
        )
        return patterns.any { it.matcher(content).find() }
    }

    private fun inspectApk(file: File): String {
        val pm = context.packageManager
        val flags =
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS

        val pkgInfo = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        }.getOrNull() ?: return "Unable to parse APK manifest metadata."

        val packageName = pkgInfo.packageName ?: "Unknown"
        val versionName = pkgInfo.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
        val dangerousPermissions = permissions.filter { permissionName ->
            runCatching {
                val permissionInfo = pm.getPermissionInfo(permissionName, 0)
                permissionInfo.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_DANGEROUS != 0
            }.getOrDefault(false)
        }

        return buildString {
            appendLine("APK Inspector")
            appendLine("Package: $packageName")
            appendLine("Version: $versionName ($versionCode)")
            appendLine("Permissions: ${permissions.size} total, ${dangerousPermissions.size} dangerous")
            appendLine("Activities: ${pkgInfo.activities?.size ?: 0}")
            appendLine("Services: ${pkgInfo.services?.size ?: 0}")
            appendLine("Receivers: ${pkgInfo.receivers?.size ?: 0}")
            appendLine("Providers: ${pkgInfo.providers?.size ?: 0}")
            if (dangerousPermissions.isNotEmpty()) {
                appendLine()
                appendLine("Dangerous permissions:")
                dangerousPermissions.take(25).forEach { appendLine("- $it") }
                if (dangerousPermissions.size > 25) {
                    appendLine("- ... +${dangerousPermissions.size - 25} more")
                }
            }
        }
    }

    private fun quickHash(path: String, sizeBytes: Long): String {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) return@runCatching ""
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(sizeBytes.toString().toByteArray())
            val buffer = ByteArray(128 * 1024)
            FileInputStream(file).use { input ->
                val read = input.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun secureDeleteFile(file: File): Boolean {
        if (!file.exists()) return true
        if (!file.isFile) return file.deleteRecursively()
        return runCatching {
            val length = file.length()
            if (length > 0L) {
                RandomAccessFile(file, "rw").use { raf ->
                    val chunk = ByteArray(16 * 1024)
                    var written = 0L
                    while (written < length) {
                        val remaining = (length - written).toInt().coerceAtMost(chunk.size)
                        SecureRandom().nextBytes(chunk)
                        raf.write(chunk, 0, remaining)
                        written += remaining
                    }
                    raf.fd.sync()
                }
            }
            file.delete()
        }.getOrDefault(false)
    }

    fun humanSize(size: Long): String {
        if (size <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = size.toDouble()
        var idx = 0
        while (value >= 1024 && idx < units.lastIndex) {
            value /= 1024
            idx++
        }
        return String.format("%.1f %s", value, units[idx])
    }

    fun lastSegment(path: String): String = Uri.parse(path).lastPathSegment ?: path

    private fun extractDirectoryFromDetail(detail: String): String? {
        if (detail.isBlank()) return null
        val file = File(detail)
        return when {
            file.isDirectory -> file.absolutePath
            else -> file.parentFile?.absolutePath
        }
    }

    private fun logFileAccessAudit(
        title: String,
        detail: String,
        throttleKey: String,
        minIntervalMs: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lastLogged = lastActionLoggedAt[throttleKey] ?: 0L
            if (now - lastLogged < minIntervalMs) return@launch
            lastActionLoggedAt[throttleKey] = now

            securityEventDao.insert(
                SecurityEventEntity(
                    type = SecurityEventType.FILE_ACCESS_AUDIT,
                    packageName = "",
                    title = title,
                    detail = detail
                )
            )
            refreshRecentFileEvents()
        }
    }
}
