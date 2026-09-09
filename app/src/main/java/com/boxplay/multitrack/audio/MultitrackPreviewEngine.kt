package com.boxplay.multitrack.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Motor de pré-escuta (Preview) do editor multipista: toca todas as pistas
 * de um projeto ao mesmo tempo — sincronizadas pelo offset relativo entre
 * elas — e permite ajustar o volume de cada uma com a reprodução em
 * andamento (botão "Tocar", especificação seção 80/Fase 4).
 *
 * Cada pista ganha seu próprio [ExoPlayer], na mesma linha de
 * [com.boxplay.audio.AudioPlayerManager] (já usado para os Boxes) — a
 * diferença é que aqui todas tocam ao mesmo tempo em vez de uma de cada
 * vez. Pan por roteamento L/C/R ainda não é aplicado nesta pré-escuta
 * (fica só o volume/mudo, que é o que a seção pediu); a mixagem final para
 * exportação é trabalho separado (Fase 5).
 */
class MultitrackPreviewEngine(private val context: Context) {

    data class PreviewTrack(
        val trackId: String,
        val filePath: String,
        val volume: Float,
        val muted: Boolean,
        val offsetUs: Long,
    )

    private data class Holder(val player: ExoPlayer)

    private val holders = mutableMapOf<String, Holder>()
    private val pendingStarts = mutableListOf<Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())

    var isPlaying: Boolean = false
        private set

    /** Para tudo e começa a tocar [tracks] em sincronia. */
    fun play(tracks: List<PreviewTrack>) {
        stop()
        if (tracks.isEmpty()) return

        tracks.forEach { track ->
            val file = File(track.filePath)
            if (!file.isFile) return@forEach
            val holder = holderFor(track.trackId)
            holder.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            holder.player.volume = if (track.muted) 0f else track.volume.coerceIn(0f, 1f)
            holder.player.prepare()
        }

        val baseOffsetUs = tracks.minOf { it.offsetUs }
        tracks.forEach { track ->
            val holder = holders[track.trackId] ?: return@forEach
            val delayMs = (track.offsetUs - baseOffsetUs) / 1000L
            if (delayMs <= 0L) {
                holder.player.play()
            } else {
                val runnable = Runnable { holder.player.play() }
                pendingStarts.add(runnable)
                mainHandler.postDelayed(runnable, delayMs)
            }
        }

        isPlaying = true
    }

    /** Ajusta o volume/mudo de uma pista já tocando, sem reiniciar nada. */
    fun setVolume(trackId: String, volume: Float, muted: Boolean) {
        holders[trackId]?.player?.volume = if (muted) 0f else volume.coerceIn(0f, 1f)
    }

    fun stop() {
        pendingStarts.forEach { mainHandler.removeCallbacks(it) }
        pendingStarts.clear()
        holders.values.forEach { it.player.stop() }
        isPlaying = false
    }

    /** Libera todos os players — chame quando o editor for fechado de vez. */
    fun release() {
        stop()
        holders.values.forEach { it.player.release() }
        holders.clear()
    }

    private fun holderFor(trackId: String): Holder = holders.getOrPut(trackId) {
        Holder(player = ExoPlayer.Builder(context).build())
    }
}
