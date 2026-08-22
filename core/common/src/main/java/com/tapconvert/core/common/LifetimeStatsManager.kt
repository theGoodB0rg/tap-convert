package com.tapconvert.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.lifetimeStatsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tapconvert_lifetime_stats")

interface LifetimeStatsManager {
    val lifetimeReclaimedBytes: Flow<Long>
    val lifetimeConversionsCount: Flow<Int>

    suspend fun recordConversion(originalSizeBytes: Long, outputSizeBytes: Long)
}

class DataStoreLifetimeStatsManager(
    private val dataStore: DataStore<Preferences>
) : LifetimeStatsManager {

    companion object {
        val KEY_LIFETIME_RECLAIMED_BYTES = longPreferencesKey("lifetime_reclaimed_bytes")
        val KEY_LIFETIME_CONVERSIONS_COUNT = intPreferencesKey("lifetime_conversions_count")

        fun create(context: Context): DataStoreLifetimeStatsManager {
            return DataStoreLifetimeStatsManager(context.lifetimeStatsDataStore)
        }
    }

    override val lifetimeReclaimedBytes: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_LIFETIME_RECLAIMED_BYTES] ?: 0L
    }

    override val lifetimeConversionsCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_LIFETIME_CONVERSIONS_COUNT] ?: 0
    }

    override suspend fun recordConversion(originalSizeBytes: Long, outputSizeBytes: Long) {
        val reclaimed = (originalSizeBytes - outputSizeBytes).coerceAtLeast(0L)
        dataStore.edit { preferences ->
            val currentBytes = preferences[KEY_LIFETIME_RECLAIMED_BYTES] ?: 0L
            val currentCount = preferences[KEY_LIFETIME_CONVERSIONS_COUNT] ?: 0
            preferences[KEY_LIFETIME_RECLAIMED_BYTES] = currentBytes + reclaimed
            preferences[KEY_LIFETIME_CONVERSIONS_COUNT] = currentCount + 1
        }
    }
}

class InMemoryLifetimeStatsManager(
    initialReclaimedBytes: Long = 0L,
    initialCount: Int = 0
) : LifetimeStatsManager {

    private val _lifetimeReclaimedBytes = MutableStateFlow(initialReclaimedBytes)
    override val lifetimeReclaimedBytes: Flow<Long> = _lifetimeReclaimedBytes.asStateFlow()

    private val _lifetimeConversionsCount = MutableStateFlow(initialCount)
    override val lifetimeConversionsCount: Flow<Int> = _lifetimeConversionsCount.asStateFlow()

    override suspend fun recordConversion(originalSizeBytes: Long, outputSizeBytes: Long) {
        val reclaimed = (originalSizeBytes - outputSizeBytes).coerceAtLeast(0L)
        _lifetimeReclaimedBytes.value += reclaimed
        _lifetimeConversionsCount.value += 1
    }
}
