package com.boxplay.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AudioPlayerManager(
    private val context: Context,
    private val onUpdate: (AudioPlayerUpdate) -> Unit,
) {
    private val holders = mutableMapOf<Int, PlayerHolder>()

    fun togglePlay(boxId: Int, path: String, volume: Float) {
        val file = File(path)
        if (!file.isFile) {
            onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Error, "Arquivo salvo não encontrado."))
            return
        }

        val holder = holderFor(boxId)
        loadIfNeeded(holder, path, volume)

        if (holder.player.isPlaying) {
            holder.player.pause()
            onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Paused))
            return
        }

        if (holder.player.playbackState == Player.STATE_ENDED) {
            holder.player.seekTo(0)
        }

        pauseOtherPlayers(boxId)
        holder.player.play()
        onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Playing))
    }

    fun restart(boxId: Int, path: String, volume: Float) {
        val file = File(path)
        if (!file.isFile) {
            onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Error, "Arquivo salvo não encontrado."))
            return
        }

        val holder = holderFor(boxId)
        val wasPlaying = holder.player.isPlaying
        loadIfNeeded(holder, path, volume)
        holder.player.seekTo(0)

        if (wasPlaying) {
            pauseOtherPlayers(boxId)
            holder.player.play()
            onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Playing))
        } else {
            holder.player.pause()
            onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Ready))
        }
    }

    fun setVolume(boxId: Int, volume: Float) {
        holders[boxId]?.player?.volume = volume.coerceIn(0f, 1f)
    }

    fun load(boxId: Int, path: String, volume: Float) {
        val file = File(path)
        if (!file.isFile) return
        loadIfNeeded(holderFor(boxId), path, volume)
    }

    fun stop(boxId: Int) {
        holders[boxId]?.let { holder ->
            holder.player.pause()
            holder.player.seekTo(0)
            if (holder.sourcePath != null) {
                onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Ready))
            }
        }
    }

    fun release(boxId: Int) {
        holders.remove(boxId)?.player?.release()
    }

    fun stopAll() {
        holders.forEach { (boxId, holder) ->
            if (holder.sourcePath != null) {
                holder.player.pause()
                holder.player.seekTo(0)
                onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Ready))
            }
        }
    }

    fun release() {
        holders.values.forEach { it.player.release() }
        holders.clear()
    }

    private fun loadIfNeeded(holder: PlayerHolder, path: String, volume: Float) {
        holder.player.volume = volume.coerceIn(0f, 1f)

        if (holder.sourcePath == path) return

        holder.sourcePath = path
        holder.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        holder.player.prepare()
    }

    private fun holderFor(boxId: Int): PlayerHolder = holders.getOrPut(boxId) {
        val player = ExoPlayer.Builder(context).build()
        val holder = PlayerHolder(player = player)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (holder.sourcePath == null) return
                if (isPlaying) {
                    onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Playing))
                } else if (player.playbackState != Player.STATE_ENDED) {
                    onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Paused))
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (holder.sourcePath == null || playbackState != Player.STATE_ENDED) return
                player.pause()
                player.seekTo(0)
                onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Ready))
            }

            override fun onPlayerError(error: PlaybackException) {
                onUpdate(
                    AudioPlayerUpdate(
                        boxId = boxId,
                        state = PlayerRuntimeState.Error,
                        message = error.message ?: "Erro ao reproduzir áudio.",
                    ),
                )
            }
        })

        holder
    }

    private fun pauseOtherPlayers(activeBoxId: Int) {
        holders.forEach { (boxId, holder) ->
            if (boxId != activeBoxId && holder.player.isPlaying) {
                holder.player.pause()
                onUpdate(AudioPlayerUpdate(boxId, PlayerRuntimeState.Paused))
            }
        }
    }

    private data class PlayerHolder(
        val player: ExoPlayer,
        var sourcePath: String? = null,
    )
}

data class AudioPlayerUpdate(
    val boxId: Int,
    val state: PlayerRuntimeState,
    val message: String? = null,
)

enum class PlayerRuntimeState {
    Ready,
    Playing,
    Paused,
    Error,
}