package com.apptracker.data.model

data class NetworkUsageInfo(
    val packageName: String,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val wifiRxPackets: Long,
    val wifiTxPackets: Long,
    val mobileRxPackets: Long,
    val mobileTxPackets: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val startTime: Long,
    val endTime: Long
) {
    val totalWifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val totalMobileBytes: Long get() = mobileRxBytes + mobileTxBytes
    val totalBytes: Long get() = totalWifiBytes + totalMobileBytes
    val totalRxBytes: Long get() = wifiRxBytes + mobileRxBytes
    val totalTxBytes: Long get() = wifiTxBytes + mobileTxBytes

    val sendReceiveRatio: Double
        get() = if (totalRxBytes > 0) totalTxBytes.toDouble() / totalRxBytes else 0.0

    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedWifi: String get() = formatBytes(totalWifiBytes)
    val formattedMobile: String get() = formatBytes(totalMobileBytes)

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}
