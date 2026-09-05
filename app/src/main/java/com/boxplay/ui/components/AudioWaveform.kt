package com.boxplay.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DecorativeBars = listOf(
    0.22f, 0.36f, 0.28f, 0.48f, 0.31f, 0.62f, 0.44f, 0.72f,
    0.38f, 0.52f, 0.30f, 0.45f, 0.80f, 0.58f, 0.92f, 0.64f,
    0.46f, 0.69f, 0.33f, 0.41f, 0.28f, 0.24f, 0.20f, 0.18f,
)

@Composable
fun DecorativeWaveform(
    modifier: Modifier = Modifier,
    color: Color,
    dimmed: Boolean,
    height: Dp = 20.dp,
) {
    val alpha = if (dimmed) 0.30f else 0.88f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val centerY = size.height / 2f
        val step = size.width / DecorativeBars.size
        val strokeWidth = 2.dp.toPx()

        DecorativeBars.forEachIndexed { index, amplitude ->
            val x = step * index + step / 2f
            val halfHeight = (size.height * amplitude * 0.44f).coerceAtLeast(2.dp.toPx())
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
