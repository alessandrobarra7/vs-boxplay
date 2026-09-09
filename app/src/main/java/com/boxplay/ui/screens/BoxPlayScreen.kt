package com.boxplay.ui.screens

// NOVA IDENTIDADE VISUAL ("vidro azul", referência: maquete "Soundboard
// Studio" enviada pelo dono do produto). A assinatura pública de
// BoxPlayScreen (única coisa que o MainActivity chama) foi mantida
// idêntica. A árvore de composables privados foi reescrita para o novo
// visual, mas a fiação com o BoxPlayViewModel (nomes de método/estado)
// segue exatamente com.boxplay.viewmodel.BoxPlayViewModel.kt real —
// conferido arquivo a arquivo, não é suposição.
//
// Esta é a TELA PRINCIPAL do app (o "soundboard"): mostra uma lista de
// Cenas, cada uma expansível, contendo uma grade de até 40 AudioBoxCard.

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxplay.data.AudioSceneConfig
import com.boxplay.ui.components.AudioBoxCard
import com.boxplay.ui.components.DecorativeWaveform
import com.boxplay.ui.model.AudioBoxUiState
import com.boxplay.ui.model.AudioSceneUiState
import com.boxplay.ui.theme.BoxPlayBackground
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayCardBorder
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText
import com.boxplay.ui.theme.BoxPlayStatusGreen
import com.boxplay.ui.theme.BoxPlaySurfaceSoft
import com.boxplay.ui.theme.BoxPlayTheme
import com.boxplay.viewmodel.BoxPlayViewModel

/**
 * Tela principal do BoxPlay (rota raiz do app — ver MainActivity). Lê todo
 * o estado do [BoxPlayViewModel] via collectAsStateWithLifecycle.
 */
@Composable
fun BoxPlayScreen(viewModel: BoxPlayViewModel = viewModel()) {
    val sceneSections by viewModel.sceneSections.collectAsStateWithLifecycle()
    val selectedScene by viewModel.selectedScene.collectAsStateWithLifecycle()
    val canCreateScene by viewModel.canCreateScene.collectAsStateWithLifecycle()
    val unlockPriceText by viewModel.unlockPriceText.collectAsStateWithLifecycle()
    val billingError by viewModel.billingError.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pickingSceneId by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingBoxId by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedSceneIds by rememberSaveable { mutableStateOf(listOf(AudioSceneConfig.DEFAULT_SCENE_ID)) }
    var showCreateSceneDialog by remember { mutableStateOf(false) }
    var editingScene by remember { mutableStateOf<AudioSceneUiState?>(null) }
    var deletingScene by remember { mutableStateOf<AudioSceneUiState?>(null) }
    var editingBox by remember { mutableStateOf<AudioBoxUiState?>(null) }
    var deletingBox by remember { mutableStateOf<AudioBoxUiState?>(null) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val sceneId = pickingSceneId
        val boxId = pickingBoxId
        pickingSceneId = null
        pickingBoxId = null
        if (uri != null && sceneId != null && boxId != null) {
            viewModel.onAudioSelected(sceneId, boxId, uri)
        }
    }

    LaunchedEffect(key1 = selectedScene.id) {
        if (selectedScene.id !in expandedSceneIds) {
            expandedSceneIds = expandedSceneIds + selectedScene.id
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(key1 = billingError) {
        val message = billingError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onBillingErrorShown()
        }
    }
    LaunchedEffect(key1 = uiMessage) {
        val message = uiMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onUiMessageShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BoxPlayBackground)
            // A barra de status (relógio/ícones) e a barra de navegação do
            // sistema ficam por cima do conteúdo em telas edge-to-edge
            // (padrão a partir do Android 15/targetSdk 35+) — sem esse
            // respiro, o cabeçalho (VS PLAY BARRA7 / Parar tudo) ficava
            // desenhado embaixo do relógio, cortado na parte de cima.
            .systemBarsPadding(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridColumns = if (maxWidth >= 520.dp) 3 else 2
            val listState = rememberLazyListState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxPlayHeaderBar(onStopAll = viewModel::onStopAllClicked)

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = 7.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sceneSections.forEach { section ->
                            val scene = section.scene
                            val expanded = scene.id in expandedSceneIds

                            item(key = "scene-${scene.id}-header") {
                                SceneExpandableHeader(
                                    scene = scene,
                                    expanded = expanded,
                                    canDeleteScene = !scene.isLocked && sceneSections.size > 1,
                                    onToggleExpanded = {
                                        viewModel.onSceneSelected(scene.id)
                                        expandedSceneIds = if (expanded) {
                                            expandedSceneIds - scene.id
                                        } else {
                                            expandedSceneIds + scene.id
                                        }
                                    },
                                    onRenameScene = { editingScene = scene },
                                    onDeleteScene = { deletingScene = scene },
                                    onToggleSceneLock = { viewModel.onToggleSceneLockClicked(scene.id) },
                                )
                            }

                            if (expanded) {
                                if (section.boxes.isEmpty()) {
                                    item(key = "scene-${scene.id}-empty") {
                                        EmptySceneInline(scene = scene)
                                    }
                                } else {
                                    items(
                                        items = section.boxes.chunked(gridColumns),
                                        key = { row -> "scene-${scene.id}-row-${row.first().id}" },
                                    ) { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            row.forEach { boxState ->
                                                AudioBoxCard(
                                                    state = boxState,
                                                    modifier = Modifier.weight(1f),
                                                    unlockPriceText = unlockPriceText,
                                                    onPickAudio = {
                                                        pickingSceneId = boxState.sceneId
                                                        pickingBoxId = boxState.id
                                                        audioPicker.launch(arrayOf("audio/*"))
                                                    },
                                                    onSave = { viewModel.onSaveClicked(boxState.sceneId, boxState.id) },
                                                    onToggleLock = { viewModel.onLockClicked(boxState.sceneId, boxState.id) },
                                                    onTogglePlay = { viewModel.onPlayPauseClicked(boxState.sceneId, boxState.id) },
                                                    onRestart = { viewModel.onRestartClicked(boxState.sceneId, boxState.id) },
                                                    onVolumeChange = { volume -> viewModel.onVolumeChanged(boxState.sceneId, boxState.id, volume) },
                                                    onUnlockClicked = {
                                                        context.findActivity()?.let { activity -> viewModel.onUnlockClicked(activity) }
                                                    },
                                                    onEditLabel = { editingBox = boxState },
                                                    onDelete = { deletingBox = boxState },
                                                )
                                            }
                                            // Completa a última fileira quando sobrar espaço, para
                                            // os cards não esticarem além do tamanho normal.
                                            repeat(gridColumns - row.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                                item(key = "scene-${scene.id}-add-box") {
                                    NewBoxInlineButton(
                                        enabled = section.canAddBox,
                                        onClick = { viewModel.onAddBoxClicked(scene.id) },
                                    )
                                }
                            }
                        }

                        item(key = "add-scene") {
                            NewSceneInlineButton(
                                enabled = canCreateScene,
                                onClick = { showCreateSceneDialog = true },
                            )
                        }
                    }

                    ListScrollIndicator(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxSize(),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(all = 16.dp),
        )
    }

    if (showCreateSceneDialog) {
        CreateSceneDialog(
            onDismiss = { showCreateSceneDialog = false },
            onCreate = { name ->
                viewModel.onCreateScene(name)
                showCreateSceneDialog = false
            },
        )
    }
    editingScene?.let { scene ->
        EditSceneDialog(
            scene = scene,
            onDismiss = { editingScene = null },
            onSave = { name ->
                viewModel.onRenameSceneClicked(scene.id, name)
                editingScene = null
            },
        )
    }
    deletingScene?.let { scene ->
        ConfirmDeleteSceneDialog(
            scene = scene,
            onDismiss = { deletingScene = null },
            onConfirm = {
                viewModel.onDeleteSceneClicked(scene.id)
                deletingScene = null
            },
        )
    }
    editingBox?.let { boxState ->
        EditBoxLabelDialog(
            boxState = boxState,
            onDismiss = { editingBox = null },
            onSave = { label ->
                viewModel.onBoxLabelChanged(boxState.sceneId, boxState.id, label)
                editingBox = null
            },
        )
    }
    deletingBox?.let { boxState ->
        ConfirmDeleteBoxDialog(
            boxState = boxState,
            onDismiss = { deletingBox = null },
            onConfirm = {
                viewModel.onDeleteBoxClicked(boxState.sceneId, boxState.id)
                deletingBox = null
            },
        )
    }
}

/** Obtém a Activity a partir de um Context do Compose (fluxo de compra do Billing exige uma Activity). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Barra de topo: título "VSPLAY BARRA7" com identidade "vidro azul" (VS em
 * azul, PLAY claro, BARRA7 itálico) + forma de onda decorativa, e o botão
 * "Parar todos os áudios".
 */
@Composable
private fun BoxPlayHeaderBar(onStopAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BoxPlayCard.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "VS", color = BoxPlayElectricBlue, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(text = "PLAY", color = BoxPlayPrimaryText, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BARRA7",
                    color = BoxPlaySecondaryText,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                DecorativeWaveform(
                    modifier = Modifier.width(20.dp),
                    color = BoxPlayElectricBlue,
                    dimmed = false,
                    height = 14.dp,
                )
            }
            Text(
                text = "BoxPlay · Soundboard ao vivo",
                color = BoxPlaySecondaryText,
                fontSize = 9.sp,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(BoxPlayCoral)
                .clickable(onClick = onStopAll)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.StopCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(text = "Parar tudo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

/**
 * Cabeçalho clicável/expansível de uma Cena. Estilo "vidro": borda azul
 * quando expandida, verde translúcida discreta quando travada.
 */
@Composable
private fun SceneExpandableHeader(
    scene: AudioSceneUiState,
    expanded: Boolean,
    canDeleteScene: Boolean,
    onToggleExpanded: () -> Unit,
    onRenameScene: () -> Unit,
    onDeleteScene: () -> Unit,
    onToggleSceneLock: () -> Unit,
) {
    val borderColor = if (expanded) BoxPlayElectricBlue.copy(alpha = 0.55f) else BoxPlayCardBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BoxPlayCard.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = if (expanded) BoxPlayElectricBlue else BoxPlaySecondaryText,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = scene.name,
            modifier = Modifier.weight(1f),
            color = BoxPlayPrimaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRenameScene) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = "Renomear cena ${scene.name}",
                tint = BoxPlaySecondaryText,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDeleteScene, enabled = canDeleteScene) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Excluir cena ${scene.name}",
                tint = if (canDeleteScene) BoxPlayCoral else BoxPlaySecondaryText.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onToggleSceneLock) {
            Icon(
                imageVector = if (scene.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (scene.isLocked) "Destravar cena ${scene.name}" else "Travar cena ${scene.name}",
                tint = if (scene.isLocked) BoxPlayStatusGreen else BoxPlaySecondaryText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Conteúdo mostrado quando a Cena expandida ainda não tem nenhum Box. */
@Composable
private fun EmptySceneInline(scene: AudioSceneUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BoxPlaySurfaceSoft.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = BoxPlayElectricBlue.copy(alpha = 0.72f), modifier = Modifier.size(20.dp))
        Text(
            text = if (scene.isLocked) "Cena travada" else "Cena vazia",
            color = BoxPlaySecondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botão inline para adicionar um novo Box à cena atual (fim da grade). */
@Composable
private fun NewBoxInlineButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) BoxPlayElectricBlue.copy(alpha = 0.10f) else BoxPlaySurfaceSoft.copy(alpha = 0.4f))
            .border(BorderStroke(1.dp, BoxPlayCardBorder), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = if (enabled) BoxPlayElectricBlue else BoxPlaySecondaryText, modifier = Modifier.size(18.dp))
        Text(
            text = if (enabled) "Novo box" else "Limite de boxes atingido",
            color = if (enabled) BoxPlayPrimaryText else BoxPlaySecondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Botão inline para criar uma nova Cena (fim da lista de cenas). */
@Composable
private fun NewSceneInlineButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .border(BorderStroke(1.dp, BoxPlayCardBorder), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = if (enabled) BoxPlayElectricBlue else BoxPlaySecondaryText, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (enabled) "Nova cena" else "Limite de cenas atingido",
            color = if (enabled) BoxPlayPrimaryText else BoxPlaySecondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BoxPlayPrimaryText,
    unfocusedTextColor = BoxPlayPrimaryText,
    focusedBorderColor = BoxPlayElectricBlue,
    unfocusedBorderColor = BoxPlayCardBorder,
    cursorColor = BoxPlayElectricBlue,
    focusedLabelColor = BoxPlayElectricBlue,
    unfocusedLabelColor = BoxPlaySecondaryText,
)

/** Diálogo para criar uma nova Cena. */
@Composable
private fun CreateSceneDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val canSubmit = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoxPlayCard,
        title = { Text("Nova cena", color = BoxPlayPrimaryText) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nome") },
                colors = darkTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = canSubmit) {
                Text("Criar", color = if (canSubmit) BoxPlayElectricBlue else BoxPlaySecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BoxPlayPrimaryText) } },
    )
}

/** Diálogo para renomear uma Cena existente. */
@Composable
private fun EditSceneDialog(scene: AudioSceneUiState, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(scene.name) }
    val canSubmit = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoxPlayCard,
        title = { Text("Renomear cena", color = BoxPlayPrimaryText) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nome") },
                colors = darkTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = canSubmit) {
                Text("Salvar", color = if (canSubmit) BoxPlayElectricBlue else BoxPlaySecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BoxPlayPrimaryText) } },
    )
}

/** Diálogo de confirmação para excluir uma Cena. */
@Composable
private fun ConfirmDeleteSceneDialog(scene: AudioSceneUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoxPlayCard,
        title = { Text("Excluir cena?", color = BoxPlayPrimaryText) },
        text = { Text("A cena \"${scene.name}\" e todos os seus boxes serão excluídos.", color = BoxPlaySecondaryText) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Excluir", color = BoxPlayCoral, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BoxPlayPrimaryText) } },
    )
}

/** Diálogo para editar a legenda customizada de um Box. */
@Composable
private fun EditBoxLabelDialog(boxState: AudioBoxUiState, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf(boxState.customLabel.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoxPlayCard,
        title = { Text("Legenda do box ${boxState.id}", color = BoxPlayPrimaryText) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                label = { Text("Legenda") },
                colors = darkTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(label) }) {
                Text("Salvar", color = BoxPlayElectricBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BoxPlayPrimaryText) } },
    )
}

/** Diálogo de confirmação para excluir um Box. */
@Composable
private fun ConfirmDeleteBoxDialog(boxState: AudioBoxUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoxPlayCard,
        title = { Text("Excluir box ${boxState.id}?", color = BoxPlayPrimaryText) },
        text = { Text("O áudio salvo em \"${boxState.displayName}\" será removido.", color = BoxPlaySecondaryText) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Excluir", color = BoxPlayCoral, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BoxPlayPrimaryText) } },
    )
}

/** Indicador de rolagem vertical customizado, fino, do lado direito da LazyColumn. */
@Composable
private fun ListScrollIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleItems >= totalItems) return

    val scrollProgress = if (totalItems > visibleItems) {
        listState.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems).toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.width(4.dp)) {
        val maxHeight = this.maxHeight
        val thumbHeight = (maxHeight * (visibleItems.toFloat() / totalItems.toFloat()))
            .coerceAtLeast(36.dp)
            .coerceAtMost(maxHeight)
        val maxOffset = maxHeight - thumbHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(BoxPlayMutedControl.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .padding(top = maxOffset * scrollProgress)
                .height(thumbHeight)
                .width(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BoxPlayElectricBlue.copy(alpha = 0.85f)),
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 920)
@Composable
private fun BoxPlayScreenPhonePreview() {
    BoxPlayTheme {
        BoxPlayScreen()
    }
}
