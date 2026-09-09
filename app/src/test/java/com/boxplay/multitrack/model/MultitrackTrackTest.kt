package com.boxplay.multitrack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultitrackTrackTest {

    private fun sampleTrack(volume: Float = 1f, offsetUs: Long = 0L, muted: Boolean = false) =
        MultitrackTrack.create(
            id = "track-1",
            name = "Bateria",
            internalFilePath = "/data/multitrack-audios/p1/track-1_bateria.wav",
            originalFileName = "bateria.wav",
            durationUs = 272_518_000L,
            offsetUs = offsetUs,
            volume = volume,
            muted = muted,
            createdAt = 1_000L,
        )

    @Test
    fun createClampsVolumeAboveOne() {
        val track = MultitrackTrack.create(
            id = "t1", name = "x", internalFilePath = "x", originalFileName = null,
            durationUs = 1000L, volume = 1.5f, createdAt = 1L,
        )
        assertEquals(1f, track.volume)
    }

    @Test
    fun withVolumeClampsAboveOne() {
        val track = sampleTrack().withVolume(1.5f)
        assertEquals(1f, track.volume)
    }

    @Test
    fun withVolumeClampsBelowZero() {
        val track = sampleTrack().withVolume(-0.3f)
        assertEquals(0f, track.volume)
    }

    @Test
    fun createClampsNegativeOffsetToZero() {
        val track = MultitrackTrack.create(
            id = "t1", name = "Click", internalFilePath = "x", originalFileName = null,
            durationUs = 1000L, offsetUs = -500L, createdAt = 1L,
        )
        assertEquals(0L, track.offsetUs)
    }

    @Test
    fun withOffsetUsNeverGoesNegative() {
        val track = sampleTrack(offsetUs = 100L).withOffsetUs(-50L)
        assertEquals(0L, track.offsetUs)
    }

    @Test
    fun shiftOffsetUsClampsAtZero() {
        val track = sampleTrack(offsetUs = 5_000L).shiftOffsetUs(-MultitrackTrack.FINE_OFFSET_STEP_US * 10)
        assertEquals(0L, track.offsetUs)
    }

    @Test
    fun shiftOffsetUsAppliesFineStep() {
        val track = sampleTrack(offsetUs = 0L).shiftOffsetUs(MultitrackTrack.FINE_OFFSET_STEP_US)
        assertEquals(MultitrackTrack.FINE_OFFSET_STEP_US, track.offsetUs)
    }

    @Test
    fun muteDoesNotChangeStoredVolume() {
        val track = sampleTrack(volume = 0.73f).withMuted(true)
        assertTrue(track.muted)
        assertEquals(0.73f, track.volume)

        val unmuted = track.withMuted(false)
        assertEquals(0.73f, unmuted.volume)
    }

    @Test
    fun withRoutingUpdatesRoutingIndependently() {
        val track = sampleTrack().withRouting(TrackRouting.LEFT)
        assertEquals(TrackRouting.LEFT, track.routing)
    }

    @Test
    fun withNameDoesNotRenamePhysicalFile() {
        val track = sampleTrack().withName("Bateria (novo nome)")
        assertEquals("Bateria (novo nome)", track.name)
        assertEquals("/data/multitrack-audios/p1/track-1_bateria.wav", track.internalFilePath)
    }
}
