package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MultitrackTransportBar(
    previewEnabled: Boolean,
    renderEnabled: Boolean,
    isPreviewing: Boolean,
    onPreviewClick: () -> Unit,
    onRenderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        MultitrackActionButton(
            label = if (isPreviewing) "Pausar" else "Tocar",
            icon = if (isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            enabled = previewEnabled,
            onClick = onPreviewClick,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        MultitrackActionButton(
            label = "Exportar para Box",
            icon = Icons.Rounded.FileUpload,
            enabled = renderEnabled,
            emphasized = true,
            onClick = onRenderClick,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        )
    }
}

