package com.boxplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.ui.model.AudioBoxUiState
import com.boxplay.ui.model.AudioPlaybackState
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayCardBorder
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayHeader
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText
import com.boxplay.ui.theme.BoxPlayStatusGreen
import com.boxplay.ui.theme.BoxPlayWarning

@Composable
fun AudioBoxCard(
    state: AudioBoxUiState,
    onPickAudio: () -> Unit,
    onSave: () -> Unit,
    onToggleLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying = state.playbackState == AudioPlaybackState.Playing

    Card(
        modifier = modifier.height(148.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BoxPlayCard),
        border = BorderStroke(1.dp, BoxPlayCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.id}. ${state.displayName}",
                        color = BoxPlayPrimaryText,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.statusMessage,
                        color = statusColor(state),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                CompactIconButton(
                    icon = if (state.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (state.isLocked) "Desbloquear box ${state.id}" else "Bloquear box ${state.id}",
                    enabled = state.playbackState != AudioPlaybackState.Saving,
                    onClick = onToggleLock,
                    size = 30.dp,
                    backgroundColor = BoxPlayMutedControl,
                    contentColor = if (state.isLocked) BoxPlaySecondaryText else Color.White,
                )
            }

            DecorativeWaveform(
                color = BoxPlayElectricBlue,
                dimmed = !state.hasSavedAudio && !state.hasPendingAudio,
                height = 16.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompactIconButton(
                    icon = Icons.Rounded.FileUpload,
                    contentDescription = "Enviar áudio para box ${state.id}",
                    enabled = state.canPickAudio,
                    onClick = onPickAudio,
                    size = 34.dp,
                    backgroundColor = Color.White,
                    contentColor = BoxPlayHeader,
                )
                CompactIconButton(
                    icon = Icons.Rounded.Save,
                    contentDescription = "Salvar áudio do box ${state.id}",
                    enabled = state.canSave,
                    onClick = onSave,
                    size = 34.dp,
                    backgroundColor = Color.White,
                    contentColor = BoxPlayHeader,
                )
                CompactIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar box ${state.id}" else "Tocar box ${state.id}",
                    enabled = state.canPlay,
                    onClick = onTogglePlay,
                    size = 46.dp,
                    backgroundColor = BoxPlayCoral,
                    contentColor = Color.White,
                )
                CompactIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Reiniciar box ${state.id}",
                    enabled = state.canRestart,
                    onClick = onRestart,
                    size = 34.dp,
                    backgroundColor = BoxPlayMutedControl,
                    contentColor = Color.White,
                )
            }

            CompactVolumeControl(
                volume = state.volume,
                enabled = state.canChangeVolume,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

@Composable
private fun CompactVolumeControl(
    volume: Float,
    enabled: Boolean,
    onVolumeChange: (Float) -> Unit,
) {
    val clampedVolume = volume.coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = "Diminuir volume",
            enabled = enabled && clampedVolume > 0f,
            onClick = { onVolumeChange((clampedVolume - 0.10f).coerceIn(0f, 1f)) },
            size = 28.dp,
            backgroundColor = BoxPlayMutedControl,
            contentColor = Color.White,
        )

        LinearProgressIndicator(
            progress = { clampedVolume },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = if (enabled) BoxPlayElectricBlue else BoxPlayMutedControl,
            trackColor = BoxPlayMutedControl.copy(alpha = 0.42f),
        )

        Text(
            text = "${(clampedVolume * 100).toInt()}%",
            color = BoxPlaySecondaryText,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            modifier = Modifier.semantics {
                contentDescription = "Volume ${(clampedVolume * 100).toInt()} por cento"
            },
        )

        CompactIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "Aumentar volume",
            enabled = enabled && clampedVolume < 1f,
            onClick = { onVolumeChange((clampedVolume + 0.10f).coerceIn(0f, 1f)) },
            size = 28.dp,
            backgroundColor = BoxPlayMutedControl,
            contentColor = Color.White,
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp,
    backgroundColor: Color,
    contentColor: Color,
) {
    val disabledBackground = BoxPlayMutedControl.copy(alpha = 0.52f)
    val disabledContent = BoxPlaySecondaryText.copy(alpha = 0.58f)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) backgroundColor else disabledBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) contentColor else disabledContent,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

private fun statusColor(state: AudioBoxUiState): Color =
    when (state.playbackState) {
        AudioPlaybackState.Empty -> BoxPlaySecondaryText
        AudioPlaybackState.Unsaved -> BoxPlayWarning
        AudioPlaybackState.Saved -> if (state.isLocked) BoxPlaySecondaryText else BoxPlayStatusGreen
        AudioPlaybackState.Saving -> BoxPlayWarning
        AudioPlaybackState.Playing -> BoxPlayElectricBlue
        AudioPlaybackState.Paused -> BoxPlayWarning
        AudioPlaybackState.Error -> BoxPlayCoral
    }
