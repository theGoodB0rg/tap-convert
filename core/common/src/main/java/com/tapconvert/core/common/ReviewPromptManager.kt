package com.tapconvert.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore(name = "tapconvert_review_prefs")

interface ReviewPromptManager {
    val successfulConversionsCount: Flow<Int>
    val hasReviewed: Flow<Boolean>
    val lastPromptTimestampMs: Flow<Long>

    suspend fun shouldPromptReview(currentTimeMs: Long = System.currentTimeMillis()): Boolean
    suspend fun recordSuccessfulConversion()
    suspend fun recordReviewDismissed(currentTimeMs: Long = System.currentTimeMillis())
    suspend fun recordReviewCompleted()
    suspend fun resetReviewState()
}

class DataStoreReviewPromptManager(
    private val dataStore: DataStore<Preferences>,
    private val minConversionsBeforePrompt: Int = 2,
    private val cooldownDays: Long = 45L
) : ReviewPromptManager {

    companion object {
        val KEY_SUCCESSFUL_CONVERSIONS = intPreferencesKey("successful_conversions_count")
        val KEY_HAS_REVIEWED = booleanPreferencesKey("has_reviewed")
        val KEY_LAST_PROMPT_TIMESTAMP = longPreferencesKey("last_prompt_timestamp_ms")

        fun create(context: Context): DataStoreReviewPromptManager {
            return DataStoreReviewPromptManager(context.reviewDataStore)
        }
    }

    override val successfulConversionsCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_SUCCESSFUL_CONVERSIONS] ?: 0
    }

    override val hasReviewed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_HAS_REVIEWED] ?: false
    }

    override val lastPromptTimestampMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_PROMPT_TIMESTAMP] ?: 0L
    }

    override suspend fun shouldPromptReview(currentTimeMs: Long): Boolean {
        val prefs = dataStore.data.first()
        val reviewed = prefs[KEY_HAS_REVIEWED] ?: false
        if (reviewed) return false

        val count = prefs[KEY_SUCCESSFUL_CONVERSIONS] ?: 0
        if (count < minConversionsBeforePrompt) return false

        val lastPrompt = prefs[KEY_LAST_PROMPT_TIMESTAMP] ?: 0L
        if (lastPrompt > 0L) {
            val cooldownMs = TimeUnit.DAYS.toMillis(cooldownDays)
            val elapsed = currentTimeMs - lastPrompt
            if (elapsed < cooldownMs) return false
        }

        return true
    }

    override suspend fun recordSuccessfulConversion() {
        dataStore.edit { preferences ->
            val current = preferences[KEY_SUCCESSFUL_CONVERSIONS] ?: 0
            preferences[KEY_SUCCESSFUL_CONVERSIONS] = current + 1
        }
    }

    override suspend fun recordReviewDismissed(currentTimeMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_PROMPT_TIMESTAMP] = currentTimeMs
        }
    }

    override suspend fun recordReviewCompleted() {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_REVIEWED] = true
        }
    }

    override suspend fun resetReviewState() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SUCCESSFUL_CONVERSIONS)
            preferences.remove(KEY_HAS_REVIEWED)
            preferences.remove(KEY_LAST_PROMPT_TIMESTAMP)
        }
    }
}

class InMemoryReviewPromptManager(
    private val minConversionsBeforePrompt: Int = 2,
    private val cooldownDays: Long = 45L
) : ReviewPromptManager {

    private val _count = MutableStateFlow(0)
    override val successfulConversionsCount: Flow<Int> = _count.asStateFlow()

    private val _hasReviewed = MutableStateFlow(false)
    override val hasReviewed: Flow<Boolean> = _hasReviewed.asStateFlow()

    private val _lastPromptTimestamp = MutableStateFlow(0L)
    override val lastPromptTimestampMs: Flow<Long> = _lastPromptTimestamp.asStateFlow()

    override suspend fun shouldPromptReview(currentTimeMs: Long): Boolean {
        if (_hasReviewed.value) return false
        if (_count.value < minConversionsBeforePrompt) return false

        if (_lastPromptTimestamp.value > 0L) {
            val cooldownMs = TimeUnit.DAYS.toMillis(cooldownDays)
            val elapsed = currentTimeMs - _lastPromptTimestamp.value
            if (elapsed < cooldownMs) return false
        }

        return true
    }

    override suspend fun recordSuccessfulConversion() {
        _count.value += 1
    }

    override suspend fun recordReviewDismissed(currentTimeMs: Long) {
        _lastPromptTimestamp.value = currentTimeMs
    }

    override suspend fun recordReviewCompleted() {
        _hasReviewed.value = true
    }

    override suspend fun resetReviewState() {
        _count.value = 0
        _hasReviewed.value = false
        _lastPromptTimestamp.value = 0L
    }
}
