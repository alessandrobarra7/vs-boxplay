package com.boxplay.multitrack.model

/**
 * Projeto do editor multipista. É uma entidade independente das cenas/boxes
 * do BoxPlay (ver especificação, seção 4/96) — a associação com um box só
 * existe através de [linkedSceneId]/[linkedBoxId] e do arquivo apontado por
 * [lastRenderedFilePath]. Nada aqui substitui ou depende do schema de
 * cenas/boxes já existente no DataStore do soundboard.
 */
data class MultitrackProject(
    val id: String,
    val name: String,
    val tracks: List<MultitrackTrack> = emptyList(),
    val linkedSceneId: Int? = null,
    val linkedBoxId: Int? = null,
    val lastRenderedFilePath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    /**
     * Duração do projeto: o maior (offset + duração) entre as pistas (seção
     * 14). Uma pista mutada ainda conta para essa duração — mute é uma
     * decisão de mix, não de exclusão do projeto.
     */
    val durationUs: Long
        get() = tracks.maxOfOrNull { it.offsetUs + it.durationUs } ?: 0L

    fun withTrack(track: MultitrackTrack, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(tracks = tracks + track, updatedAt = updatedAt)

    fun withoutTrack(trackId: String, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(tracks = tracks.filterNot { it.id == trackId }, updatedAt = updatedAt)

    fun replacingTrack(updatedTrack: MultitrackTrack, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(
            tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            updatedAt = updatedAt,
        )

    fun withName(name: String, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(name = name, updatedAt = updatedAt)

    fun withLinkedDestination(sceneId: Int?, boxId: Int?, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(linkedSceneId = sceneId, linkedBoxId = boxId, updatedAt = updatedAt)

    fun withLastRenderedFilePath(path: String?, updatedAt: Long = this.updatedAt): MultitrackProject =
        copy(lastRenderedFilePath = path, updatedAt = updatedAt)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
