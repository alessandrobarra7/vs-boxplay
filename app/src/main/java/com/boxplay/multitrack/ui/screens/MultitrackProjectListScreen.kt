package com.boxplay.multitrack.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.multitrack.ui.model.MultitrackProjectSummaryUiState
import com.boxplay.multitrack.viewmodel.MultitrackViewModel
import com.boxplay.multitrack.ui.components.MultitrackActionButton
import com.boxplay.ui.theme.BoxPlayBackground
import com.boxplay.ui.theme.BoxPlayCard
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText

/** Lista de projetos do editor multipista (especificação, seção 87/88). */
@Composable
fun MultitrackProjectListScreen(
    viewModel: MultitrackViewModel,
    onOpenProject: (String) -> Unit,
    onExit: () -> Unit,
) {
    val projects by viewModel.projectSummaries.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoxPlayBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar ao soundboard", tint = BoxPlayPrimaryText)
                }
                Text(
                    text = "Editor multipista",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = BoxPlayPrimaryText,
                )
            }
            MultitrackActionButton(
                label = "Novo projeto",
                icon = Icons.Rounded.Add,
                onClick = { showCreateDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum projeto ainda. Toque em \"+ Novo projeto\" para começar.",
                    color = BoxPlaySecondaryText,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(projects, key = { it.projectId }) { summary ->
                    ProjectSummaryCard(
                        summary = summary,
                        onClick = { onOpenProject(summary.projectId) },
                        onDelete = { viewModel.deleteProject(summary.projectId) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onConfirm = { name ->
                showCreateDialog = false
                val projectId = viewModel.createProject(name)
                onOpenProject(projectId)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun ProjectSummaryCard(
    summary: MultitrackProjectSummaryUiState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BoxPlayCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = summary.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BoxPlayPrimaryText)
            Text(
                text = "${summary.trackCount} pista(s) · ${summary.updatedAtLabel}",
                fontSize = 11.sp,
                color = BoxPlaySecondaryText,
            )
            summary.lastDestinationLabel?.let { destination ->
                Text(text = "Último destino: $destination", fontSize = 11.sp, color = BoxPlaySecondaryText)
            }
        }
        FilledIconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = BoxPlayCoral.copy(alpha = 0.12f),
                contentColor = BoxPlayCoral,
            ),
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Excluir projeto ${summary.name}",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CreateProjectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo projeto") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do projeto") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { "Novo projeto" }) }) {
                Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
