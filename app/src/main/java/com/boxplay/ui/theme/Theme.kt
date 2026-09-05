package com.boxplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BoxPlayHeader,
    onPrimary = BoxPlayPrimaryText,
    secondary = BoxPlayElectricBlue,
    onSecondary = Color.White,
    tertiary = BoxPlayCoral,
    background = BoxPlayBackground,
    onBackground = BoxPlayHeader,
    surface = BoxPlayCard,
    onSurface = BoxPlayPrimaryText,
)

@Composable
fun BoxPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = BoxPlayTypography,
        content = content,
    )
}
