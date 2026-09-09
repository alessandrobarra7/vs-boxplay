package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.multitrack.model.TrackRouting
import com.boxplay.ui.theme.BoxPlayElectricBlue

/** Seletor L/C/R de uma pista (especificação, seção 7). */
@Composable
fun TrackRoutingSelector(
    selected: TrackRouting,
    onSelect: (TrackRouting) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        TrackRouting.values().forEachIndexed { index, routing ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(4.dp))
            }
            RoutingButton(
                label = routing.shortLabel(),
                active = routing == selected,
                onClick = { onSelect(routing) },
            )
        }
    }
}

@Composable
private fun RoutingButton(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color(0xFF06182F) else BoxPlayElectricBlue,
        modifier = Modifier
            .clip(shape)
            .background(if (active) BoxPlayElectricBlue else Color.Transparent)
            .then(if (active) Modifier else Modifier.border(1.dp, BoxPlayElectricBlue, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun TrackRouting.shortLabel(): String = when (this) {
    TrackRouting.LEFT -> "L"
    TrackRouting.CENTER -> "C"
    TrackRouting.RIGHT -> "R"
}
