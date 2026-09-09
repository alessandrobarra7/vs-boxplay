package com.boxplay.ui.theme

import androidx.compose.ui.graphics.Color

// Nova identidade visual "vidro azul" do BoxPlay (VSPLAY BARRA7), baseada na
// referência "Soundboard Studio" enviada pelo dono do produto: fundo quase
// preto com gradiente azul, cards translúcidos com brilho ciano, verde para
// "tocando"/estados protegidos, dourado só para o paywall, coral para ações
// críticas. Os NOMES abaixo foram mantidos iguais aos da paleta anterior de
// propósito — vários arquivos (AudioBoxCard, MultitrackEditorScreen, cards
// do editor multipista etc.) já importam com.boxplay.ui.theme.* e referenciam
// esses tokens pelo nome; só os valores de cor mudaram.
val BoxPlayBackground = Color(0xFF0A1526)
val BoxPlayHeader = Color(0xFF0A1526)
val BoxPlayCard = Color(0xFF16273F)
val BoxPlayCardBorder = Color(0x5C4FC3F7)
val BoxPlayPrimaryText = Color(0xFFF5F8FC)
val BoxPlaySecondaryText = Color(0xFFA9BAD1)
val BoxPlayElectricBlue = Color(0xFF3D9BFF)
val BoxPlayCoral = Color(0xFFFF5A5A)
val BoxPlayMutedControl = Color(0xFF1E3350)
val BoxPlayStatusGreen = Color(0xFF4ADE97)
val BoxPlayWarning = Color(0xFFFFC857)

// Tokens novos (aditivos — nada os referenciava antes, então não quebram
// nenhum arquivo existente) usados pelos "vidros"/superfícies da nova UI.
val BoxPlaySurfaceSoft = Color(0xFF122036)
val BoxPlaySurfaceRaised = Color(0xFF1B2E4A)
val BoxPlayGlassHighlight = Color(0x1FFFFFFF)
