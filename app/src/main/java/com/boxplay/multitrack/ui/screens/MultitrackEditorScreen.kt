package com.boxplay.multitrack.ui.screens

// NOTA DESTE PACOTE DE HANDOFF: o cache local usado para montar este pacote
// era de uma versão anterior à reformulação final do botão "Exportar" (o
// pedido do usuário foi: clicar em Exportar abre primeiro o seletor nativo
// de PASTA do Android — com opção de criar pasta, no aparelho ou no Drive —
// e só depois de escolhida a pasta é que aparece o diálogo perguntando em
// qual Cena/Box sincronizar; além disso, tanto a barra de importação quanto
// a de exportação passaram a mostrar PORCENTAGEM real, não só um spinner
// indeterminado). As partes afetadas abaixo (o launcher de pasta, o
// `onRenderClick`, e as duas funções de banner) foram atualizadas para
// refletir esse comportamento final, coerente com o que foi testado e
// entregue no APK `vsplay-barra7-editor-multipista-mixagem-otimizada-debug.apk`.
// O restante do arquivo (cabeçalho, lista de pistas, transporte, diálogo de
// Cena/Box em si) foi conferido como já correspondente à versão atual.

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.data.AudioSceneState
import com.boxplay.multitrack.model.MultitrackTrack
import com.boxplay.multitrack.ui.components.MultitrackTrackCard
import com.boxplay.multitrack.ui.components.MultitrackTransportBar
import com.boxplay.multitrack.ui.model.ProjectSaveStatus
import com.boxplay.multitrack.ui.model.ZipImportUiState
import com.boxplay.multitrack.viewmodel.MultitrackViewModel
import com.boxplay.ui.theme.BoxPlayBackground
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText
import com.boxplay.ui.theme.BoxPlayStatusGreen
import com.boxplay.ui.theme.BoxPlayWarning

/**
 * Editor de um projeto multipista (especificação, seções 10-13). Os
 * controles de cada pista funcionam e autosalvam; "Tocar" liga a
 * pré-escuta simultânea das pistas (com volume ajustável ao vivo) e
 * "Exportar para Box" pergunta a Cena/Box de destino, mixa as pistas num
 * único áudio e salva no Box escolhido.
 */
@Composable
fun MultitrackEditorScreen(
    projectId: String,
    viewModel: MultitrackViewModel,
    onBack: () -> Unit,
    // Chamado quando o áudio mixado é vinculado a um Box com sucesso —
    // volta direto para o soundboard (em vez de deixar o usuário na tela do
    // editor, precisando voltar duas vezes manualmente) para que o Box já
    // sincronizado apareça imediatamente. Correção de bug relatado pelo
    // usuário: "após o multipista terminar o processamento e escolher o
    // box, já deve aparecer o box com o áudio sincronizado".
    onExportComplete: () -> Unit = {},
) {
    LaunchedEffect(projectId) {
        viewModel.openProject(projectId)
    }

    val state by viewModel.editorUiState.collectAsState()
    val zipImportState by viewModel.zipImportState.collectAsState()
    val isPreviewing by viewModel.isPreviewing.collectAsState()
    val sceneState by viewModel.sceneState.collectAsState()
    val exportDialogVisible by viewModel.exportDialogVisible.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    // Um único seletor para tudo: o usuário escolhe um áudio avulso OU um
    // pacote .zip/.rar de stems na mesma tela de arquivos, sem precisar
    // saber de antemão qual botão usar nem selecionar nada dentro do
    // pacote — o ViewModel decide sozinho, a partir do arquivo escolhido,
    // se importa uma pista ou descompacta o pacote inteiro
    // automaticamente.
    //
    // O filtro é "*/*" (tudo) de propósito: provedores de arquivo/nuvem
    // (Google Drive em especial) reportam o tipo MIME de .zip/.rar de um
    // jeito muito inconsistente — o mesmo .rar pode aparecer como
    // "application/x-rar-compressed", "application/octet-stream" ou algo
    // totalmente diferente dependendo do provedor — e quando a lista de
    // tipos aceitos não bate com o que o Drive reportou, o arquivo aparece
    // acinzentado e não pode nem ser selecionado (era exatamente isso que
    // estava acontecendo com os .rar). Aceitando qualquer tipo aqui, todo
    // arquivo fica sempre selecionável, e é o ViewModel quem decide de
    // verdade o que fazer com ele a partir do nome/extensão real.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.addTrackOrZipFromUri(it) }
    }

    // Seletor nativo de PASTA (SAF, permite criar pasta no próprio diálogo,
    // tanto no armazenamento do aparelho quanto no Google Drive) disparado
    // pelo botão "Exportar" — ver onRenderClick logo abaixo. Só depois que
    // o usuário escolhe (ou cria) a pasta é que a mixagem começa; o
    // resultado dispara viewModel.beginExport(treeUri), que mixa, salva o
    // áudio final ali e só então libera o diálogo de escolha de Cena/Box.
    val exportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        treeUri?.let { viewModel.beginExport(it) }
    }
    val exportProgressPercent by viewModel.exportProgressPercent.collectAsState()

    // O seletor de pasta abaixo é uma tela do PRÓPRIO SISTEMA Android (SAF/
    // DocumentsUI ou o app do Google Drive) — o app não tem como desenhar
    // um botão dentro dela. O usuário relatou dificuldade para voltar ao
    // app a partir de lá; como mitigação, este passo intermediário (dentro
    // do próprio BoxPlay) aparece ANTES do seletor nativo abrir, com um
    // "Cancelar" claro para quem mudou de ideia sem precisar entrar no
    // seletor do sistema. Uma vez lá dentro, o caminho de volta continua
    // sendo o botão/gesto de voltar do Android — fora do alcance do app.
    var showFolderPickerHint by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoxPlayBackground)
            // A barra de navegação do sistema (gestos/botões) fica por cima
            // do conteúdo em telas que vão até a borda inferior — sem esse
            // respiro, o botão de importar e a barra de transporte (seções
            // 26/31) acabavam desenhados sob a barra do sistema, dando a
            // impressão de botões "sobrepostos"/cortados na parte de baixo.
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = BoxPlayPrimaryText)
            }
            Column {
                Text(
                    text = "Editor multipista",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = BoxPlayPrimaryText,
                )
                Text(
                    text = state?.name.orEmpty(),
                    fontSize = 12.sp,
                    color = BoxPlaySecondaryText,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val editorState = state
        if (editorState == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Carregando projeto...", color = BoxPlaySecondaryText)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Duração: ${editorState.durationLabel}",
                    fontSize = 11.sp,
                    color = BoxPlaySecondaryText,
                )
                Text(
                    text = editorState.saveStatus.label(),
                    fontSize = 11.sp,
                    color = if (editorState.saveStatus == ProjectSaveStatus.ERRO_AO_SALVAR) {
                        BoxPlayCoral
                    } else {
                        BoxPlayStatusGreen
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ZipImportBanner(state = zipImportState, onDismiss = { viewModel.dismissZipImportStatus() })
            ExportMessageBanner(
                message = exportMessage,
                isExporting = isExporting,
                percent = exportProgressPercent,
                onDismiss = { viewModel.dismissExportMessage() },
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(editorState.tracks, key = { it.id }) { track ->
                    MultitrackTrackCard(
                        track = track,
                        onToggleExpanded = { viewModel.toggleTrackExpanded(track.id) },
                        onRoutingChange = { routing -> viewModel.setTrackRouting(track.id, routing) },
                        onVolumeChange = { volume -> viewModel.setTrackVolume(track.id, volume) },
                        onVolumeChangeFinished = {},
                        onToggleMute = { viewModel.setTrackMuted(track.id, !track.muted) },
                        onStepBack = {
                            viewModel.shiftTrackOffset(track.id, -MultitrackTrack.FINE_OFFSET_STEP_US)
                        },
                        onStepForward = {
                            viewModel.shiftTrackOffset(track.id, MultitrackTrack.FINE_OFFSET_STEP_US)
                        },
                        onResetOffset = { viewModel.zeroTrackOffset(track.id) },
                    )
                }
                item {
                    Text(
                        text = "+ Adicionar pista (áudio, .zip ou .rar)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BoxPlayCoral,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = zipImportState !is ZipImportUiState.Importing) {
                                importLauncher.launch(arrayOf("*/*"))
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MultitrackTransportBar(
                previewEnabled = editorState.tracks.isNotEmpty(),
                renderEnabled = editorState.tracks.isNotEmpty() && !isExporting,
                isPreviewing = isPreviewing,
                onPreviewClick = { viewModel.togglePreview() },
                // Antes de abrir o seletor nativo de pasta, mostra um passo
                // do próprio app com opção de cancelar (ver showFolderPickerHint
                // acima) — só ao confirmar é que o seletor do Android/Drive
                // abre de verdade.
                onRenderClick = { showFolderPickerHint = true },
            )
        }

        if (showFolderPickerHint) {
            SelectFolderHintDialog(
                onConfirm = {
                    showFolderPickerHint = false
                    exportFolderLauncher.launch(null)
                },
                onDismiss = { showFolderPickerHint = false },
            )
        }

        if (exportDialogVisible) {
            ExportToBoxDialog(
                sceneState = sceneState,
                onDismiss = { viewModel.dismissExportDialog() },
                onConfirm = { sceneId, boxId, sceneName ->
                    viewModel.confirmExportDestination(sceneId, boxId, sceneName)
                    onExportComplete()
                },
            )
        }
    }
}

private fun ProjectSaveStatus.label(): String = when (this) {
    ProjectSaveStatus.SALVO -> "Salvo"
    ProjectSaveStatus.SALVANDO -> "Salvando..."
    ProjectSaveStatus.ERRO_AO_SALVAR -> "Erro ao salvar"
}

/**
 * Aviso de progresso/resultado da importação de um pacote .zip ou .rar
 * (especificação: importação de stems em lote). Nada aparece em
 * [ZipImportUiState.Idle]; nos demais estados, um toque dispensa o aviso
 * (exceto durante [ZipImportUiState.Importing], que não pode ser
 * cancelado nesta fase).
 */
@Composable
private fun ZipImportBanner(state: ZipImportUiState, onDismiss: () -> Unit) {
    val (message, isError) = when (state) {
        ZipImportUiState.Idle -> return
        is ZipImportUiState.Importing -> {
            val percentSuffix = state.percent?.let { " $it%" }.orEmpty()
            "Importando pacote...$percentSuffix" to false
        }
        is ZipImportUiState.Done -> {
            val skippedSuffix = if (state.skipped > 0) " (${state.skipped} arquivo(s) ignorado(s))" else ""
            "${state.imported} pista(s) importada(s) do pacote$skippedSuffix" to false
        }
        is ZipImportUiState.Failed -> state.message to true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BoxPlayCard)
            .then(
                if (state is ZipImportUiState.Importing) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onDismiss)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                fontSize = 11.sp,
                color = if (isError) BoxPlayWarning else BoxPlaySecondaryText,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state !is ZipImportUiState.Importing) {
                Text(text = "Ok", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BoxPlayCoral)
            }
        }

        // Barra de progresso COM PORCENTAGEM real (pedido do usuário: "uma
        // barra de carregamento com a inclusão de porcentagem", tanto para
        // importar quanto para exportar) — quando o percentual ainda não
        // chegou (ex.: primeiro instante), cai para o modo indeterminado
        // (progress = null) em vez de travar em 0%.
        if (state is ZipImportUiState.Importing) {
            Spacer(modifier = Modifier.height(8.dp))
            val percent = state.percent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BoxPlayCoral,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BoxPlayCoral,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * Aviso sobre a mixagem/vínculo de Cena/Box do botão "Exportar para Box"
 * (ver [MultitrackViewModel.confirmExportDestination]). Some sozinho
 * (`message == null`). Mixar pode levar um tempo perceptível (decodificar
 * cada pista, somar e codificar de novo), então mostra uma barra de
 * progresso enquanto [isExporting] — mesma ideia do aviso de importação de
 * pacote, para deixar claro que não travou. Não pode ser dispensado
 * durante a mixagem.
 */
@Composable
private fun ExportMessageBanner(
    message: String?,
    isExporting: Boolean,
    percent: Int?,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BoxPlayCard)
            .then(if (isExporting) Modifier else Modifier.clickable(onClick = onDismiss))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                fontSize = 11.sp,
                color = BoxPlaySecondaryText,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!isExporting) {
                Text(text = "Ok", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BoxPlayCoral)
            }
        }

        if (isExporting) {
            Spacer(modifier = Modifier.height(8.dp))
            // Mesma lógica de "percentual real, com fallback indeterminado"
            // do ZipImportBanner acima — cobre tanto a fase de mixagem
            // (percent vindo de MultitrackMixdownEngine.render) quanto o
            // instante de "Salvando na pasta escolhida..." (percent = null).
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BoxPlayCoral,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BoxPlayCoral,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * Seletor de Cena e Box do botão "Exportar para Box": o usuário escolhe a
 * Cena e, dentro dela, o Box de destino — pedido explícito do usuário
 * ("o app pergunta a cena e o box que ele precisa ir"). Tocar num Box já
 * confirma a escolha.
 */
/**
 * Passo intermediário, dentro do próprio app, antes de abrir o seletor de
 * pasta do sistema (Android/Drive). O seletor nativo é uma tela de outro
 * app (DocumentsUI/Drive) que o BoxPlay não controla e onde não é possível
 * colocar um botão "voltar ao app" — este diálogo garante que, se o
 * usuário mudar de ideia, "Cancelar" volta para o editor imediatamente,
 * sem precisar navegar pelo seletor do sistema.
 */
@Composable
private fun SelectFolderHintDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Escolher pasta de destino", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Text(
                text = "Agora vai abrir o seletor de pastas do Android (ou do Google Drive) para " +
                    "você escolher ou criar a pasta onde o áudio mixado será salvo. Para voltar ao " +
                    "BoxPlay de lá, use o botão/gesto de voltar do seu aparelho.",
                fontSize = 13.sp,
                color = BoxPlaySecondaryText,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Escolher pasta", color = BoxPlayElectricBlue, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = BoxPlaySecondaryText)
            }
        },
    )
}

@Composable
private fun ExportToBoxDialog(
    sceneState: AudioSceneState,
    onDismiss: () -> Unit,
    onConfirm: (sceneId: Int, boxId: Int, sceneName: String) -> Unit,
) {
    var selectedSceneId by remember(sceneState.selectedSceneId) { mutableStateOf(sceneState.selectedSceneId) }
    val selectedScene = sceneState.scenes.find { it.id == selectedSceneId } ?: sceneState.scenes.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Exportar para Box", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text(text = "Cena", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BoxPlaySecondaryText)
                Spacer(modifier = Modifier.height(4.dp))
                sceneState.scenes.forEach { scene ->
                    val isSelected = scene.id == selectedScene.id
                    Text(
                        text = scene.name,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) BoxPlayCoral else BoxPlayPrimaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSceneId = scene.id }
                            .padding(vertical = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Box", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BoxPlaySecondaryText)
                Spacer(modifier = Modifier.height(4.dp))

                if (selectedScene.boxes.isEmpty()) {
                    Text(
                        text = "Essa cena ainda não tem nenhum Box.",
                        fontSize = 12.sp,
                        color = BoxPlaySecondaryText,
                    )
                } else {
                    selectedScene.boxes.forEach { box ->
                        Text(
                            text = box.customLabel ?: box.displayName,
                            fontSize = 13.sp,
                            color = BoxPlayPrimaryText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(selectedScene.id, box.id, selectedScene.name) }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                text = "Cancelar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BoxPlaySecondaryText,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
            )
        },
    )
}
