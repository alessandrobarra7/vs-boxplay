package com.boxplay.multitrack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultitrackProjectTest {

    private fun track(id: String, offsetUs: Long, durationUs: Long) = MultitrackTrack.create(
        id = id, name = id, internalFilePath = "/x/$id", originalFileName = null,
        durationUs = durationUs, offsetUs = offsetUs, createdAt = 1L,
    )

    private fun emptyProject() = MultitrackProject(
        id = "proj-1", name = "Minha musica", createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun durationIsZeroForProjectWithoutTracks() {
        assertEquals(0L, emptyProject().durationUs)
    }

    @Test
    fun durationIsMaxOfOffsetPlusDurationAcrossTracks() {
        val project = emptyProject()
            .withTrack(track("a", offsetUs = 0L, durationUs = 100_000L))
            .withTrack(track("b", offsetUs = 50_000L, durationUs = 100_000L))

        assertEquals(150_000L, project.durationUs)
    }

    @Test
    fun mutedTrackStillCountsTowardsProjectDuration() {
        val muted = track("a", offsetUs = 0L, durationUs = 200_000L).withMuted(true)
        val project = emptyProject().withTrack(muted)
        assertEquals(200_000L, project.durationUs)
    }

    @Test
    fun withoutTrackRemovesOnlyMatchingTrack() {
        val project = emptyProject()
            .withTrack(track("a", 0L, 1000L))
            .withTrack(track("b", 0L, 1000L))
            .withoutTrack("a")

        assertEquals(1, project.tracks.size)
        assertEquals("b", project.tracks.first().id)
    }

    @Test
    fun replacingTrackUpdatesOnlyMatchingId() {
        val project = emptyProject().withTrack(track("a", 0L, 1000L))
        val updated = project.replacingTrack(project.tracks.first().withVolume(0.5f))
        assertEquals(0.5f, updated.tracks.first().volume)
        assertEquals(1, updated.tracks.size)
    }

    @Test
    fun newProjectUsesCurrentSchemaVersionByDefault() {
        assertEquals(MultitrackProject.CURRENT_SCHEMA_VERSION, emptyProject().schemaVersion)
    }

    @Test
    fun linkedDestinationStartsUnset() {
        assertNull(emptyProject().linkedSceneId)
        assertNull(emptyProject().linkedBoxId)
    }

    @Test
    fun withLinkedDestinationSetsBothIds() {
        val project = emptyProject().withLinkedDestination(sceneId = 1, boxId = 3)
        assertEquals(1, project.linkedSceneId)
        assertEquals(3, project.linkedBoxId)
    }

    @Test
    fun withLastRenderedFilePathIsIndependentFromTracks() {
        val project = emptyProject()
            .withTrack(track("a", 0L, 1000L))
            .withLastRenderedFilePath("/data/boxplay-audios/minha_musica_boxplay.mp3")

        assertEquals("/data/boxplay-audios/minha_musica_boxplay.mp3", project.lastRenderedFilePath)
        assertEquals(1, project.tracks.size)
    }
}
