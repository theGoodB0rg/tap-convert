package com.tapconvert.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tapconvert_settings")

interface AppSettingsManager {
    val customStorageUri: Flow<String?>
    val customStorageDisplayPath: Flow<String?>
    val autoSaveToGallery: Flow<Boolean>
    val hapticFeedbackEnabled: Flow<Boolean>

    suspend fun setCustomStorageLocation(uri: String, displayPath: String)
    suspend fun resetToDefaultStorage()
    suspend fun setAutoSaveToGallery(enabled: Boolean)
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)
}

class DataStoreAppSettingsManager(
    private val dataStore: DataStore<Preferences>
) : AppSettingsManager {

    companion object {
        val KEY_CUSTOM_STORAGE_URI = stringPreferencesKey("custom_storage_uri")
        val KEY_CUSTOM_STORAGE_DISPLAY_PATH = stringPreferencesKey("custom_storage_display_path")
        val KEY_AUTO_SAVE_TO_GALLERY = booleanPreferencesKey("auto_save_to_gallery")
        val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")

        fun create(context: Context): DataStoreAppSettingsManager {
            return DataStoreAppSettingsManager(context.dataStore)
        }
    }

    override val customStorageUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_STORAGE_URI]
    }

    override val customStorageDisplayPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_STORAGE_DISPLAY_PATH]
    }

    override val autoSaveToGallery: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SAVE_TO_GALLERY] ?: true
    }

    override val hapticFeedbackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    override suspend fun setCustomStorageLocation(uri: String, displayPath: String) {
        dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_STORAGE_URI] = uri
            preferences[KEY_CUSTOM_STORAGE_DISPLAY_PATH] = displayPath
        }
    }

    override suspend fun resetToDefaultStorage() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_CUSTOM_STORAGE_URI)
            preferences.remove(KEY_CUSTOM_STORAGE_DISPLAY_PATH)
        }
    }

    override suspend fun setAutoSaveToGallery(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_SAVE_TO_GALLERY] = enabled
        }
    }

    override suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }
}

class InMemoryAppSettingsManager(
    initialCustomUri: String? = null,
    initialCustomDisplayPath: String? = null,
    initialAutoSaveToGallery: Boolean = true,
    initialHapticEnabled: Boolean = true
) : AppSettingsManager {

    private val _customStorageUri = MutableStateFlow(initialCustomUri)
    override val customStorageUri: Flow<String?> = _customStorageUri.asStateFlow()

    private val _customStorageDisplayPath = MutableStateFlow(initialCustomDisplayPath)
    override val customStorageDisplayPath: Flow<String?> = _customStorageDisplayPath.asStateFlow()

    private val _autoSaveToGallery = MutableStateFlow(initialAutoSaveToGallery)
    override val autoSaveToGallery: Flow<Boolean> = _autoSaveToGallery.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(initialHapticEnabled)
    override val hapticFeedbackEnabled: Flow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    override suspend fun setCustomStorageLocation(uri: String, displayPath: String) {
        _customStorageUri.value = uri
        _customStorageDisplayPath.value = displayPath
    }

    override suspend fun resetToDefaultStorage() {
        _customStorageUri.value = null
        _customStorageDisplayPath.value = null
    }

    override suspend fun setAutoSaveToGallery(enabled: Boolean) {
        _autoSaveToGallery.value = enabled
    }

    override suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
    }
}
