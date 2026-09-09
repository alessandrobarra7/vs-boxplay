package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayCardBorder
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayPrimaryText

/**
 * Barra fixa com Tocar e Exportar para Box (especificação, seção 80).
 *
 * "Tocar" liga o motor de pré-escuta ([com.boxplay.multitrack.audio.MultitrackPreviewEngine]):
 * toca todas as pistas do projeto ao mesmo tempo e permite ajustar o volume
 * de cada uma com a reprodução em andamento. "Exportar para Box" pergunta
 * para qual Cena/Box o projeto deve ir; a mixagem final das pistas num
 * único áudio (Fase 5) ainda está para ser implementada, então por
 * enquanto essa ação só vincula o projeto ao destino escolhido.
 */
@Composable
fun MultitrackTransportBar(
    previewEnabled: Boolean,
    renderEnabled: Boolean,
    isPreviewing: Boolean,
    onPreviewClick: () -> Unit,
    onRenderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        TransportButton(
            label = if (isPreviewing) "Pausar" else "Tocar",
            enabled = previewEnabled,
            background = BoxPlayCard,
            contentColor = BoxPlayPrimaryText,
            border = BorderStroke(1.dp, BoxPlayCardBorder),
            onClick = onPreviewClick,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        TransportButton(
            label = "Exportar para Box",
            enabled = renderEnabled,
            background = BoxPlayCoral,
            contentColor = Color.White,
            onClick = onRenderClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TransportButton(
    label: String,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = contentColor.copy(alpha = alpha),
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(background.copy(alpha = alpha))
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
