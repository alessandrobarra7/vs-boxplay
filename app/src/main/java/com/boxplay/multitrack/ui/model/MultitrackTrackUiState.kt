package com.boxplay.multitrack.ui.model

import com.boxplay.multitrack.model.TrackRouting

/**
 * Estado do arquivo de uma pista, exibido na UI (ver especificação, seção
 * 22 e 82). A V1 da UI (Fase 2) ainda não faz a validação real do arquivo
 * decodificável — isso é trabalho da Fase 3 — então por enquanto qualquer
 * pista recém-importada é READY, e MISSING só é detectado se o arquivo
 * interno tiver desaparecido do disco.
 */
enum class TrackFileStatus {
    READY,
    MISSING,
    INVALID,
}

data class MultitrackTrackUiState(
    val id: String,
    val name: String,
    val routing: TrackRouting,
    val volume: Float,
    val muted: Boolean,
    val offsetLabel: String,
    val durationLabel: String,
    val offsetUs: Long,
    val fileStatus: TrackFileStatus,
    val expanded: Boolean,
) {
    val volumePercent: Int
        get() = (volume * 100f).toInt().coerceIn(0, 100)
}
