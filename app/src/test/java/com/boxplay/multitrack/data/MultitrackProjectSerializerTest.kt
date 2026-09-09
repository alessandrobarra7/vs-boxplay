package com.boxplay.multitrack.data

import com.boxplay.multitrack.model.MultitrackProject
import com.boxplay.multitrack.model.MultitrackTrack
import com.boxplay.multitrack.model.TrackRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultitrackProjectSerializerTest {

    private fun sampleProject(): MultitrackProject {
        val track = MultitrackTrack.create(
            id = "track-1",
            name = "Voz / Backing",
            internalFilePath = "/data/multitrack-audios/p1/track-1_voz.wav",
            originalFileName = "voz original.wav",
            durationUs = 272_518_000L,
            offsetUs = 350_000L,
            volume = 0.8f,
            muted = false,
            routing = TrackRouting.RIGHT,
            sampleRate = 44_100,
            channelCount = 2,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )
        return MultitrackProject(
            id = "proj-1",
            name = "Minha música",
            tracks = listOf(track),
            linkedSceneId = 1,
            linkedBoxId = 3,
            lastRenderedFilePath = "/data/boxplay-audios/minha_musica_boxplay.mp3",
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )
    }

    @Test
    fun roundTripPreservesAllFields() {
        val project = sampleProject()
        val json = MultitrackProjectSerializer.toJson(project)
        val result = MultitrackProjectSerializer.fromJson(json)

        assertTrue(result is MultitrackProjectSerializer.ParseResult.Success)
        val loaded = (result as MultitrackProjectSerializer.ParseResult.Success).project

        assertEquals(project, loaded)
    }

    @Test
    fun roundTripPreservesUnicodeAndSpecialCharactersInNames() {
        val project = sampleProject().withName("Projeto \"especial\" — ção\nnova linha")
        val json = MultitrackProjectSerializer.toJson(project)
        val result = MultitrackProjectSerializer.fromJson(json) as MultitrackProjectSerializer.ParseResult.Success

        assertEquals(project.name, result.project.name)
    }

    @Test
    fun malformedJsonIsReportedAsCorruptedNotACrash() {
        val result = MultitrackProjectSerializer.fromJson("{ isso nao e json")
        assertTrue(result is MultitrackProjectSerializer.ParseResult.Corrupted)
    }

    @Test
    fun missingRequiredFieldIsReportedAsCorrupted() {
        val result = MultitrackProjectSerializer.fromJson("""{"schemaVersion":1,"name":"sem id"}""")
        assertTrue(result is MultitrackProjectSerializer.ParseResult.Corrupted)
    }

    @Test
    fun futureSchemaVersionIsNotOverwrittenAutomatically() {
        val json = """{"schemaVersion":99,"id":"p","name":"n","createdAt":1,"updatedAt":1,"tracks":[]}"""
        val result = MultitrackProjectSerializer.fromJson(json)
        assertTrue(result is MultitrackProjectSerializer.ParseResult.UnsupportedSchema)
        assertEquals(99, (result as MultitrackProjectSerializer.ParseResult.UnsupportedSchema).foundVersion)
    }

    @Test
    fun projectWithoutTracksSerializesWithEmptyArray() {
        val project = MultitrackProject(id = "p", name = "vazio", createdAt = 1L, updatedAt = 1L)
        val json = MultitrackProjectSerializer.toJson(project)
        val result = MultitrackProjectSerializer.fromJson(json) as MultitrackProjectSerializer.ParseResult.Success
        assertTrue(result.project.tracks.isEmpty())
    }

    @Test
    fun trackWithUnknownRoutingFallsBackToCenterInsteadOfCrashing() {
        val json = """
            {"schemaVersion":1,"id":"p","name":"n","createdAt":1,"updatedAt":1,"tracks":[
                {"id":"t1","name":"x","internalFilePath":"/x","durationUs":1000,"createdAt":1,"routing":"DIAGONAL"}
            ]}
        """.trimIndent()
        val result = MultitrackProjectSerializer.fromJson(json) as MultitrackProjectSerializer.ParseResult.Success
        assertEquals(TrackRouting.CENTER, result.project.tracks.first().routing)
    }
}
