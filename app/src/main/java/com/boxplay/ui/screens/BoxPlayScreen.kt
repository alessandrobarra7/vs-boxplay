package com.boxplay.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxplay.ui.components.AudioBoxCard
import com.boxplay.ui.theme.BoxPlayBackground
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayHeader
import com.boxplay.ui.theme.BoxPlayMutedControl
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlayTheme
import com.boxplay.viewmodel.BoxPlayViewModel

@Composable
fun BoxPlayScreen(viewModel: BoxPlayViewModel = viewModel()) {
    val boxes by viewModel.boxes.collectAsStateWithLifecycle()
    val unlockPriceText by viewModel.unlockPriceText.collectAsStateWithLifecycle()
    val billingError by viewModel.billingError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingBoxId by rememberSaveable { mutableStateOf<Int?>(null) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val boxId = pickingBoxId
        pickingBoxId = null
        if (uri != null && boxId != null) {
            viewModel.onAudioSelected(boxId, uri)
        }
    }

    // FIX: billingError used to be exposed by the ViewModel but never collected
    // here, so a failed purchase (no product configured, Play Store
    // unavailable, network error, etc.) left the user staring at a screen that
    // looked unresponsive with no feedback at all. Surface it as a one-off
    // snackbar and clear it once shown so it doesn't reappear on
    // recomposition/rotation.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(billingError) {
        val message = billingError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onBillingErrorShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BoxPlayBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val gridColumns = if (maxWidth < 320.dp) 1 else 2
            val gridState = rememberLazyGridState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxPlayHeaderBar(onStopAll = viewModel::onStopAllClicked)

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = 7.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(boxes, key = { it.id }) { boxState ->
                            AudioBoxCard(
                                state = boxState,
                                onPickAudio = {
                                    pickingBoxId = boxState.id
                                    audioPicker.launch(arrayOf("audio/*"))
                                },
                                onSave = { viewModel.onSaveClicked(boxState.id) },
                                onToggleLock = { viewModel.onLockClicked(boxState.id) },
                                onTogglePlay = { viewModel.onPlayPauseClicked(boxState.id) },
                                onRestart = { viewModel.onRestartClicked(boxState.id) },
                                onVolumeChange = { volume -> viewModel.onVolumeChanged(boxState.id, volume) },
                                modifier = Modifier.fillMaxWidth(),
                                onUnlockClicked = {
                                    val activity = context.findActivity()
                                    if (activity != null) viewModel.onUnlockClicked(activity)
                                },
                                unlockPriceText = unlockPriceText,
                            )
                        }
                    }

                    GridScrollIndicator(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 1.dp),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun GridScrollIndicator(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0 || visibleItems.isEmpty() || totalItems <= visibleItems.size) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(5.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val firstVisibleIndex = visibleItems.minOf { it.index }
        val maxFirstVisibleIndex = (totalItems - visibleItems.size).coerceAtLeast(1)
        val scrollProgress =
            (firstVisibleIndex.toFloat() / maxFirstVisibleIndex.toFloat()).coerceIn(0f, 1f)
        val thumbHeight = (maxHeight * (visibleItems.size.toFloat() / totalItems.toFloat()))
            .coerceAtLeast(36.dp)
            .coerceAtMost(maxHeight)
        val maxOffset = maxHeight - thumbHeight

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BoxPlayMutedControl.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .offset(y = maxOffset * scrollProgress)
                .height(thumbHeight)
                .width(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BoxPlayElectricBlue.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun BoxPlayHeaderBar(onStopAll: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        color = BoxPlayHeader,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "Onda sonora",
                tint = BoxPlayElectricBlue,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "BOXPLAY",
                color = BoxPlayPrimaryText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                ),
            )
            IconButton(onClick = onStopAll) {
                Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = "Parar todos os audios",
                    tint = BoxPlayPrimaryText,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 920)
@Composable
private fun BoxPlayScreenPhonePreview() {
    BoxPlayTheme {
        BoxPlayScreen()
    }
}
