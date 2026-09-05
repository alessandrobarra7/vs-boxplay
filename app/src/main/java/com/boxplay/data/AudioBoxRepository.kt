package com.boxplay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.audioBoxDataStore by preferencesDataStore(name = "boxplay_audio_boxes")

class AudioBoxRepository(private val context: Context) {
    val configs: Flow<List<AudioBoxConfig>> = context.audioBoxDataStore.data.map { preferences ->
        AudioBoxConfig.emptySlots().map { defaultConfig ->
            val id = defaultConfig.id
            defaultConfig.copy(
                displayName = preferences[displayNameKey(id)] ?: defaultConfig.displayName,
                originalFileName = preferences[originalFileNameKey(id)],
                internalFilePath = preferences[internalFilePathKey(id)],
                volume = (preferences[volumeKey(id)] ?: defaultConfig.volume).coerceIn(0f, 1f),
                isLocked = preferences[isLockedKey(id)] ?: defaultConfig.isLocked,
                updatedAtEpochMillis = preferences[updatedAtKey(id)],
            )
        }
    }

    suspend fun saveConfig(config: AudioBoxConfig) {
        context.audioBoxDataStore.edit { preferences ->
            val id = config.id
            preferences[displayNameKey(id)] = config.displayName
            preferences[volumeKey(id)] = config.volume.coerceIn(0f, 1f)
            preferences[isLockedKey(id)] = config.isLocked
            preferences[updatedAtKey(id)] = config.updatedAtEpochMillis ?: System.currentTimeMillis()

            val originalFileName = config.originalFileName
            val internalFilePath = config.internalFilePath

            if (originalFileName == null) {
                preferences.remove(originalFileNameKey(id))
            } else {
                preferences[originalFileNameKey(id)] = originalFileName
            }

            if (internalFilePath == null) {
                preferences.remove(internalFilePathKey(id))
            } else {
                preferences[internalFilePathKey(id)] = internalFilePath
            }
        }
    }

    private fun displayNameKey(id: Int) = stringPreferencesKey("box_${id}_display_name")
    private fun originalFileNameKey(id: Int) = stringPreferencesKey("box_${id}_original_file_name")
    private fun internalFilePathKey(id: Int) = stringPreferencesKey("box_${id}_internal_file_path")
    private fun volumeKey(id: Int) = floatPreferencesKey("box_${id}_volume")
    private fun isLockedKey(id: Int) = booleanPreferencesKey("box_${id}_is_locked")
    private fun updatedAtKey(id: Int) = longPreferencesKey("box_${id}_updated_at")
}
