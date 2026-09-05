package com.boxplay.ui.model

import com.boxplay.data.AudioBoxConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBoxUiStateTest {
    @Test
    fun emptySlotsCreateExactlyTwentyFixedSlots() {
        val boxes = AudioBoxConfig.emptySlots()

        assertEquals(20, boxes.size)
        assertEquals((1..20).toList(), boxes.map { it.id })
    }

    @Test
    fun lockedSavedBoxStillKeepsPlaybackActionsAvailable() {
        val lockedBox = AudioBoxUiState(
            id = 1,
            displayName = "Abertura.mp3",
            originalFileName = "Abertura.mp3",
            internalFilePath = "/app/audio/abertura.mp3",
            hasPendingAudio = false,
            volume = 0.8f,
            isLocked = true,
            playbackState = AudioPlaybackState.Saved,
            statusMessage = "Bloqueado",
        )

        assertFalse(lockedBox.canEditSettings)
        assertTrue(lockedBox.canPlay)
        assertTrue(lockedBox.canRestart)
    }

    @Test
    fun unsavedSelectionCanBeSavedButNotPlayedYet() {
        val selectedBox = AudioBoxUiState(
            id = 2,
            displayName = "Vinheta.mp3",
            originalFileName = null,
            internalFilePath = null,
            hasPendingAudio = true,
            volume = 0.8f,
            isLocked = false,
            playbackState = AudioPlaybackState.Unsaved,
            statusMessage = "Selecionado - salvar",
        )

        assertTrue(selectedBox.canSave)
        assertFalse(selectedBox.canPlay)
    }
}
