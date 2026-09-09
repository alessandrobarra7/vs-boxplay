package com.boxplay.multitrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxplay.multitrack.ui.screens.MultitrackEditorScreen
import com.boxplay.multitrack.ui.screens.MultitrackProjectListScreen
import com.boxplay.multitrack.viewmodel.MultitrackViewModel

/**
 * Navegação mínima do módulo multipista (especificação, seção 41): lista de
 * projetos -> editor de um projeto -> volta ao soundboard. Sem Navigation
 * Compose de propósito, para não adicionar uma dependência nova de Gradle só
 * por causa da V1 — é uma troca de estado local, suficiente para uma pilha
 * de no máximo duas telas.
 */
private sealed class MultitrackRoute {
    object ProjectList : MultitrackRoute()
    data class Editor(val projectId: String) : MultitrackRoute()
}

@Composable
fun MultitrackNavHost(onExit: () -> Unit) {
    val viewModel: MultitrackViewModel = viewModel()
    var route by remember { mutableStateOf<MultitrackRoute>(MultitrackRoute.ProjectList) }

    when (val current = route) {
        is MultitrackRoute.ProjectList -> MultitrackProjectListScreen(
            viewModel = viewModel,
            onOpenProject = { projectId -> route = MultitrackRoute.Editor(projectId) },
            onExit = onExit,
        )
        is MultitrackRoute.Editor -> MultitrackEditorScreen(
            projectId = current.projectId,
            viewModel = viewModel,
            onBack = {
                viewModel.closeCurrentProject()
                route = MultitrackRoute.ProjectList
            },
            // Depois de vincular o áudio a um Box, sai do módulo multipista
            // inteiro (não só volta pra lista de projetos) para o usuário
            // cair direto no soundboard com o Box já sincronizado.
            onExportComplete = {
                viewModel.closeCurrentProject()
                onExit()
            },
        )
    }
}
