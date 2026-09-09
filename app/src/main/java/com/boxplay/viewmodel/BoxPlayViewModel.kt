package com.boxplay.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boxplay.audio.AudioPlayerManager
import com.boxplay.audio.AudioPlayerUpdate
import com.boxplay.audio.PlayerRuntimeState
import com.boxplay.billing.BillingManager
import com.boxplay.billing.BoxPlayBillingConfig
import com.boxplay.billing.PurchaseEntitlement
import com.boxplay.data.AudioBoxConfig
import com.boxplay.data.AudioBoxRepository
import com.boxplay.data.AudioSceneConfig
import com.boxplay.data.AudioSceneState
import com.boxplay.data.DeleteBoxResult
import com.boxplay.data.DeleteSceneResult
import com.boxplay.data.LocalAudioStorage
import com.boxplay.ui.model.AudioBoxUiState
import com.boxplay.ui.model.AudioPlaybackState
import com.boxplay.ui.model.AudioSceneSectionUiState
import com.boxplay.ui.model.AudioSceneUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoxPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioBoxRepository(application.applicationContext)
    private val storage = LocalAudioStorage(application.applicationContext)
    private val sceneState = MutableStateFlow(AudioSceneState.default())
    private val runtimeStates = MutableStateFlow<Map<Int, AudioBoxRuntimeState>>(emptyMap())
    private val _uiMessage = MutableStateFlow<String?>(null)

    // FIX (investigação "botão Salvar desacoplado do multipista"): o estado
    // "runtime" de um Box (Selecionado-salvar/Salvando/Erro/mensagem) só
    // existe para acompanhar uma ação em andamento NESTA tela. Quando o
    // arquivo persistido de um Box muda por fora daqui — por exemplo, o
    // editor multipista sincronizando um áudio renderizado direto num Box —
    // esse runtime antigo ficava "grudado" e escondia o novo áudio já
    // salvo. Guardamos aqui o último `updatedAtEpochMillis` visto de cada
    // Box para detectar essas mudanças externas e limpar o runtime
    // correspondente, deixando a tela voltar a refletir o estado salvo de
    // verdade.
    private val lastKnownConfigTimestamps = mutableMapOf<Int, Long?>()

    // One-time-purchase gating per docs/BOXPLAY_PLANO_COMPRA_UNICA_PLAYSTORE_V1.txt:
    // only box BoxPlayBillingConfig.FreeBoxId of scene
    // BoxPlayBillingConfig.FreeSceneId stays free; every other box in every
    // scene requires entitlement.unlocksAllBoxes. See BoxPlayBillingConfig
    // for why this is keyed on (sceneId, boxId) rather than boxId alone.
    private val billingManager = BillingManager(application.applicationContext)
    val entitlement: StateFlow<PurchaseEntitlement> = billingManager.entitlement
    val unlockPriceText: StateFlow<String?> = billingManager.unlockProduct
        .map { it?.oneTimePurchaseOfferDetails?.formattedPrice }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val billingError: StateFlow<String?> = billingManager.billingError
    val uiMessage: StateFlow<String?> = _uiMessage

    private val playerManager = AudioPlayerManager(application.applicationContext) { update ->
        handlePlayerUpdate(update)
    }

    val scenes: StateFlow<List<AudioSceneUiState>> = sceneState
        .map { state ->
            state.scenes.map { scene -> scene.toUiState(scene.id == state.selectedSceneId) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AudioSceneState.default().scenes.map { scene -> scene.toUiState(true) },
        )

    val selectedScene: StateFlow<AudioSceneUiState> = sceneState
        .map { state -> state.selectedScene.toUiState(true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AudioSceneConfig.default().toUiState(true),
        )

    val canCreateScene: StateFlow<Boolean> = sceneState
        .map { it.canCreateScene }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val canAddBox: StateFlow<Boolean> = sceneState
        .map { state -> state.selectedScene.canAddBox() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val sceneSections: StateFlow<List<AudioSceneSectionUiState>> = combine(
        sceneState,
        runtimeStates,
        billingManager.entitlement,
    ) { currentSceneState, runtime, currentEntitlement ->
        currentSceneState.scenes.map { scene ->
            val sceneUi = scene.toUiState(scene.id == currentSceneState.selectedSceneId)
            AudioSceneSectionUiState(
                scene = sceneUi,
                boxes = scene.boxes.map { config ->
                    val runtimeKey = playerKey(scene.id, config.id)
                    val runtimeState = runtime[runtimeKey] ?: AudioBoxRuntimeState()
                    config.toUiState(scene.id, scene.isLocked, runtimeState, currentEntitlement)
                },
                canAddBox = scene.canAddBox(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = listOf(
            AudioSceneSectionUiState(
                scene = AudioSceneConfig.default().toUiState(true),
                boxes = emptyList(),
                canAddBox = true,
            ),
        ),
    )

    val boxes: StateFlow<List<AudioBoxUiState>> = sceneSections
        .map { sections -> sections.firstOrNull { it.scene.isSelected }?.boxes.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        repository.sceneState
            .onEach { loadedState ->
                reconcileRuntimeStates(loadedState)
                sceneState.value = loadedState
            }
            .launchIn(viewModelScope)
        billingManager.start()
    }

    /**
     * Descarta o runtime em memória de qualquer Box cujo `updatedAtEpochMillis`
     * persistido mudou desde a última vez que observamos — sinal de que algo
     * fora desta tela (ex.: `MultitrackViewModel.confirmExportDestination`)
     * gravou um novo áudio direto no repositório. Sem isso, um runtime antigo
     * (de uma seleção nunca salva, ou de um erro anterior) continuava sendo
     * exibido por cima do áudio novo já sincronizado.
     */
    private fun reconcileRuntimeStates(loadedState: AudioSceneState) {
        var changed = false
        val updatedRuntimeStates = runtimeStates.value.toMutableMap()

        loadedState.scenes.forEach { scene ->
            scene.boxes.forEach { box ->
                val runtimeKey = playerKey(scene.id, box.id)
                val lastKnownTimestamp = lastKnownConfigTimestamps[runtimeKey]
                if (lastKnownTimestamp != null &&
                    lastKnownTimestamp != box.updatedAtEpochMillis &&
                    updatedRuntimeStates.remove(runtimeKey) != null
                ) {
                    changed = true
                }
                lastKnownConfigTimestamps[runtimeKey] = box.updatedAtEpochMillis
            }
        }

        if (changed) {
            runtimeStates.value = updatedRuntimeStates
        }
    }

    fun onUnlockClicked(activity: Activity) {
        billingManager.launchPurchaseFlow(activity)
    }

    fun onBillingErrorShown() {
        billingManager.clearError()
    }

    fun onUiMessageShown() {
        _uiMessage.value = null
    }

    private fun isLockedByPaywall(sceneId: Int, boxId: Int): Boolean =
        BoxPlayBillingConfig.isPremiumBox(sceneId, boxId) && !billingManager.entitlement.value.unlocksAllBoxes

    fun onSceneSelected(sceneId: Int) {
        viewModelScope.launch {
            repository.selectScene(sceneId)
        }
    }

    fun onCreateScene(name: String) {
        viewModelScope.launch {
            repository.createScene(name)
        }
    }

    fun onRenameSceneClicked(sceneId: Int, name: String) {
        viewModelScope.launch {
            repository.renameScene(sceneId, name)
        }
    }

    fun onDeleteSceneClicked(sceneId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteScene(sceneId)) {
                is DeleteSceneResult.Deleted -> {
                    result.boxIds.forEach { boxId -> releaseRuntime(playerKey(sceneId, boxId)) }
                    result.internalFilePaths.forEach(storage::deleteIfInternal)
                }
                DeleteSceneResult.LastScene -> _uiMessage.value = "A última cena não pode ser excluída."
                DeleteSceneResult.Locked -> _uiMessage.value = "Destrave a cena antes de excluir."
                DeleteSceneResult.NotFound -> _uiMessage.value = "Cena não encontrada."
            }
        }
    }

    fun onAddBoxClicked() {
        onAddBoxClicked(sceneState.value.selectedScene.id)
    }

    fun onAddBoxClicked(sceneId: Int) {
        val scene = sceneState.value.scenes.firstOrNull { it.id == sceneId } ?: return
        if (!scene.canAddBox()) return

        viewModelScope.launch {
            repository.addBox(scene.id)
        }
    }

    fun onToggleSceneLockClicked() {
        onToggleSceneLockClicked(sceneState.value.selectedScene.id)
    }

    fun onToggleSceneLockClicked(sceneId: Int) {
        val scene = sceneState.value.scenes.firstOrNull { it.id == sceneId } ?: return
        viewModelScope.launch {
            repository.setSceneLocked(scene.id, !scene.isLocked)
        }
    }

    fun onDeleteBoxClicked(sceneId: Int, boxId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteBox(sceneId, boxId)) {
                is DeleteBoxResult.Deleted -> {
                    releaseRuntime(playerKey(sceneId, boxId))
                    storage.deleteIfInternal(result.internalFilePath)
                }
                DeleteBoxResult.SceneLocked -> _uiMessage.value = "Destrave a cena antes de excluir o box."
                DeleteBoxResult.NotFound -> _uiMessage.value = "Box não encontrado."
            }
        }
    }

    fun onBoxLabelChanged(sceneId: Int, boxId: Int, label: String) {
        val config = configFor(sceneId, boxId)
        val normalizedLabel = label.trim().take(MAX_BOX_LABEL_LENGTH).takeIf { it.isNotBlank() }

        viewModelScope.launch {
            repository.saveConfig(
                sceneId,
                config.copy(
                    customLabel = normalizedLabel,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onAudioSelected(boxId: Int, uri: Uri) {
        onAudioSelected(selectedSceneId(), boxId, uri)
    }

    fun onAudioSelected(sceneId: Int, boxId: Int, uri: Uri) {
        val config = configFor(sceneId, boxId)
        if (config.isLocked || isLockedByPaywall(sceneId, boxId)) return

        val fileName = storage.readDisplayName(uri)
        updateRuntime(playerKey(sceneId, boxId)) {
            it.copy(
                selectedUri = uri,
                selectedFileName = fileName,
                playbackState = AudioPlaybackState.Unsaved,
                statusMessage = "Selecionado - salvar",
            )
        }
    }

    fun onSaveClicked(boxId: Int) {
        onSaveClicked(selectedSceneId(), boxId)
    }

    fun onSaveClicked(sceneId: Int, boxId: Int) {
        val runtimeKey = playerKey(sceneId, boxId)
        val config = configFor(sceneId, boxId)
        val runtimeState = runtimeFor(runtimeKey)
        val selectedUri = runtimeState.selectedUri ?: return

        if (config.isLocked || isLockedByPaywall(sceneId, boxId)) return

        viewModelScope.launch {
            updateRuntime(runtimeKey) {
                it.copy(
                    playbackState = AudioPlaybackState.Saving,
                    statusMessage = "Salvando...",
                )
            }

            val previousPath = config.internalFilePath
            val volume = (runtimeFor(runtimeKey).volumeOverride ?: config.volume).coerceIn(0f, 1f)

            try {
                val storedAudio = storage.copyFromUri(runtimeKey, selectedUri)
                repository.saveConfig(
                    sceneId,
                    config.copy(
                        displayName = storedAudio.originalFileName,
                        originalFileName = storedAudio.originalFileName,
                        internalFilePath = storedAudio.internalFilePath,
                        volume = volume,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                playerManager.load(runtimeKey, storedAudio.internalFilePath, volume)
                storage.deleteIfInternal(previousPath)
                updateRuntime(runtimeKey) {
                    it.copy(
                        selectedUri = null,
                        selectedFileName = null,
                        playbackState = AudioPlaybackState.Saved,
                        statusMessage = "Salvo",
                    )
                }
            } catch (error: Throwable) {
                updateRuntime(runtimeKey) {
                    it.copy(
                        playbackState = AudioPlaybackState.Error,
                        statusMessage = error.message ?: "Erro ao salvar áudio.",
                    )
                }
            }
        }
    }

    fun onPlayPauseClicked(boxId: Int) {
        onPlayPauseClicked(selectedSceneId(), boxId)
    }

    fun onPlayPauseClicked(sceneId: Int, boxId: Int) {
        if (isLockedByPaywall(sceneId, boxId)) return
        val runtimeKey = playerKey(sceneId, boxId)
        val config = configFor(sceneId, boxId)
        val path = config.internalFilePath ?: return
        val volume = (runtimeFor(runtimeKey).volumeOverride ?: config.volume).coerceIn(0f, 1f)
        playerManager.togglePlay(runtimeKey, path, volume)
    }

    fun onRestartClicked(boxId: Int) {
        onRestartClicked(selectedSceneId(), boxId)
    }

    fun onRestartClicked(sceneId: Int, boxId: Int) {
        if (isLockedByPaywall(sceneId, boxId)) return
        val runtimeKey = playerKey(sceneId, boxId)
        val config = configFor(sceneId, boxId)
        val path = config.internalFilePath ?: return
        val volume = (runtimeFor(runtimeKey).volumeOverride ?: config.volume).coerceIn(0f, 1f)
        playerManager.restart(runtimeKey, path, volume)
    }

    fun onVolumeChanged(boxId: Int, volume: Float) {
        onVolumeChanged(selectedSceneId(), boxId, volume)
    }

    fun onVolumeChanged(sceneId: Int, boxId: Int, volume: Float) {
        val runtimeKey = playerKey(sceneId, boxId)
        val config = configFor(sceneId, boxId)
        if (config.isLocked || isLockedByPaywall(sceneId, boxId)) return

        val normalizedVolume = volume.coerceIn(0f, 1f)
        playerManager.setVolume(runtimeKey, normalizedVolume)
        updateRuntime(runtimeKey) { it.copy(volumeOverride = normalizedVolume) }

        viewModelScope.launch {
            repository.saveConfig(
                sceneId,
                config.copy(
                    volume = normalizedVolume,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun onLockClicked(boxId: Int) {
        onLockClicked(selectedSceneId(), boxId)
    }

    fun onLockClicked(sceneId: Int, boxId: Int) {
        val config = configFor(sceneId, boxId)

        viewModelScope.launch {
            repository.saveConfig(
                sceneId,
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
        billingManager.destroy()
    }

    private fun AudioSceneConfig.toUiState(isSelected: Boolean): AudioSceneUiState = AudioSceneUiState(
        id = id,
        name = name,
        boxCount = boxCount,
        isSelected = isSelected,
        isLocked = isLocked,
    )

    private fun AudioSceneConfig.canAddBox(): Boolean =
        !isLocked && boxCount < AudioBoxConfig.MAX_BOX_COUNT

    private fun AudioBoxConfig.toUiState(
        sceneId: Int,
        sceneIsLocked: Boolean,
        runtimeState: AudioBoxRuntimeState,
        currentEntitlement: PurchaseEntitlement,
    ): AudioBoxUiState {
        val fileIsAvailable = storage.fileExists(internalFilePath)
        val hasPersistedPath = internalFilePath != null
        val effectiveVolume = (runtimeState.volumeOverride ?: volume).coerceIn(0f, 1f)
        val lockedByPaywall = BoxPlayBillingConfig.isPremiumBox(sceneId, id) && !currentEntitlement.unlocksAllBoxes
        val state = when {
            runtimeState.playbackState == AudioPlaybackState.Saving -> AudioPlaybackState.Saving
            runtimeState.selectedUri != null -> AudioPlaybackState.Unsaved
            hasPersistedPath && !fileIsAvailable -> AudioPlaybackState.Error
            runtimeState.playbackState != null -> runtimeState.playbackState
            fileIsAvailable -> AudioPlaybackState.Saved
            else -> AudioPlaybackState.Empty
        }
        val title = customLabel
            ?: runtimeState.selectedFileName
            ?: originalFileName
            ?: "Box $id"
        val status = when {
            lockedByPaywall -> "Compre para desbloquear"
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
            sceneId = sceneId,
            displayName = title,
            customLabel = customLabel,
            originalFileName = originalFileName,
            internalFilePath = internalFilePath?.takeIf { fileIsAvailable },
            hasPendingAudio = runtimeState.selectedUri != null,
            volume = effectiveVolume,
            isLocked = isLocked,
            isSceneLocked = sceneIsLocked,
            playbackState = state,
            statusMessage = status,
            isLockedByPaywall = lockedByPaywall,
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

    private fun releaseRuntime(runtimeKey: Int) {
        playerManager.stop(runtimeKey)
        playerManager.release(runtimeKey)
        removeRuntime(runtimeKey)
    }

    private fun updateRuntime(runtimeKey: Int, transform: (AudioBoxRuntimeState) -> AudioBoxRuntimeState) {
        runtimeStates.value = runtimeStates.value.toMutableMap().also { states ->
            states[runtimeKey] = transform(states[runtimeKey] ?: AudioBoxRuntimeState())
        }
    }

    private fun removeRuntime(runtimeKey: Int) {
        runtimeStates.value = runtimeStates.value.toMutableMap().also { states ->
            states.remove(runtimeKey)
        }
    }

    private fun selectedSceneId(): Int = sceneState.value.selectedScene.id

    private fun configFor(sceneId: Int, boxId: Int): AudioBoxConfig =
        sceneState.value.scenes
            .firstOrNull { it.id == sceneId }
            ?.boxes
            ?.firstOrNull { it.id == boxId }
            ?: AudioBoxConfig.emptySlot(boxId)

    private fun runtimeFor(runtimeKey: Int): AudioBoxRuntimeState =
        runtimeStates.value[runtimeKey] ?: AudioBoxRuntimeState()

    private fun playerKey(sceneId: Int, boxId: Int): Int = sceneId * PLAYER_KEY_SCENE_MULTIPLIER + boxId

    companion object {
        private const val PLAYER_KEY_SCENE_MULTIPLIER = 1_000
        private const val MAX_BOX_LABEL_LENGTH = 36
    }
}

private data class AudioBoxRuntimeState(
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val volumeOverride: Float? = null,
    val playbackState: AudioPlaybackState? = null,
    val statusMessage: String? = null,
)