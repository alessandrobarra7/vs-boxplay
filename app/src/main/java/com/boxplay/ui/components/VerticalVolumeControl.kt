package com.boxplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlaySecondaryText

@Composable
fun VerticalVolumeControl(
    volume: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val clampedVolume = volume.coerceIn(0f, 1f)
    val icon = if (clampedVolume == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    val iconDescription = "Volume ${(clampedVolume * 100).toInt()} por cento"

    Box(
        modifier = modifier
            .width(42.dp)
            .height(164.dp)
            .semantics {
                contentDescription = iconDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clampedVolume, 0f..1f)
                if (!enabled) {
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .height(132.dp)
                .width(28.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center,
        ) {
            val thumbSize = 20.dp
            val travel = maxHeight - thumbSize
            val accent = if (enabled) BoxPlayElectricBlue else BoxPlayMutedControl
            val track = BoxPlayMutedControl.copy(alpha = if (enabled) 0.62f else 0.36f)

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(maxHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(track),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(8.dp)
                    .height(maxHeight * clampedVolume)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = travel * (1f - clampedVolume))
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else BoxPlaySecondaryText.copy(alpha = 0.64f)),
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White else BoxPlaySecondaryText.copy(alpha = 0.58f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(22.dp),
        )
    }
}
