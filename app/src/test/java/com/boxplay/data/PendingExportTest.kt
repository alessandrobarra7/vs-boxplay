package com.boxplay.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PendingExportTest {
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        var failWrite = false
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrite) throw IOException("write failed")
            return transform(state.value).also { state.value = it }
        }
    }

    private val old = AudioBoxConfig.emptySlot(1).copy(
        originalFileName = "old.mp3", internalFilePath = "/audio/old.mp3",
        customLabel = "Entrada", volume = 0.4f,
    )
    private val exported = StoredAudio("new.mp3", "/audio/new.mp3")

    @Test fun exportIsPendingAndSurvivesRepositoryRecreation() = runBlocking {
        val store = MemoryStore()
        val repo = AudioBoxRepository(store)
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        val restored = AudioBoxRepository(MemoryStore(store.data.first()))
            .sceneState.first().selectedScene.boxes.single()
        assertEquals(old.internalFilePath, restored.internalFilePath)
        assertEquals(old.originalFileName, restored.originalFileName)
        assertEquals(exported.internalFilePath, restored.pendingExportPath)
        assertEquals(exported.originalFileName, restored.pendingExportName)
    }

    @Test fun saveIsTheOnlyStepThatReplacesTheSavedAudio() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        val previous = repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
        assertEquals(old.internalFilePath, previous.internalFilePath)
        val saved = repo.sceneState.first().selectedScene.boxes.single()
        assertEquals(exported.internalFilePath, saved.internalFilePath)
        assertNull(saved.pendingExportPath)
        assertNull(saved.pendingExportName)
        assertEquals("Entrada", saved.customLabel)
        assertEquals(0.4f, saved.volume)
    }

    @Test fun failedSaveKeepsPreviousAudioAndPendingExport() = runBlocking {
        val store = MemoryStore()
        val repo = AudioBoxRepository(store)
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        store.failWrite = true
        try {
            repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
            fail("Expected write failure")
        } catch (_: IOException) { }
        val box = repo.sceneState.first().selectedScene.boxes.single()
        assertEquals(old.internalFilePath, box.internalFilePath)
        assertEquals(exported.internalFilePath, box.pendingExportPath)
        store.failWrite = false
        repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
        assertNull(repo.sceneState.first().selectedScene.boxes.single().pendingExportPath)
    }

    @Test fun aNewerExportCannotBeOverwrittenByALateSave() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        val newer = StoredAudio("newer.mp3", "/audio/newer.mp3")
        assertEquals(exported.internalFilePath, repo.stageExport(1, 1, newer))
        try {
            repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
            fail("Expected stale save rejection")
        } catch (_: IllegalStateException) { }
        val box = repo.sceneState.first().selectedScene.boxes.single()
        assertEquals(old.internalFilePath, box.internalFilePath)
        assertEquals(newer.internalFilePath, box.pendingExportPath)
    }

    @Test fun deletedBoxIsNotResurrectedByExportOrSave() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        val deleted = repo.deleteBox(1, 1) as DeleteBoxResult.Deleted
        assertEquals(old.internalFilePath, deleted.internalFilePath)
        assertEquals(exported.internalFilePath, deleted.pendingExportPath)
        try {
            repo.stageExport(1, 1, exported)
            fail("Deleted box accepted export")
        } catch (_: IllegalStateException) { }
        try {
            repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
            fail("Deleted box accepted save")
        } catch (_: IllegalStateException) { }
        assertTrue(repo.sceneState.first().selectedScene.boxes.isEmpty())
    }

    @Test fun lockPreventsExportAndSave() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        repo.setSceneLocked(1, true)
        try {
            repo.stageExport(1, 1, exported)
            fail("Locked scene accepted export")
        } catch (_: IllegalStateException) { }
        try {
            repo.confirmAudio(1, 1, exported, exported.internalFilePath, 0.4f)
            fail("Locked scene accepted save")
        } catch (_: IllegalStateException) { }
        assertEquals(old.internalFilePath, repo.sceneState.first().selectedScene.boxes.single().internalFilePath)
    }

    @Test fun pendingOnlyBoxHasNoSavedAudioAndMetadataEditsKeepPending() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, AudioBoxConfig.emptySlot(1))
        repo.stageExport(1, 1, exported)
        repo.saveConfig(1, AudioBoxConfig.emptySlot(1).copy(customLabel = "Nome"))
        val box = repo.sceneState.first().selectedScene.boxes.single()
        assertNull(box.internalFilePath)
        assertEquals(exported.internalFilePath, box.pendingExportPath)
    }

    @Test fun manualSelectionCanReplaceExportOnlyWhenSaved() = runBlocking {
        val repo = AudioBoxRepository(MemoryStore())
        repo.saveConfig(1, old)
        repo.stageExport(1, 1, exported)
        val manual = StoredAudio("manual.mp3", "/audio/manual.mp3")
        val previous = repo.confirmAudio(1, 1, manual, exported.internalFilePath, 0.4f)
        assertEquals(exported.internalFilePath, previous.pendingExportPath)
        val box = repo.sceneState.first().selectedScene.boxes.single()
        assertEquals(manual.internalFilePath, box.internalFilePath)
        assertNull(box.pendingExportPath)
    }
}

