package com.boxplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A nova identidade é escura por natureza ("vidro azul" sobre fundo quase
// preto), então o esquema de cores do Material3 passou de claro para
// escuro. Isso também escurece automaticamente componentes padrão que a UI
// usa sem estilização própria (AlertDialog, TextButton etc. nas telas do
// soundboard e do editor multipista), deixando tudo consistente.
private val BoxPlayColors = darkColorScheme(
    primary = BoxPlayElectricBlue,
    onPrimary = Color.White,
    secondary = BoxPlayElectricBlue,
    onSecondary = Color.White,
    tertiary = BoxPlayCoral,
    onTertiary = Color.White,
    background = BoxPlayBackground,
    onBackground = BoxPlayPrimaryText,
    surface = BoxPlayCard,
    onSurface = BoxPlayPrimaryText,
    surfaceVariant = BoxPlaySurfaceSoft,
    onSurfaceVariant = BoxPlaySecondaryText,
    error = BoxPlayCoral,
    onError = Color.White,
    outline = BoxPlayCardBorder,
)

@Composable
fun BoxPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BoxPlayColors,
        typography = BoxPlayTypography,
        content = content,
    )
}
