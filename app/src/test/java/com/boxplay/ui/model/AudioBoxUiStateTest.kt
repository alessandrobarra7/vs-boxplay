package com.boxplay.ui.model

import com.boxplay.data.AudioBoxConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBoxUiStateTest {
    @Test
    fun emptySlotsDefaultsToNoBoxesInTheScenesModel() {
        // Scenes now start empty and boxes are added one at a time via
        // AudioBoxRepository.addBox(), instead of a fixed 20-box layout.
        val boxes = AudioBoxConfig.emptySlots()

        assertEquals(0, boxes.size)
    }

    @Test
    fun emptySlotsCanCreateAnExplicitNumberOfSlots() {
        val boxes = AudioBoxConfig.emptySlots(5)

        assertEquals(5, boxes.size)
        assertEquals((1..5).toList(), boxes.map { it.id })
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

    // --- Paywall gating (box 1 free, boxes 2-20 require the unlock purchase) ---

    @Test
    fun boxLockedByPaywallBlocksEveryAction_evenWithSavedAudioAndUnlockedCadeado() {
        val paywalledBox = AudioBoxUiState(
            id = 5,
            displayName = "Efeito.mp3",
            originalFileName = "Efeito.mp3",
            internalFilePath = "/app/audio/efeito.mp3",
            hasPendingAudio = false,
            volume = 0.8f,
            isLocked = false, // the manual cadeado is OFF
            playbackState = AudioPlaybackState.Saved,
            statusMessage = "Compre para desbloquear",
            isLockedByPaywall = true,
        )

        assertFalse("paywall must block picking a new file", paywalledBox.canPickAudio)
        assertFalse("paywall must block saving", paywalledBox.canSave)
        assertFalse("paywall must block play, even with saved audio", paywalledBox.canPlay)
        assertFalse("paywall must block restart, even with saved audio", paywalledBox.canRestart)
        assertFalse("paywall must block volume changes", paywalledBox.canChangeVolume)
    }

    @Test
    fun manualLockAndPaywallLockAreIndependentReasonsToBeLocked() {
        val onlyManuallyLocked = AudioBoxUiState(
            id = 1,
            displayName = "Box 1",
            originalFileName = "a.mp3",
            internalFilePath = "/app/audio/a.mp3",
            hasPendingAudio = false,
            volume = 0.5f,
            isLocked = true,
            playbackState = AudioPlaybackState.Saved,
            statusMessage = "Bloqueado",
            isLockedByPaywall = false,
        )
        assertTrue(onlyManuallyLocked.canPlay)
        assertFalse(onlyManuallyLocked.canEditSettings)

        val onlyPaywallLocked = onlyManuallyLocked.copy(isLocked = false, isLockedByPaywall = true)
        assertFalse(onlyPaywallLocked.canPlay)
        assertFalse(onlyPaywallLocked.canEditSettings)

        val neitherLocked = onlyManuallyLocked.copy(isLocked = false, isLockedByPaywall = false)
        assertTrue(neitherLocked.canPlay)
        assertTrue(neitherLocked.canEditSettings)
    }

    @Test
    fun freeBoxOneIsNeverLockedByPaywallByDefault() {
        val freeBox = AudioBoxUiState(
            id = 1,
            displayName = "Box 1",
            originalFileName = null,
            internalFilePath = null,
            hasPendingAudio = false,
            volume = 0.8f,
            isLocked = false,
            playbackState = AudioPlaybackState.Empty,
            statusMessage = "Vazio",
        )

        assertFalse(freeBox.isLockedByPaywall)
    }
}
