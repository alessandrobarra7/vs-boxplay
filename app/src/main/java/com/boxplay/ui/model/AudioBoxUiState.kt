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

data class AudioBoxUiState(
    val id: Int,
    val displayName: String,
    val originalFileName: String?,
    val internalFilePath: String?,
    val hasPendingAudio: Boolean,
    val volume: Float,
    val isLocked: Boolean,
    val playbackState: AudioPlaybackState,
    val statusMessage: String,
) {
    val hasSavedAudio: Boolean
        get() = internalFilePath != null

    val canEditSettings: Boolean
        get() = !isLocked

    val canPickAudio: Boolean
        get() = canEditSettings && playbackState != AudioPlaybackState.Saving

    val canSave: Boolean
        get() = canEditSettings && hasPendingAudio && playbackState != AudioPlaybackState.Saving

    val canPlay: Boolean
        get() = hasSavedAudio && playbackState != AudioPlaybackState.Error && playbackState != AudioPlaybackState.Saving

    val canRestart: Boolean
        get() = hasSavedAudio && playbackState != AudioPlaybackState.Saving

    val canChangeVolume: Boolean
        get() = canEditSettings && playbackState != AudioPlaybackState.Saving
}
