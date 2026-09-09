package com.boxplay.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val DecorativeBars = listOf(
    0.22f, 0.36f, 0.28f, 0.48f, 0.31f, 0.62f, 0.44f, 0.72f,
    0.38f, 0.52f, 0.30f, 0.45f, 0.80f, 0.58f, 0.92f, 0.64f,
    0.46f, 0.69f, 0.33f, 0.41f, 0.28f, 0.24f, 0.20f, 0.18f,
)

/**
 * Forma de onda decorativa usada em cada AudioBoxCard e no editor
 * multipista. Identidade "vidro azul": quando `dimmed = false` (Box
 * tocando/ativo) as barras pulsam suavemente com defasagem entre elas,
 * imitando um equalizador ao vivo — o mesmo efeito "signal-pulse" da
 * referência visual — sem precisar de nenhum ícone extra. Assinatura
 * mantida idêntica à versão anterior para não quebrar nenhum call site.
 */
@Composable
fun DecorativeWaveform(
    modifier: Modifier = Modifier,
    color: Color,
    dimmed: Boolean,
    height: Dp = 20.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform-pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "waveform-phase",
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveform-glow",
    )
    val alpha = if (dimmed) 0.28f else 0.92f * glow

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
            val pulseScale = if (dimmed) 1f else (0.65f + 0.35f * sin(phase + index * 0.5f).coerceIn(-1f, 1f))
            val halfHeight = (size.height * amplitude * 0.44f * pulseScale).coerceAtLeast(2.dp.toPx())
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, y = centerY - halfHeight),
                end = Offset(x, y = centerY + halfHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
