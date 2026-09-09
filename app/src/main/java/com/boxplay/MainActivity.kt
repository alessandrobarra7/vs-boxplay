package com.boxplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.multitrack.ui.navigation.MultitrackNavHost
import com.boxplay.ui.screens.BoxPlayScreen
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BoxPlayTheme {
                BoxPlayBarra7App()
            }
        }
    }
}

/**
 * Alterna entre o soundboard (BoxPlayScreen, inalterado) e o editor
 * multipista. Troca de estado local em vez de Navigation Compose (ver
 * com.boxplay.multitrack.ui.navigation.MultitrackNavHost) — o soundboard
 * continua sendo a tela padrão e nada aqui muda o comportamento dele
 * (especificação, seções 1 e 41/42).
 */
@Composable
private fun BoxPlayBarra7App() {
    var showMultitrack by remember { mutableStateOf(false) }

    if (showMultitrack) {
        MultitrackNavHost(onExit = { showMultitrack = false })
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxPlayScreen()
            MultitrackDockButton(
                onClick = { showMultitrack = true },
                // navigationBarsPadding() antes do padding visual: sem isso a
                // barra de navegação do sistema (gestos ou os 3 botões) ficava
                // desenhada por cima do botão, cortando o texto/ícone.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

/**
 * Botão flutuante que abre o Editor Multipista — estilo "dock" da nova
 * identidade visual (pílula azul com um mini-equalizador e duas linhas de
 * texto), em vez do FloatingActionButton padrão anterior. Mesma ação
 * (abre showMultitrack = true), só o visual mudou.
 */
@Composable
private fun MultitrackDockButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .shadow(elevation = 12.dp, shape = shape, ambientColor = BoxPlayElectricBlue, spotColor = BoxPlayElectricBlue)
            .clip(shape)
            .background(BoxPlayElectricBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column {
            Text(
                text = "ABRIR MÓDULO",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Editor multipista",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
