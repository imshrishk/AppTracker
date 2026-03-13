package com.apptracker.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.apptracker.data.model.UsageTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apptracker_prefs")

object OnboardingPreferences {
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val BEGINNER_MODE = booleanPreferencesKey("beginner_mode")
    private val ON_DEVICE_ONLY = booleanPreferencesKey("on_device_only")
    private val DEFAULT_USAGE_RANGE_DAYS = intPreferencesKey("default_usage_range_days")

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
}
