package com.apptracker.util

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

enum class CertificatePinningStatus {
    LIKELY_PRESENT,
    CONFIG_PRESENT,
    NO_EVIDENCE,
    UNAVAILABLE
}

data class CertificatePinningInspection(
    val status: CertificatePinningStatus,
    val summary: String,
    val evidence: List<String> = emptyList()
)

object ApkFingerprintScanner {

    private val sdkMarkers: Map<String, String> = mapOf(
        "com.google.firebase.analytics" to "Firebase Analytics",
        "com.google.android.gms.ads" to "Google Mobile Ads",
        "com.facebook.appevents" to "Facebook SDK (App Events)",
        "com.facebook.ads" to "Facebook Ads SDK",
        "com.appsflyer" to "AppsFlyer",
        "com.adjust.sdk" to "Adjust",
        "com.mixpanel.android" to "Mixpanel",
        "com.amplitude" to "Amplitude",
        "io.branch.referral" to "Branch",
        "com.flurry.android" to "Flurry",
        "com.unity3d.ads" to "Unity Ads",
        "com.ironsource" to "ironSource",
        "com.startapp" to "StartApp"
    )

    fun scanKnownSdkMarkers(apkPath: String?): List<String> {
        if (apkPath.isNullOrBlank()) return emptyList()
        val file = File(apkPath)
        if (!file.exists() || !file.isFile) return emptyList()

        return runCatching {
            val maxBytes = 8 * 1024 * 1024 // keep scan bounded
            val bytes = file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
            val raw = bytes.toString(Charsets.ISO_8859_1)
            sdkMarkers.entries
                .filter { (needle, _) -> raw.contains(needle, ignoreCase = true) }
                .map { it.value }
                .distinct()
                .sorted()
        }.getOrElse { emptyList() }
    }

    fun inspectCertificatePinning(apkPath: String?): CertificatePinningInspection {
        if (apkPath.isNullOrBlank()) {
            return CertificatePinningInspection(
                status = CertificatePinningStatus.UNAVAILABLE,
                summary = "APK path unavailable for certificate pinning scan."
            )
        }

        val file = File(apkPath)
        if (!file.exists() || !file.isFile) {
            return CertificatePinningInspection(
                status = CertificatePinningStatus.UNAVAILABLE,
                summary = "APK file not available for certificate pinning scan."
            )
        }

        return runCatching {
            val evidence = linkedSetOf<String>()
            val entryNames = mutableListOf<String>()
            val markerHits = linkedSetOf<String>()
            val markerMap = mapOf(
                "certificatepinner" to "OkHttp CertificatePinner marker",
                "okhttp3/certificatepinner" to "OkHttp CertificatePinner class",
                "network_security_config" to "Android network security config reference",
                "pin-set" to "Pin-set marker",
                "<pin" to "Pin XML marker",
                "sha256/" to "SHA-256 pin marker",
                "sha1/" to "SHA-1 pin marker",
                "trustkit" to "TrustKit pinning marker",
                "sslpinning" to "SSL pinning marker"
            )

            ZipFile(file).use { zipFile ->
                val entries = zipFile.entries().asSequence().toList()
                entries.forEach { entry ->
                    entryNames += entry.name.lowercase(Locale.ROOT)
                }

                if (entryNames.any { it.contains("network_security_config") }) {
                    evidence += "Network security config file present in APK"
                }

                val maxEntriesToScan = 20
                val maxEntryBytes = 512 * 1024
                val candidateEntries = entries.filter { entry ->
                    !entry.isDirectory &&
                        entry.size in 1..maxEntryBytes.toLong() &&
                        (entry.name.endsWith(".xml", ignoreCase = true) ||
                            entry.name.endsWith(".dex", ignoreCase = true) ||
                            entry.name.endsWith(".txt", ignoreCase = true) ||
                            entry.name.endsWith(".json", ignoreCase = true) ||
                            entry.name.endsWith(".properties", ignoreCase = true) ||
                            entry.name.startsWith("assets/", ignoreCase = true) ||
                            entry.name.startsWith("res/", ignoreCase = true))
                }.take(maxEntriesToScan)

                candidateEntries.forEach { entry ->
                    val raw = zipFile.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        bytes.toString(Charsets.ISO_8859_1).lowercase(Locale.ROOT)
                    }
                    markerMap.forEach { (needle, label) ->
                        if (raw.contains(needle)) {
                            markerHits += label
                        }
                    }
                }
            }

            evidence += markerHits

            when {
                markerHits.any {
                    it.contains("Pin-set", ignoreCase = true) ||
                        it.contains("SHA-256", ignoreCase = true) ||
                        it.contains("CertificatePinner", ignoreCase = true) ||
                        it.contains("TrustKit", ignoreCase = true) ||
                        it.contains("SSL pinning", ignoreCase = true)
                } -> CertificatePinningInspection(
                    status = CertificatePinningStatus.LIKELY_PRESENT,
                    summary = "Quick APK scan found certificate pinning evidence.",
                    evidence = evidence.toList().take(5)
                )

                evidence.any { it.contains("network security config", ignoreCase = true) } -> CertificatePinningInspection(
                    status = CertificatePinningStatus.CONFIG_PRESENT,
                    summary = "Network security configuration found, but no explicit pin-set evidence was detected in the quick scan.",
                    evidence = evidence.toList().take(5)
                )

                else -> CertificatePinningInspection(
                    status = CertificatePinningStatus.NO_EVIDENCE,
                    summary = "Quick APK scan found no certificate pinning evidence.",
                    evidence = emptyList()
                )
            }
        }.getOrElse {
            CertificatePinningInspection(
                status = CertificatePinningStatus.UNAVAILABLE,
                summary = "Certificate pinning scan failed for this APK."
            )
        }
    }
}
