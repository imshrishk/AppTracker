package com.apptracker.data.model

data class StorageUsageInfo(
    val packageName: String,
    val appSizeBytes: Long,
    val dataSizeBytes: Long,
    val cacheSizeBytes: Long,
    val totalSizeBytes: Long
) {
    val formattedAppSize: String get() = NetworkUsageInfo.formatBytes(appSizeBytes)
    val formattedDataSize: String get() = NetworkUsageInfo.formatBytes(dataSizeBytes)
    val formattedCacheSize: String get() = NetworkUsageInfo.formatBytes(cacheSizeBytes)
    val formattedTotalSize: String get() = NetworkUsageInfo.formatBytes(totalSizeBytes)
}
