package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText

/**
 * Chip de mute + slider de volume + porcentagem (especificação, seções 8/9).
 * O volume armazenado nunca é zerado pelo mute — o chip só alterna [muted],
 * o slider continua mostrando/editando o valor real de [volumePercent].
 */
@Composable
fun TrackVolumeControl(
    volumePercent: Int,
    muted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MuteChip(muted = muted, onClick = onToggleMute)
        Spacer(modifier = Modifier.width(9.dp))
        Slider(
            value = (volumePercent / 100f).coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = BoxPlayElectricBlue,
                activeTrackColor = BoxPlayElectricBlue,
                inactiveTrackColor = BoxPlayMutedControl,
            ),
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = "$volumePercent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (muted) BoxPlaySecondaryText else BoxPlayPrimaryText,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}

@Composable
private fun MuteChip(muted: Boolean, onClick: () -> Unit) {
    Text(
        text = "mute",
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = if (muted) BoxPlayPrimaryText else Color(0xFF06182F),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (muted) BoxPlayCoral else Color(0xFF639922))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
