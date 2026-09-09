package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.multitrack.model.TrackRouting
import com.boxplay.multitrack.ui.model.MultitrackTrackUiState
import com.boxplay.multitrack.ui.model.TrackFileStatus
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlayWarning

/**
 * Card de pista, recolhido ou expandido (especificação, seções 11/12).
 * Nome, L/C/R, mute e volume ficam sempre visíveis; a posição detalhada só
 * aparece quando [MultitrackTrackUiState.expanded] é true.
 */
@Composable
fun MultitrackTrackCard(
    track: MultitrackTrackUiState,
    onToggleExpanded: () -> Unit,
    onRoutingChange: (TrackRouting) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onToggleMute: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BoxPlayCard)
            .then(if (track.expanded) Modifier.border(1.dp, BoxPlayElectricBlue, shape) else Modifier)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = track.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BoxPlayPrimaryText,
                )
                if (track.fileStatus != TrackFileStatus.READY) {
                    Text(
                        text = if (track.fileStatus == TrackFileStatus.MISSING) {
                            "Arquivo não encontrado"
                        } else {
                            "Arquivo inválido"
                        },
                        fontSize = 9.sp,
                        color = BoxPlayWarning,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackRoutingSelector(selected = track.routing, onSelect = onRoutingChange)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (track.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (track.expanded) "Recolher pista" else "Expandir pista",
                    tint = BoxPlayElectricBlue,
                    modifier = Modifier.clickable(onClick = onToggleExpanded),
                )
            }
        }

        Spacer(modifier = Modifier.height(9.dp))

        TrackVolumeControl(
            volumePercent = track.volumePercent,
            muted = track.muted,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            onToggleMute = onToggleMute,
        )

        if (track.expanded) {
            Spacer(modifier = Modifier.height(9.dp))
            TrackPositionControl(
                offsetLabel = track.offsetLabel,
                durationLabel = track.durationLabel,
                onStepBack = onStepBack,
                onStepForward = onStepForward,
                onReset = onResetOffset,
            )
        }
    }
}
