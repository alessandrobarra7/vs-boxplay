package com.boxplay.ui.model

enum class AudioPlaybackState(val label: String) {
    Empty("Vazio"),
    Unsaved("Não salvo"),
    Saved("Salvo"),
    Saving("Salvando"),
    Playing("Tocando"),
    Paused("Pausado"),
    Error("Erro"),
}

data class AudioSceneUiState(
    val id: Int,
    val name: String,
    val boxCount: Int,
    val isSelected: Boolean,
    val isLocked: Boolean,
)

data class AudioSceneSectionUiState(
    val scene: AudioSceneUiState,
    val boxes: List<AudioBoxUiState>,
    val canAddBox: Boolean,
)

data class AudioBoxUiState(
    val id: Int,
    val sceneId: Int = 1,
    val displayName: String,
    val customLabel: String? = null,
    val originalFileName: String?,
    val internalFilePath: String?,
    val hasPendingAudio: Boolean,
    val volume: Float,
    val isLocked: Boolean,
    val isSceneLocked: Boolean = false,
    val playbackState: AudioPlaybackState,
    val statusMessage: String,
    // FIX: isLocked is the user's own manual "cadeado" toggle. isLockedByPaywall
    // is the commercial gate from the one-time-purchase entitlement
    // (docs/BOXPLAY_PLANO_COMPRA_UNICA_PLAYSTORE_V1.txt). They are independent
    // reasons a box can be restricted: a box can be locked by either, both, or
    // neither. Unlike the manual lock (which still allows playback of already
    // saved audio), the paywall blocks every action, including playback,
    // because the box's content is not something the user has paid to use.
    val isLockedByPaywall: Boolean = false,
) {
    val hasSavedAudio: Boolean
        get() = internalFilePath != null

    val canEditSettings: Boolean
        get() = !isLocked && !isLockedByPaywall

    val canPickAudio: Boolean
        get() = canEditSettings && playbackState != AudioPlaybackState.Saving

    val canSave: Boolean
        get() = canEditSettings && hasPendingAudio && playbackState != AudioPlaybackState.Saving

    val canPlay: Boolean
        get() = !isLockedByPaywall && hasSavedAudio && playbackState != AudioPlaybackState.Error && playbackState != AudioPlaybackState.Saving

    val canRestart: Boolean
        get() = !isLockedByPaywall && hasSavedAudio && playbackState != AudioPlaybackState.Saving

    val canChangeVolume: Boolean
        get() = canEditSettings && playbackState != AudioPlaybackState.Saving

    val canRename: Boolean
        get() = canEditSettings && playbackState != AudioPlaybackState.Saving

    val canDelete: Boolean
        get() = !isSceneLocked && canEditSettings && playbackState != AudioPlaybackState.Saving
}