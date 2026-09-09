package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.multitrack.model.MultitrackTrack
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText

/**
 * Controle de posição exibido apenas quando a pista está expandida
 * (especificação, seção 6): ajuste fino de +/-10ms, ZERAR, e a duração
 * completa da pista.
 */
@Composable
fun TrackPositionControl(
    offsetLabel: String,
    durationLabel: String,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepMs = MultitrackTrack.FINE_OFFSET_STEP_US / 1000L

    Column(modifier = modifier) {
        Text(
            text = "POSIÇÃO",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = BoxPlaySecondaryText,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionAction(label = "-${stepMs}ms", onClick = onStepBack)
            Text(text = offsetLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BoxPlayPrimaryText)
            PositionAction(label = "+${stepMs}ms", onClick = onStepForward)
            PositionAction(label = "ZERAR", onClick = onReset)
        }
        Text(
            text = "Duração: $durationLabel",
            fontSize = 10.sp,
            color = BoxPlaySecondaryText,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PositionAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = BoxPlayElectricBlue,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
