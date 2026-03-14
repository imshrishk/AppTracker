package com.apptracker.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.apptracker.data.model.UsageTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apptracker_prefs")

object OnboardingPreferences {
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val BEGINNER_MODE = booleanPreferencesKey("beginner_mode")
    private val ON_DEVICE_ONLY = booleanPreferencesKey("on_device_only")
    private val DEFAULT_USAGE_RANGE_DAYS = intPreferencesKey("default_usage_range_days")
    private val HIGH_RISK_THRESHOLD = intPreferencesKey("high_risk_threshold")
    private val BACKGROUND_HEAVY_HOURS = intPreferencesKey("background_heavy_hours")
    private val DNS_LEAK_SENSITIVITY = intPreferencesKey("dns_leak_sensitivity")
    private val LAST_HEALTH_SCORE = intPreferencesKey("last_health_score")
    private val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
    private val LAST_WEEKLY_DIGEST_TIMESTAMP = longPreferencesKey("last_weekly_digest_timestamp")
    private val BASELINE_CAPTURED = booleanPreferencesKey("baseline_captured")
    private val BASELINE_CAPTURED_AT = longPreferencesKey("baseline_captured_at")
    private val BASELINE_HEALTH_SCORE = intPreferencesKey("baseline_health_score")
    private val BASELINE_APP_COUNT = intPreferencesKey("baseline_app_count")
    private val BASELINE_HIGH_RISK_COUNT = intPreferencesKey("baseline_high_risk_count")
    private val BASELINE_DANGEROUS_PERMISSION_COUNT = intPreferencesKey("baseline_dangerous_permission_count")
    private val APP_LIST_SEARCH_QUERY = stringPreferencesKey("app_list_search_query")
    private val GLOBAL_SEARCH_QUERY = stringPreferencesKey("global_search_query")
    private val SEARCH_QUERY_PERSISTENCE = booleanPreferencesKey("search_query_persistence")

    fun isOnboardingDone(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    fun beginnerMode(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[BEGINNER_MODE] ?: true }

    fun onDeviceOnly(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ON_DEVICE_ONLY] ?: true }

    fun defaultUsageRange(context: Context): Flow<UsageTimeRange> =
        context.dataStore.data.map {
            UsageTimeRange.fromDays(it[DEFAULT_USAGE_RANGE_DAYS] ?: UsageTimeRange.LAST_24_HOURS.days)
        }

    fun highRiskThreshold(context: Context): Flow<Int> =
        context.dataStore.data.map { it[HIGH_RISK_THRESHOLD] ?: 45 }

    fun backgroundHeavyHours(context: Context): Flow<Int> =
        context.dataStore.data.map { it[BACKGROUND_HEAVY_HOURS] ?: 1 }

    fun dnsLeakSensitivity(context: Context): Flow<Int> =
        context.dataStore.data.map { it[DNS_LEAK_SENSITIVITY] ?: 2 }

    fun appListSearchQuery(context: Context): Flow<String> =
        context.dataStore.data.map { it[APP_LIST_SEARCH_QUERY] ?: "" }

    fun globalSearchQuery(context: Context): Flow<String> =
        context.dataStore.data.map { it[GLOBAL_SEARCH_QUERY] ?: "" }

    fun searchQueryPersistence(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SEARCH_QUERY_PERSISTENCE] ?: true }

    suspend fun markOnboardingDone(context: Context) {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setBeginnerMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[BEGINNER_MODE] = enabled }
    }

    suspend fun setOnDeviceOnly(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[ON_DEVICE_ONLY] = enabled }
    }

    suspend fun setDefaultUsageRange(context: Context, range: UsageTimeRange) {
        context.dataStore.edit { it[DEFAULT_USAGE_RANGE_DAYS] = range.days }
    }

    suspend fun setHighRiskThreshold(context: Context, threshold: Int) {
        context.dataStore.edit { it[HIGH_RISK_THRESHOLD] = threshold.coerceIn(30, 80) }
    }

    suspend fun setBackgroundHeavyHours(context: Context, hours: Int) {
        context.dataStore.edit { it[BACKGROUND_HEAVY_HOURS] = hours.coerceIn(1, 6) }
    }

    suspend fun setDnsLeakSensitivity(context: Context, level: Int) {
        context.dataStore.edit { it[DNS_LEAK_SENSITIVITY] = level.coerceIn(1, 3) }
    }

    suspend fun setAppListSearchQuery(context: Context, query: String) {
        context.dataStore.edit { it[APP_LIST_SEARCH_QUERY] = query }
    }

    suspend fun setGlobalSearchQuery(context: Context, query: String) {
        context.dataStore.edit { it[GLOBAL_SEARCH_QUERY] = query }
    }

    suspend fun setSearchQueryPersistence(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[SEARCH_QUERY_PERSISTENCE] = enabled }
    }

    suspend fun getLastHealthScore(context: Context): Int =
        context.dataStore.data.map { it[LAST_HEALTH_SCORE] ?: -1 }.first()

    suspend fun getDnsLeakSensitivity(context: Context): Int =
        context.dataStore.data.map { it[DNS_LEAK_SENSITIVITY] ?: 2 }.first()

    fun lastHealthScore(context: Context): Flow<Int> =
        context.dataStore.data.map { it[LAST_HEALTH_SCORE] ?: -1 }

    fun lastScanTimestamp(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_SCAN_TIMESTAMP] ?: 0L }

    fun lastWeeklyDigestTimestamp(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_WEEKLY_DIGEST_TIMESTAMP] ?: 0L }

    fun baselineCaptured(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[BASELINE_CAPTURED] ?: false }

    fun baselineSummary(context: Context): Flow<BaselineSummary?> =
        context.dataStore.data.map {
            if (it[BASELINE_CAPTURED] != true) {
                null
            } else {
                BaselineSummary(
                    capturedAt = it[BASELINE_CAPTURED_AT] ?: 0L,
                    healthScore = it[BASELINE_HEALTH_SCORE] ?: 0,
                    appCount = it[BASELINE_APP_COUNT] ?: 0,
                    highRiskCount = it[BASELINE_HIGH_RISK_COUNT] ?: 0,
                    dangerousPermissionCount = it[BASELINE_DANGEROUS_PERMISSION_COUNT] ?: 0
                )
            }
        }

    suspend fun getLastScanTimestamp(context: Context): Long =
        context.dataStore.data.map { it[LAST_SCAN_TIMESTAMP] ?: 0L }.first()

    suspend fun setLastHealthScore(context: Context, score: Int) {
        context.dataStore.edit { it[LAST_HEALTH_SCORE] = score }
    }

    suspend fun setLastScanTimestamp(context: Context, timestamp: Long) {
        context.dataStore.edit { it[LAST_SCAN_TIMESTAMP] = timestamp }
    }

    suspend fun getLastWeeklyDigestTimestamp(context: Context): Long =
        context.dataStore.data.map { it[LAST_WEEKLY_DIGEST_TIMESTAMP] ?: 0L }.first()

    suspend fun setLastWeeklyDigestTimestamp(context: Context, timestamp: Long) {
        context.dataStore.edit { it[LAST_WEEKLY_DIGEST_TIMESTAMP] = timestamp }
    }

    suspend fun captureBaselineIfMissing(
        context: Context,
        healthScore: Int,
        appCount: Int,
        highRiskCount: Int,
        dangerousPermissionCount: Int,
        capturedAt: Long = System.currentTimeMillis()
    ) {
        context.dataStore.edit {
            if (it[BASELINE_CAPTURED] == true) return@edit
            it[BASELINE_CAPTURED] = true
            it[BASELINE_CAPTURED_AT] = capturedAt
            it[BASELINE_HEALTH_SCORE] = healthScore
            it[BASELINE_APP_COUNT] = appCount
            it[BASELINE_HIGH_RISK_COUNT] = highRiskCount
            it[BASELINE_DANGEROUS_PERMISSION_COUNT] = dangerousPermissionCount
        }
    }
}

data class BaselineSummary(
    val capturedAt: Long,
    val healthScore: Int,
    val appCount: Int,
    val highRiskCount: Int,
    val dangerousPermissionCount: Int
)
