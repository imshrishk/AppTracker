package com.apptracker.util

object LogcatReader {

    fun readRecentLines(packageName: String, maxLines: Int = 80): List<String> {
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-t", maxLines.coerceAtLeast(20).toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { reader ->
                val all = reader.readLines()
                if (packageName.isBlank()) {
                    all.takeLast(maxLines)
                } else {
                    val filtered = all.filter { line ->
                        line.contains(packageName, ignoreCase = true)
                    }
                    if (filtered.isNotEmpty()) filtered.takeLast(maxLines) else all.takeLast(maxLines)
                }
            }
        }.getOrDefault(listOf("Logcat unavailable on this device/profile."))
    }
}
