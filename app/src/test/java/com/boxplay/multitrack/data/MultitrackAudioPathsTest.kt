package com.boxplay.multitrack.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultitrackAudioPathsTest {

    @Test
    fun sanitizeFileNameReplacesUnsafeCharacters() {
        assertEquals("voz_original.wav", MultitrackAudioPaths.sanitizeFileName("voz original.wav"))
    }

    @Test
    fun sanitizeFileNameNeverReturnsBlank() {
        assertEquals("audio", MultitrackAudioPaths.sanitizeFileName("###"))
    }

    @Test
    fun sanitizeFileNameCapsLength() {
        val long = "a".repeat(200)
        assertTrue(MultitrackAudioPaths.sanitizeFileName(long).length <= 80)
    }

    @Test
    fun trackFileNamePrefixesWithTrackId() {
        val name = MultitrackAudioPaths.trackFileName("track-1", "voz original.wav")
        assertEquals("track-1_voz_original.wav", name)
    }

    @Test
    fun isWithinAcceptsFileInsideRoot() {
        val root = Files.createTempDirectory("mt-root").toFile()
        val file = File(root, "sub/track.wav").apply {
            parentFile.mkdirs()
            writeText("x")
        }
        assertTrue(MultitrackAudioPaths.isWithin(root, file))
        root.deleteRecursively()
    }

    @Test
    fun isWithinRejectsPathOutsideRoot() {
        val root = Files.createTempDirectory("mt-root").toFile()
        val outside = Files.createTempDirectory("mt-outside").toFile()
        val file = File(outside, "track.wav").apply { writeText("x") }

        assertFalse(MultitrackAudioPaths.isWithin(root, file))

        root.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun isWithinRejectsTraversalViaDotDot() {
        val root = Files.createTempDirectory("mt-root").toFile()
        val traversal = File(root, "../outside.wav")

        assertFalse(MultitrackAudioPaths.isWithin(root, traversal))

        root.deleteRecursively()
    }
}
