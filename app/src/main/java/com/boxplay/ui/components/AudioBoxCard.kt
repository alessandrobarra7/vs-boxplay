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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText
import com.boxplay.ui.theme.BoxPlayStatusGreen
import com.boxplay.ui.theme.BoxPlaySurfaceSoft
import com.boxplay.ui.theme.BoxPlayWarning

// NOVA IDENTIDADE VISUAL ("vidro azul", referência: maquete "Soundboard
// Studio" enviada pelo dono do produto). Assinatura pública IDÊNTICA à
// versão anterior — nenhum call site (BoxPlayScreen/AudioBoxRow) precisa
// mudar. Só o desenho interno do card foi reformulado: cartão translúcido
// com borda/brilho coloridos por estado (verde tocando ou travado, coral
// erro, dourado só no paywall), forma de onda pulsante e um badge
// monoespaçado para o número do Box.

/**
 * Card de um único Box do BoxPlay — a peça central do "soundboard".
 */
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
    onUnlockClicked: () -> Unit = {},
    onEditLabel: () -> Unit = {},
    onDelete: () -> Unit = {},
    unlockPriceText: String? = null,
) {
    val isPlaying = state.playbackState == AudioPlaybackState.Playing
    val glowColor = boxGlowColor(state)
    val borderColor = if (glowColor != null) glowColor.copy(alpha = 0.65f) else BoxPlayCardBorder
    val cardShape = RoundedCornerShape(14.dp)

    // UX: uma vez que o Box já está configurado, salvo e travado no
    // cadeado (fluxo normal de uso ao vivo), o botão de play pequeno vira
    // um alvo de toque difícil de acertar sob pressão. Nesse estado o card
    // inteiro passa a funcionar como o próprio botão de tocar/pausar — os
    // controles pequenos continuam lá (editar, salvar, travar, reiniciar)
    // para quando o Box precisa ser reconfigurado (destravando-o primeiro).
    val isTapToPlayEnabled = state.isLocked && state.canPlay
    val cardClickModifier = if (isTapToPlayEnabled) {
        Modifier.clickable(
            onClickLabel = if (isPlaying) "Pausar box ${state.id}" else "Tocar box ${state.id}",
            role = Role.Button,
            onClick = onTogglePlay,
        )
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .then(
                if (glowColor != null) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = cardShape,
                        ambientColor = glowColor,
                        spotColor = glowColor,
                    )
                } else {
                    Modifier
                },
            )
            .then(cardClickModifier),
        shape = cardShape,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = BoxPlayCard.copy(alpha = 0.88f)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 1) Cabeçalho: badge numérico + título + editar/excluir
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BoxPlaySurfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.id.toString().padStart(2, '0'),
                        color = BoxPlayElectricBlue,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    )
                }
                Text(
                    text = state.displayName,
                    modifier = Modifier.weight(1f),
                    color = BoxPlayPrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompactIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = "Renomear box ${state.id}",
                        enabled = state.canRename,
                        onClick = onEditLabel,
                        size = 20.dp,
                    )
                    CompactIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Excluir box ${state.id}",
                        enabled = state.canDelete,
                        onClick = onDelete,
                        size = 20.dp,
                        dangerTint = true,
                    )
                }
            }

            if (state.isLockedByPaywall) {
                Spacer(modifier = Modifier.height(6.dp))
                PaywallCardContent(
                    boxId = state.id,
                    priceText = unlockPriceText,
                    onUnlockClicked = onUnlockClicked,
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                DecorativeWaveform(
                    color = boxWaveformColor(state),
                    dimmed = !isPlaying,
                    height = 24.dp,
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.statusMessage.uppercase(),
                    color = boxGlowColor(state) ?: BoxPlaySecondaryText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.playbackState == AudioPlaybackState.Saving) {
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BoxPlayElectricBlue,
                        trackColor = BoxPlayMutedControl,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    CompactIconButton(
                        icon = Icons.Rounded.FileUpload,
                        contentDescription = "Enviar áudio para box ${state.id}",
                        enabled = state.canPickAudio,
                        onClick = onPickAudio,
                        modifier = Modifier.weight(1f),
                        size = 30.dp,
                        fillWidth = true,
                    )
                    CompactIconButton(
                        icon = Icons.Rounded.Save,
                        contentDescription = "Salvar áudio do box ${state.id}",
                        enabled = state.canSave,
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        size = 30.dp,
                        fillWidth = true,
                    )
                    CompactIconButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar box ${state.id}" else "Tocar box ${state.id}",
                        enabled = state.canPlay,
                        active = isPlaying,
                        onClick = onTogglePlay,
                        modifier = Modifier.weight(1f),
                        size = 30.dp,
                        fillWidth = true,
                    )
                    CompactIconButton(
                        icon = if (state.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = if (state.isLocked) "Desbloquear box ${state.id}" else "Bloquear box ${state.id}",
                        enabled = !state.isLockedByPaywall,
                        active = state.isLocked,
                        onClick = onToggleLock,
                        modifier = Modifier.weight(1f),
                        size = 30.dp,
                        fillWidth = true,
                    )
                    CompactIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reiniciar box ${state.id}",
                        enabled = state.canRestart,
                        onClick = onRestart,
                        modifier = Modifier.weight(1f),
                        size = 30.dp,
                        fillWidth = true,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                CompactVolumeControl(
                    volume = state.volume,
                    enabled = state.canChangeVolume,
                    onVolumeChange = onVolumeChange,
                )
            }
        }
    }
}

/** Cor de brilho/borda do card conforme o estado — nulo = sem brilho (borda neutra). */
private fun boxGlowColor(state: AudioBoxUiState): Color? = when {
    state.playbackState == AudioPlaybackState.Error -> BoxPlayCoral
    state.playbackState == AudioPlaybackState.Playing -> BoxPlayStatusGreen
    state.isLocked -> BoxPlayStatusGreen
    else -> null
}

/** Cor da forma de onda decorativa conforme o estado do Box. */
private fun boxWaveformColor(state: AudioBoxUiState): Color = when {
    state.playbackState == AudioPlaybackState.Error -> BoxPlayCoral
    state.playbackState == AudioPlaybackState.Playing -> BoxPlayStatusGreen
    state.isLocked -> BoxPlayStatusGreen
    state.playbackState == AudioPlaybackState.Paused ||
        state.playbackState == AudioPlaybackState.Unsaved -> BoxPlayWarning
    state.playbackState == AudioPlaybackState.Empty -> BoxPlaySecondaryText
    else -> BoxPlayElectricBlue
}

/**
 * Conteúdo mostrado no lugar do card normal quando o Box está além do
 * limite do plano gratuito (paywall comercial).
 */
@Composable
private fun PaywallCardContent(
    boxId: Int,
    priceText: String?,
    onUnlockClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BoxPlayWarning.copy(alpha = 0.08f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = BoxPlayWarning, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Box $boxId",
            color = BoxPlayPrimaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        Text(
            text = "Disponível na versão completa",
            color = BoxPlayWarning,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onUnlockClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BoxPlayWarning,
                contentColor = Color(0xFF1A1300),
            ),
        ) {
            Text(text = priceText ?: "Comprar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Controle de volume horizontal e compacto usado dentro do AudioBoxCard
 * (diferente do [VerticalVolumeControl], que é vertical e usado em outro
 * lugar). Passos de 10% por toque, com uma barra mostrando o nível atual.
 */
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
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = "Diminuir volume",
            enabled = enabled && clampedVolume > 0f,
            onClick = { onVolumeChange((clampedVolume - 0.10f).coerceIn(0f, 1f)) },
            size = 20.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { clampedVolume },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .semantics { contentDescription = "Volume ${(clampedVolume * 100).toInt()} por cento" },
                color = if (enabled) BoxPlayElectricBlue else BoxPlayMutedControl,
                trackColor = BoxPlayMutedControl,
            )
        }
        Text(
            text = "${(clampedVolume * 100).toInt()}%",
            color = BoxPlaySecondaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
        )
        CompactIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "Aumentar volume",
            enabled = enabled && clampedVolume < 1f,
            onClick = { onVolumeChange((clampedVolume + 0.10f).coerceIn(0f, 1f)) },
            size = 20.dp,
        )
    }
}

/**
 * Botão de ícone reutilizado por todos os controles do card. Estilo
 * "vidro": fundo translúcido com borda sutil; quando `active` (ex.:
 * tocando, travado), fica preenchido em azul; quando `dangerTint`, o ícone
 * fica coral.
 */
@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    active: Boolean = false,
    dangerTint: Boolean = false,
    fillWidth: Boolean = false,
) {
    val backgroundColor = if (active) BoxPlayElectricBlue else BoxPlaySurfaceSoft
    val contentColor = when {
        active -> Color.White
        !enabled -> BoxPlaySecondaryText.copy(alpha = 0.4f)
        dangerTint -> BoxPlayCoral
        else -> BoxPlayPrimaryText
    }
    val shape = if (fillWidth) RoundedCornerShape(8.dp) else CircleShape
    Box(
        modifier = modifier
            .height(size)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.size(size))
            .clip(shape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
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
            tint = contentColor,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}
