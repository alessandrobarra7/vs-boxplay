package com.boxplay.multitrack.model

/**
 * Representa uma pista dentro de um projeto do editor multipista.
 *
 * Decisões importantes (ver especificação, seções 15-19):
 * - [id] é um identificador próprio (UUID), independente do boxId do BoxPlay
 *   (que é local à cena e não serve como identificador global de pista).
 * - [durationUs] e [offsetUs] são armazenados em microssegundos (Long), nunca
 *   inferidos a partir do tamanho do arquivo em bytes.
 * - [volume] é sempre mantido mesmo quando [muted] é true — mute nunca zera o
 *   valor de volume armazenado (seções 8 e 9).
 * - [offsetUs] nunca é negativo na V1 (seção 6).
 *
 * Use [MultitrackTrack.create] para criar novas pistas e os métodos `with*`
 * para alterá-las: eles aplicam os limites (clamps) definidos na
 * especificação. A função `copy()` gerada automaticamente pelo Kotlin NÃO
 * aplica esses limites, então evite usá-la diretamente para esses campos.
 */
data class MultitrackTrack(
    val id: String,
    val name: String,
    val internalFilePath: String,
    val originalFileName: String?,
    val durationUs: Long,
    val offsetUs: Long,
    val volume: Float,
    val muted: Boolean,
    val routing: TrackRouting,
    val sampleRate: Int?,
    val channelCount: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun withVolume(volume: Float, updatedAt: Long = this.updatedAt): MultitrackTrack =
        copy(volume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME), updatedAt = updatedAt)

    fun withMuted(muted: Boolean, updatedAt: Long = this.updatedAt): MultitrackTrack =
        copy(muted = muted, updatedAt = updatedAt)

    fun withRouting(routing: TrackRouting, updatedAt: Long = this.updatedAt): MultitrackTrack =
        copy(routing = routing, updatedAt = updatedAt)

    fun withOffsetUs(offsetUs: Long, updatedAt: Long = this.updatedAt): MultitrackTrack =
        copy(offsetUs = offsetUs.coerceAtLeast(0L), updatedAt = updatedAt)

    fun withName(name: String, updatedAt: Long = this.updatedAt): MultitrackTrack =
        copy(name = name, updatedAt = updatedAt)

    /** Desloca o offset em [deltaUs] (positivo ou negativo), sem nunca ficar negativo. */
    fun shiftOffsetUs(deltaUs: Long, updatedAt: Long = this.updatedAt): MultitrackTrack =
        withOffsetUs(offsetUs + deltaUs, updatedAt)

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f

        /** Ajuste fino padrão de posição usado pelos botões -10ms/+10ms (seção 6). */
        const val FINE_OFFSET_STEP_US = 10_000L

        fun create(
            id: String,
            name: String,
            internalFilePath: String,
            originalFileName: String?,
            durationUs: Long,
            offsetUs: Long = 0L,
            volume: Float = MAX_VOLUME,
            muted: Boolean = false,
            routing: TrackRouting = TrackRouting.CENTER,
            sampleRate: Int? = null,
            channelCount: Int? = null,
            createdAt: Long,
            updatedAt: Long = createdAt,
        ): MultitrackTrack = MultitrackTrack(
            id = id,
            name = name,
            internalFilePath = internalFilePath,
            originalFileName = originalFileName,
            durationUs = durationUs.coerceAtLeast(0L),
            offsetUs = offsetUs.coerceAtLeast(0L),
            volume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME),
            muted = muted,
            routing = routing,
            sampleRate = sampleRate,
            channelCount = channelCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
