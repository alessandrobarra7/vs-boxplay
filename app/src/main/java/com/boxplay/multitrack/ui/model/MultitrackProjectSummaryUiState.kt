package com.boxplay.multitrack.ui.model

/** Linha da tela de lista de projetos (especificação, seção 87). */
data class MultitrackProjectSummaryUiState(
    val projectId: String,
    val name: String,
    val trackCount: Int,
    val updatedAtLabel: String,
    val lastDestinationLabel: String?,
)
