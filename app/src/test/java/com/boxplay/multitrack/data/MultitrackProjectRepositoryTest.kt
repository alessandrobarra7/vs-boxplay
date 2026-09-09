package com.boxplay.multitrack.data

import com.boxplay.multitrack.model.MultitrackProject
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultitrackProjectRepositoryTest {

    private lateinit var repository: MultitrackProjectRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("multitrack-projects-test").toFile()
        repository = MultitrackProjectRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun project(id: String = "proj-1") = MultitrackProject(
        id = id, name = "Minha música", createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun saveThenLoadReturnsTheSameProject() {
        val project = project()
        assertTrue(repository.saveProject(project))

        val result = repository.loadProject(project.id)
        assertTrue(result is MultitrackProjectSerializer.ParseResult.Success)
        assertEquals(project, (result as MultitrackProjectSerializer.ParseResult.Success).project)
    }

    @Test
    fun loadingMissingProjectReportsCorruptedNotACrash() {
        val result = repository.loadProject("nao-existe")
        assertTrue(result is MultitrackProjectSerializer.ParseResult.Corrupted)
    }

    @Test
    fun savingOverwritesThePreviousVersionAtomicallyWithoutLeftoverTemp() {
        repository.saveProject(project().withName("Nome 1"))
        repository.saveProject(project().withName("Nome 2"))

        val result = repository.loadProject("proj-1") as MultitrackProjectSerializer.ParseResult.Success
        assertEquals("Nome 2", result.project.name)

        val leftovers = tempDir.listFiles { file -> file.name.endsWith(".tmp") }
        assertTrue(leftovers == null || leftovers.isEmpty())
    }

    @Test
    fun deleteProjectRemovesTheFile() {
        repository.saveProject(project())
        assertTrue(repository.deleteProject("proj-1"))
        assertTrue(repository.loadProject("proj-1") is MultitrackProjectSerializer.ParseResult.Corrupted)
    }

    @Test
    fun deleteProjectOnMissingProjectIsANoOpSuccess() {
        assertTrue(repository.deleteProject("nunca-existiu"))
    }

    @Test
    fun loadAllValidProjectsSkipsCorruptedFilesAndSortsByUpdatedAtDescending() {
        repository.saveProject(project("a").copy(updatedAt = 10L))
        repository.saveProject(project("b").copy(updatedAt = 20L))
        File(tempDir, "corrompido.json").writeText("{ nao e json")

        val projects = repository.loadAllValidProjects()
        assertEquals(2, projects.size)
        assertEquals("b", projects.first().id)
    }
}
