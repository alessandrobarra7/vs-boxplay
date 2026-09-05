package com.boxplay.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boxplay.audio.AudioPlayerManager
import com.boxplay.audio.AudioPlayerUpdate
import com.boxplay.audio.PlayerRuntimeState
import com.boxplay.data.AudioBoxConfig
import com.boxplay.data.AudioBoxRepository
import com.boxplay.data.LocalAudioStorage
import com.boxplay.ui.model.AudioBoxUiState
import com.boxplay.ui.model.AudioPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoxPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioBoxRepository(application.applicationContext)
    private val storage = LocalAudioStorage(application.applicationContext)
    private val configs = MutableStateFlow(AudioBoxConfig.emptySlots())
    private val runtimeStates = MutableStateFlow<Map<Int, AudioBoxRuntimeState>>(emptyMap())

    private val playerManager = AudioPlayerManager(application.applicationContext) { update ->
        handlePlayerUpdate(update)
    }

    val boxes: StateFlow<List<AudioBoxUiState>> = combine(configs, runtimeStates) { persistedConfigs, runtime ->
        persistedConfigs.map { config ->
            val runtimeState = runtime[config.id] ?: AudioBoxRuntimeState()
            config.toUiState(runtimeState)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AudioBoxConfig.emptySlots().map { it.toUiState(AudioBoxRuntimeState()) },
    )

    init {
        repository.configs
            .onEach { loadedConfigs -> configs.value = loadedConfigs }
            .launchIn(viewModelScope)
    }

    fun onAudioSelected(boxId: Int, uri: Uri) {
        val config = configFor(boxId)
        if (config.isLocked) return

        val fileName = storage.readDisplayName(uri)
        updateRuntime(boxId) {
            it.copy(
                selectedUri = uri,
                selectedFileName = fileName,
                playbackState = AudioPlaybackState.Unsaved,
                statusMessage = "Selecionado - salvar",
            )
        }
    }

    fun onSaveClicked(boxId: Int) {
        val config = configFor(boxId)
        val runtimeState = runtimeFor(boxId)
        val selectedUri = runtimeState.selectedUri ?: return

        if (config.isLocked) return

        viewModelScope.launch {
            updateRuntime(boxId) {
                it.copy(
                    playbackState = AudioPlaybackState.Saving,
                    statusMessage = "Salvando...",
                )
            }

            val previousPath = config.internalFilePath
            val volume = (runtimeFor(boxId).volumeOverride ?: config.volume).coerceIn(0f, 1f)

            try {
                val storedAudio = storage.copyFromUri(boxId, selectedUri)
                repository.saveConfig(
                    config.copy(
                        displayName = storedAudio.originalFileName,
                        originalFileName = storedAudio.originalFileName,
                        internalFilePath = storedAudio.internalFilePath,
                        volume = volume,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                playerManager.load(boxId, storedAudio.internalFilePath, volume)
                storage.deleteIfInternal(previousPath)
                updateRuntime(boxId) {
                    it.copy(
                        selectedUri = null,
                        selectedFileName = null,
                        playbackState = AudioPlaybackState.Saved,
                        statusMessage = "Salvo",
                    )
                }
            } catch (error: Throwable) {
                updateRuntime(boxId) {
                    it.copy(
                        playbackState = AudioPlaybackState.Error,
                        statusMessage = error.message ?: "Erro ao salvar áudio.",
                    )
                }
            }
        }
    }

    fun onPlayPauseClicked(boxId: Int) {
        val config = configFor(boxId)
        val path = config.internalFilePath ?: return
        val volume = (runtimeFor(boxId).volumeOverride ?: config.volume).coerceIn(0f, 1f)
        playerManager.togglePlay(boxId, path, volume)
    }

    fun onRestartClicked(boxId: Int) {
        val config = configFor(boxId)
        val path = config.internalFilePath ?: return
        val volume = (runtimeFor(boxId).volumeOverride ?: config.volume).coerceIn(0f, 1f)
        playerManager.restart(boxId, path, volume)
    }

    fun onVolumeChanged(boxId: Int, volume: Float) {
        val config = configFor(boxId)
        if (config.isLocked) return

        val normalizedVolume = volume.coerceIn(0f, 1f)
        playerManager.setVolume(boxId, normalizedVolume)
        updateRuntime(boxId) { it.copy(volumeOverride = normalizedVolume) }

        viewModelScope.launch {
            repository.saveConfig(
                config.copy(
                    volume = normalizedVolume,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onLockClicked(boxId: Int) {
        val config = configFor(boxId)

        viewModelScope.launch {
            repository.saveConfig(
                config.copy(
                    isLocked = !config.isLocked,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onStopAllClicked() {
        playerManager.stopAll()
    }

    override fun onCleared() {
        playerManager.release()
    }

    private fun AudioBoxConfig.toUiState(runtimeState: AudioBoxRuntimeState): AudioBoxUiState {
        val fileIsAvailable = storage.fileExists(internalFilePath)
        val hasPersistedPath = internalFilePath != null
        val effectiveVolume = (runtimeState.volumeOverride ?: volume).coerceIn(0f, 1f)
        val state = when {
            runtimeState.playbackState == AudioPlaybackState.Saving -> AudioPlaybackState.Saving
            runtimeState.selectedUri != null -> AudioPlaybackState.Unsaved
            hasPersistedPath && !fileIsAvailable -> AudioPlaybackState.Error
            runtimeState.playbackState != null -> runtimeState.playbackState
            fileIsAvailable -> AudioPlaybackState.Saved
            else -> AudioPlaybackState.Empty
        }
        val title = runtimeState.selectedFileName
            ?: originalFileName
            ?: "Box $id"
        val status = when {
            runtimeState.statusMessage != null -> runtimeState.statusMessage
            hasPersistedPath && !fileIsAvailable -> "Arquivo ausente"
            isLocked -> "Bloqueado"
            state == AudioPlaybackState.Empty -> "Vazio"
            state == AudioPlaybackState.Unsaved -> "Selecionado - salvar"
            state == AudioPlaybackState.Saved -> "Salvo"
            state == AudioPlaybackState.Playing -> "Tocando"
            state == AudioPlaybackState.Paused -> "Pausado"
            state == AudioPlaybackState.Saving -> "Salvando..."
            state == AudioPlaybackState.Error -> "Erro"
            else -> state.label
        }

        return AudioBoxUiState(
            id = id,
            displayName = title,
            originalFileName = originalFileName,
            internalFilePath = internalFilePath?.takeIf { fileIsAvailable },
            hasPendingAudio = runtimeState.selectedUri != null,
            volume = effectiveVolume,
            isLocked = isLocked,
            playbackState = state,
            statusMessage = status,
        )
    }

    private fun handlePlayerUpdate(update: AudioPlayerUpdate) {
        val state = when (update.state) {
            PlayerRuntimeState.Ready -> AudioPlaybackState.Saved
            PlayerRuntimeState.Playing -> AudioPlaybackState.Playing
            PlayerRuntimeState.Paused -> AudioPlaybackState.Paused
            PlayerRuntimeState.Error -> AudioPlaybackState.Error
        }
        val message = update.message ?: when (state) {
            AudioPlaybackState.Saved -> "Pronto"
            AudioPlaybackState.Playing -> "Tocando"
            AudioPlaybackState.Paused -> "Pausado"
            AudioPlaybackState.Error -> "Erro"
            else -> state.label
        }

        updateRuntime(update.boxId) {
            it.copy(
                playbackState = state,
                statusMessage = message,
            )
        }
    }

    private fun updateRuntime(boxId: Int, transform: (AudioBoxRuntimeState) -> AudioBoxRuntimeState) {
        runtimeStates.value = runtimeStates.value.toMutableMap().also { states ->
            states[boxId] = transform(states[boxId] ?: AudioBoxRuntimeState())
        }
    }

    private fun configFor(boxId: Int): AudioBoxConfig =
        configs.value.firstOrNull { it.id == boxId }
            ?: AudioBoxConfig.emptySlots().first { it.id == boxId }

    private fun runtimeFor(boxId: Int): AudioBoxRuntimeState =
        runtimeStates.value[boxId] ?: AudioBoxRuntimeState()
}

private data class AudioBoxRuntimeState(
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val volumeOverride: Float? = null,
    val playbackState: AudioPlaybackState? = null,
    val statusMessage: String? = null,
)
