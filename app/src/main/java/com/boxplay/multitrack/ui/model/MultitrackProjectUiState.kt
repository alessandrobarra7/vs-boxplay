package com.boxplay.multitrack.ui.model

/** Indicador de autosave exibido na UI (especificação, seção 55). */
enum class ProjectSaveStatus {
    SALVO,
    SALVANDO,
    ERRO_AO_SALVAR,
}

data class MultitrackProjectUiState(
    val projectId: String,
    val name: String,
    val tracks: List<MultitrackTrackUiState>,
    val durationLabel: String,
    val saveStatus: ProjectSaveStatus,
    val linkedSceneId: Int?,
    val linkedBoxId: Int?,
)
