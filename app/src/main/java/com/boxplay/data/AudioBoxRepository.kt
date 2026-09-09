package com.boxplay.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// FIX: without a corruptionHandler, a single corrupted write to this file
// (killed mid-write, disk full, storage corruption) makes DataStore throw
// every time the app reads it afterwards, permanently crashing BoxPlay on
// launch until the user clears app data. Falling back to empty preferences
// loses only that one corrupted snapshot instead of bricking the app.
private val Context.audioBoxDataStore by preferencesDataStore(
    name = "boxplay_audio_boxes",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

sealed class DeleteSceneResult {
    data class Deleted(
        val boxIds: List<Int>,
        val internalFilePaths: List<String>,
    ) : DeleteSceneResult()

    object LastScene : DeleteSceneResult()
    object Locked : DeleteSceneResult()
    object NotFound : DeleteSceneResult()
}

sealed class DeleteBoxResult {
    data class Deleted(val internalFilePath: String?) : DeleteBoxResult()
    object SceneLocked : DeleteBoxResult()
    object NotFound : DeleteBoxResult()
}

class AudioBoxRepository(private val context: Context) {
    val sceneState: Flow<AudioSceneState> = context.audioBoxDataStore.data.map { preferences ->
        preferences.toSceneState()
    }

    val configs: Flow<List<AudioBoxConfig>> = sceneState.map { it.selectedScene.boxes }

    suspend fun saveConfig(config: AudioBoxConfig) {
        saveConfig(AudioSceneConfig.DEFAULT_SCENE_ID, config)
    }

    suspend fun saveConfig(sceneId: Int, config: AudioBoxConfig) {
        context.audioBoxDataStore.edit { preferences ->
            preferences.ensureSceneExists(sceneId)
            preferences.ensureBoxExists(sceneId, config.id)
            preferences.saveBox(sceneId, config)
            preferences.touchScene(sceneId)
        }
    }

    suspend fun selectScene(sceneId: Int) {
        context.audioBoxDataStore.edit { preferences ->
            val ids = preferences.sceneIdsOrDefault()
            if (sceneId in ids) {
                preferences[selectedSceneIdKey] = sceneId
            }
        }
    }

    suspend fun createScene(name: String) {
        val normalizedName = name.trim().take(MAX_SCENE_NAME_LENGTH).ifBlank { "Cena" }

        context.audioBoxDataStore.edit { preferences ->
            val currentIds = preferences.sceneIdsOrDefault()
            if (currentIds.size >= AudioSceneConfig.MAX_SCENE_COUNT) return@edit

            val nextId = ((currentIds.maxOrNull() ?: 0) + 1).coerceAtLeast(AudioSceneConfig.DEFAULT_SCENE_ID + 1)
            val newIds = (currentIds + nextId).distinct().take(AudioSceneConfig.MAX_SCENE_COUNT)
            val scene = AudioSceneConfig(
                id = nextId,
                name = normalizedName,
                isLocked = false,
                boxes = emptyList(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            )

            preferences[sceneIdsKey] = newIds.joinToString(",")
            preferences[selectedSceneIdKey] = nextId
            preferences.saveSceneMeta(scene)
        }
    }

    suspend fun renameScene(sceneId: Int, name: String) {
        val normalizedName = name.trim().take(MAX_SCENE_NAME_LENGTH).ifBlank { "Cena $sceneId" }

        context.audioBoxDataStore.edit { preferences ->
            if (sceneId !in preferences.sceneIdsOrDefault()) return@edit
            preferences[sceneNameKey(sceneId)] = normalizedName
            preferences.touchScene(sceneId)
        }
    }

    suspend fun deleteScene(sceneId: Int): DeleteSceneResult {
        var result: DeleteSceneResult = DeleteSceneResult.NotFound

        context.audioBoxDataStore.edit { preferences ->
            val currentIds = preferences.sceneIdsOrDefault()
            result = when {
                sceneId !in currentIds -> DeleteSceneResult.NotFound
                currentIds.size <= 1 -> DeleteSceneResult.LastScene
                else -> {
                    val scene = preferences.readScene(sceneId)
                    if (scene.isLocked) {
                        DeleteSceneResult.Locked
                    } else {
                        val boxIds = scene.boxes.map { box -> box.id }
                        val deletedPaths = boxIds.mapNotNull { boxId -> preferences.boxInternalPath(sceneId, boxId) }

                        boxIds.forEach { boxId -> preferences.removeBox(sceneId, boxId) }
                        preferences.removeScene(sceneId)

                        val newIds = currentIds.filterNot { it == sceneId }
                        preferences[sceneIdsKey] = newIds.joinToString(",")

                        val selectedId = preferences[selectedSceneIdKey]
                        if (selectedId == sceneId || selectedId !in newIds) {
                            preferences[selectedSceneIdKey] = newIds.first()
                        }

                        DeleteSceneResult.Deleted(
                            boxIds = boxIds,
                            internalFilePaths = deletedPaths,
                        )
                    }
                }
            }
        }

        return result
    }

    suspend fun addBox(sceneId: Int): Int? {
        var addedBoxId: Int? = null

        context.audioBoxDataStore.edit { preferences ->
            preferences.ensureSceneExists(sceneId)
            val scene = preferences.readScene(sceneId)
            if (scene.isLocked || scene.boxCount >= AudioBoxConfig.MAX_BOX_COUNT) return@edit

            val currentIds = scene.boxes.map { it.id }.toSet()
            val nextBoxId = (AudioBoxConfig.FIRST_BOX_ID..AudioBoxConfig.MAX_BOX_COUNT)
                .firstOrNull { id -> id !in currentIds }
                ?: return@edit
            val newIds = (currentIds + nextBoxId).sorted()

            preferences[sceneBoxIdsKey(sceneId)] = newIds.joinToString(",")
            preferences[sceneLegacyBoxCountKey(sceneId)] = newIds.size
            preferences.saveBox(sceneId, AudioBoxConfig.emptySlot(nextBoxId))
            preferences.touchScene(sceneId)
            addedBoxId = nextBoxId
        }

        return addedBoxId
    }

    suspend fun deleteBox(sceneId: Int, boxId: Int): DeleteBoxResult {
        var result: DeleteBoxResult = DeleteBoxResult.NotFound

        context.audioBoxDataStore.edit { preferences ->
            val currentIds = preferences.sceneIdsOrDefault()
            if (sceneId !in currentIds) {
                result = DeleteBoxResult.NotFound
                return@edit
            }

            val scene = preferences.readScene(sceneId)
            result = when {
                scene.isLocked -> DeleteBoxResult.SceneLocked
                scene.boxes.none { it.id == boxId } -> DeleteBoxResult.NotFound
                else -> {
                    val deletedPath = preferences.boxInternalPath(sceneId, boxId)
                    val newIds = scene.boxes.map { it.id }.filterNot { it == boxId }

                    preferences[sceneBoxIdsKey(sceneId)] = newIds.joinToString(",")
                    preferences[sceneLegacyBoxCountKey(sceneId)] = newIds.size
                    preferences.removeBox(sceneId, boxId)
                    preferences.touchScene(sceneId)

                    DeleteBoxResult.Deleted(deletedPath)
                }
            }
        }

        return result
    }

    suspend fun setSceneLocked(sceneId: Int, isLocked: Boolean) {
        context.audioBoxDataStore.edit { preferences ->
            preferences.ensureSceneExists(sceneId)
            preferences[sceneIsLockedKey(sceneId)] = isLocked
            preferences.touchScene(sceneId)
        }
    }

    private fun Preferences.toSceneState(): AudioSceneState {
        val ids = sceneIdsOrDefault()
            .distinct()
            .take(AudioSceneConfig.MAX_SCENE_COUNT)
        val scenes = ids.map { sceneId -> readScene(sceneId) }
            .ifEmpty { listOf(AudioSceneConfig.default()) }
        val selectedId = this[selectedSceneIdKey]
            ?.takeIf { requestedId -> scenes.any { it.id == requestedId } }
            ?: AudioSceneConfig.DEFAULT_SCENE_ID

        return AudioSceneState(
            scenes = scenes,
            selectedSceneId = selectedId.takeIf { id -> scenes.any { it.id == id } } ?: scenes.first().id,
        )
    }

    private fun Preferences.readScene(sceneId: Int): AudioSceneConfig {
        val sceneName = this[sceneNameKey(sceneId)]
            ?.takeIf { it.isNotBlank() }
            ?: if (sceneId == AudioSceneConfig.DEFAULT_SCENE_ID) AudioSceneConfig.DEFAULT_SCENE_NAME else "Cena $sceneId"
        val boxIds = activeBoxIds(sceneId)
        val boxes = boxIds.map { id -> readBox(sceneId, AudioBoxConfig.emptySlot(id)) }

        return AudioSceneConfig(
            id = sceneId,
            name = sceneName,
            isLocked = this[sceneIsLockedKey(sceneId)] ?: false,
            boxes = boxes,
            updatedAtEpochMillis = this[sceneUpdatedAtKey(sceneId)],
        )
    }

    private fun Preferences.activeBoxIds(sceneId: Int): List<Int> {
        val explicitIds = this[sceneBoxIdsKey(sceneId)]?.toIdList(AudioBoxConfig.MAX_BOX_COUNT)
        if (explicitIds != null) return explicitIds

        val legacyCount = this[sceneLegacyBoxCountKey(sceneId)]
            ?.coerceIn(0, AudioBoxConfig.MAX_BOX_COUNT)
            ?: AudioBoxConfig.MAX_BOX_COUNT

        return (AudioBoxConfig.FIRST_BOX_ID..legacyCount)
            .filter { id -> hasMeaningfulBox(sceneId, id) }
            .distinct()
            .take(AudioBoxConfig.MAX_BOX_COUNT)
    }

    private fun Preferences.readBox(sceneId: Int, defaultConfig: AudioBoxConfig): AudioBoxConfig {
        val id = defaultConfig.id
        val isDefaultScene = sceneId == AudioSceneConfig.DEFAULT_SCENE_ID
        val legacyDisplayName = if (isDefaultScene) this[legacyDisplayNameKey(id)] else null
        val legacyOriginalFileName = if (isDefaultScene) this[legacyOriginalFileNameKey(id)] else null
        val legacyInternalFilePath = if (isDefaultScene) this[legacyInternalFilePathKey(id)] else null
        val legacyVolume = if (isDefaultScene) this[legacyVolumeKey(id)] else null
        val legacyIsLocked = if (isDefaultScene) this[legacyIsLockedKey(id)] else null
        val legacyUpdatedAt = if (isDefaultScene) this[legacyUpdatedAtKey(id)] else null

        return defaultConfig.copy(
            displayName = this[boxDisplayNameKey(sceneId, id)]
                ?: legacyDisplayName
                ?: defaultConfig.displayName,
            customLabel = this[boxCustomLabelKey(sceneId, id)]?.takeIf { it.isNotBlank() },
            originalFileName = this[boxOriginalFileNameKey(sceneId, id)] ?: legacyOriginalFileName,
            internalFilePath = this[boxInternalFilePathKey(sceneId, id)] ?: legacyInternalFilePath,
            volume = (this[boxVolumeKey(sceneId, id)] ?: legacyVolume ?: defaultConfig.volume).coerceIn(0f, 1f),
            isLocked = this[boxIsLockedKey(sceneId, id)] ?: legacyIsLocked ?: defaultConfig.isLocked,
            updatedAtEpochMillis = this[boxUpdatedAtKey(sceneId, id)] ?: legacyUpdatedAt,
        )
    }

    private fun Preferences.hasMeaningfulBox(sceneId: Int, boxId: Int): Boolean {
        val defaultConfig = AudioBoxConfig.emptySlot(boxId)
        val isDefaultScene = sceneId == AudioSceneConfig.DEFAULT_SCENE_ID
        val displayName = this[boxDisplayNameKey(sceneId, boxId)]
            ?: if (isDefaultScene) this[legacyDisplayNameKey(boxId)] else null
        val originalName = this[boxOriginalFileNameKey(sceneId, boxId)]
            ?: if (isDefaultScene) this[legacyOriginalFileNameKey(boxId)] else null
        val internalPath = this[boxInternalFilePathKey(sceneId, boxId)]
            ?: if (isDefaultScene) this[legacyInternalFilePathKey(boxId)] else null
        val customLabel = this[boxCustomLabelKey(sceneId, boxId)]
        val volume = this[boxVolumeKey(sceneId, boxId)]
            ?: if (isDefaultScene) this[legacyVolumeKey(boxId)] else null
        val isLocked = this[boxIsLockedKey(sceneId, boxId)]
            ?: if (isDefaultScene) this[legacyIsLockedKey(boxId)] else null

        return originalName != null ||
            internalPath != null ||
            !customLabel.isNullOrBlank() ||
            (displayName != null && displayName != defaultConfig.displayName) ||
            (volume != null && volume != defaultConfig.volume) ||
            isLocked == true
    }

    private fun Preferences.boxInternalPath(sceneId: Int, boxId: Int): String? {
        val legacyPath = if (sceneId == AudioSceneConfig.DEFAULT_SCENE_ID) this[legacyInternalFilePathKey(boxId)] else null
        return this[boxInternalFilePathKey(sceneId, boxId)] ?: legacyPath
    }

    private fun MutablePreferences.ensureSceneExists(sceneId: Int) {
        val ids = sceneIdsOrDefault().toMutableList()
        if (sceneId !in ids && ids.size < AudioSceneConfig.MAX_SCENE_COUNT) {
            ids.add(sceneId)
            this[sceneIdsKey] = ids.distinct().joinToString(",")
        }
        if (sceneId == AudioSceneConfig.DEFAULT_SCENE_ID && this[sceneNameKey(sceneId)] == null) {
            saveSceneMeta(AudioSceneConfig.default())
        }
    }

    private fun MutablePreferences.ensureBoxExists(sceneId: Int, boxId: Int) {
        val ids = activeBoxIds(sceneId).toMutableSet()
        if (boxId !in ids && ids.size < AudioBoxConfig.MAX_BOX_COUNT) {
            ids.add(boxId)
            this[sceneBoxIdsKey(sceneId)] = ids.sorted().joinToString(",")
            this[sceneLegacyBoxCountKey(sceneId)] = ids.size
        }
    }

    private fun MutablePreferences.saveSceneMeta(scene: AudioSceneConfig) {
        this[sceneNameKey(scene.id)] = scene.name
        this[sceneIsLockedKey(scene.id)] = scene.isLocked
        this[sceneBoxIdsKey(scene.id)] = scene.boxes.map { it.id }.joinToString(",")
        this[sceneLegacyBoxCountKey(scene.id)] = scene.boxCount
        this[sceneUpdatedAtKey(scene.id)] = scene.updatedAtEpochMillis ?: System.currentTimeMillis()
    }

    private fun MutablePreferences.touchScene(sceneId: Int) {
        this[sceneUpdatedAtKey(sceneId)] = System.currentTimeMillis()
    }

    private fun MutablePreferences.saveBox(sceneId: Int, config: AudioBoxConfig) {
        val id = config.id
        this[boxDisplayNameKey(sceneId, id)] = config.displayName
        this[boxVolumeKey(sceneId, id)] = config.volume.coerceIn(0f, 1f)
        this[boxIsLockedKey(sceneId, id)] = config.isLocked
        this[boxUpdatedAtKey(sceneId, id)] = config.updatedAtEpochMillis ?: System.currentTimeMillis()

        val customLabel = config.customLabel?.trim()?.take(MAX_BOX_LABEL_LENGTH)?.takeIf { it.isNotBlank() }
        if (customLabel == null) {
            remove(boxCustomLabelKey(sceneId, id))
        } else {
            this[boxCustomLabelKey(sceneId, id)] = customLabel
        }

        val originalFileName = config.originalFileName
        if (originalFileName == null) {
            remove(boxOriginalFileNameKey(sceneId, id))
        } else {
            this[boxOriginalFileNameKey(sceneId, id)] = originalFileName
        }

        val internalFilePath = config.internalFilePath
        if (internalFilePath == null) {
            remove(boxInternalFilePathKey(sceneId, id))
        } else {
            this[boxInternalFilePathKey(sceneId, id)] = internalFilePath
        }
    }

    private fun MutablePreferences.removeBox(sceneId: Int, boxId: Int) {
        remove(boxDisplayNameKey(sceneId, boxId))
        remove(boxCustomLabelKey(sceneId, boxId))
        remove(boxOriginalFileNameKey(sceneId, boxId))
        remove(boxInternalFilePathKey(sceneId, boxId))
        remove(boxVolumeKey(sceneId, boxId))
        remove(boxIsLockedKey(sceneId, boxId))
        remove(boxUpdatedAtKey(sceneId, boxId))

        if (sceneId == AudioSceneConfig.DEFAULT_SCENE_ID) {
            remove(legacyDisplayNameKey(boxId))
            remove(legacyOriginalFileNameKey(boxId))
            remove(legacyInternalFilePathKey(boxId))
            remove(legacyVolumeKey(boxId))
            remove(legacyIsLockedKey(boxId))
            remove(legacyUpdatedAtKey(boxId))
        }
    }

    private fun MutablePreferences.removeScene(sceneId: Int) {
        remove(sceneNameKey(sceneId))
        remove(sceneIsLockedKey(sceneId))
        remove(sceneBoxIdsKey(sceneId))
        remove(sceneLegacyBoxCountKey(sceneId))
        remove(sceneUpdatedAtKey(sceneId))
    }

    private fun Preferences.sceneIdsOrDefault(): List<Int> = this[sceneIdsKey]
        ?.toIdList(AudioSceneConfig.MAX_SCENE_COUNT)
        ?.ifEmpty { null }
        ?: listOf(AudioSceneConfig.DEFAULT_SCENE_ID)

    private fun String.toIdList(maxItems: Int): List<Int> = split(',')
        .mapNotNull { raw -> raw.trim().toIntOrNull() }
        .filter { id -> id > 0 }
        .distinct()
        .take(maxItems)

    private fun sceneNameKey(sceneId: Int) = stringPreferencesKey("scene_${sceneId}_name")
    private fun sceneIsLockedKey(sceneId: Int) = booleanPreferencesKey("scene_${sceneId}_is_locked")
    private fun sceneBoxIdsKey(sceneId: Int) = stringPreferencesKey("scene_${sceneId}_box_ids")
    private fun sceneLegacyBoxCountKey(sceneId: Int) = intPreferencesKey("scene_${sceneId}_box_count")
    private fun sceneUpdatedAtKey(sceneId: Int) = longPreferencesKey("scene_${sceneId}_updated_at")

    private fun boxDisplayNameKey(sceneId: Int, boxId: Int) = stringPreferencesKey("scene_${sceneId}_box_${boxId}_display_name")
    private fun boxCustomLabelKey(sceneId: Int, boxId: Int) = stringPreferencesKey("scene_${sceneId}_box_${boxId}_custom_label")
    private fun boxOriginalFileNameKey(sceneId: Int, boxId: Int) = stringPreferencesKey("scene_${sceneId}_box_${boxId}_original_file_name")
    private fun boxInternalFilePathKey(sceneId: Int, boxId: Int) = stringPreferencesKey("scene_${sceneId}_box_${boxId}_internal_file_path")
    private fun boxVolumeKey(sceneId: Int, boxId: Int) = floatPreferencesKey("scene_${sceneId}_box_${boxId}_volume")
    private fun boxIsLockedKey(sceneId: Int, boxId: Int) = booleanPreferencesKey("scene_${sceneId}_box_${boxId}_is_locked")
    private fun boxUpdatedAtKey(sceneId: Int, boxId: Int) = longPreferencesKey("scene_${sceneId}_box_${boxId}_updated_at")

    private fun legacyDisplayNameKey(id: Int) = stringPreferencesKey("box_${id}_display_name")
    private fun legacyOriginalFileNameKey(id: Int) = stringPreferencesKey("box_${id}_original_file_name")
    private fun legacyInternalFilePathKey(id: Int) = stringPreferencesKey("box_${id}_internal_file_path")
    private fun legacyVolumeKey(id: Int) = floatPreferencesKey("box_${id}_volume")
    private fun legacyIsLockedKey(id: Int) = booleanPreferencesKey("box_${id}_is_locked")
    private fun legacyUpdatedAtKey(id: Int) = longPreferencesKey("box_${id}_updated_at")

    companion object {
        private const val MAX_SCENE_NAME_LENGTH = 32
        private const val MAX_BOX_LABEL_LENGTH = 36
        private val sceneIdsKey = stringPreferencesKey("scene_ids")
        private val selectedSceneIdKey = intPreferencesKey("selected_scene_id")
    }
}