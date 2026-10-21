package com.cloudjex.jexplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTechnicalInfoTest {

    @Test
    fun mergeAddsNewlyAvailableFieldsWithoutErasingExistingOnes() {
        val early = AudioTechnicalInfo(sampleRateHz = 44_100, channelCount = 2)

        val merged = early.merge(AudioTechnicalInfo(bitRateBps = 1_089_000L))

        assertEquals(44_100, merged.sampleRateHz)
        assertEquals(1_089_000L, merged.bitRateBps)
        assertEquals(2, merged.channelCount)
        assertEquals(null, merged.bitsPerSample)
    }

    @Test
    fun mergeUsesLaterVerifiedValueAndIgnoresUnknownOrInvalidValues() {
        val early = AudioTechnicalInfo(sampleRateHz = 48_000, bitRateBps = 320_000L)

        val merged = early.merge(
            AudioTechnicalInfo(sampleRateHz = 96_000, bitRateBps = 0L, bitsPerSample = -1)
        )

        assertEquals(96_000, merged.sampleRateHz)
        assertEquals(320_000L, merged.bitRateBps)
        assertEquals(null, merged.bitsPerSample)
    }

    @Test
    fun emptyMetadataDoesNotClaimToHaveValues() {
        assertFalse(AudioTechnicalInfo().hasValues)
        assertTrue(AudioTechnicalInfo(channelCount = 1).hasValues)
    }

    @Test
    fun displayValuesOmitsUnknownFieldsAndKeepsStableOrder() {
        val info = AudioTechnicalInfo(
            sampleRateHz = 44_100,
            channelCount = 2,
            bitsPerSample = 16
        )

        assertEquals(listOf("44.1 kHz", "Estéreo", "16 bits"), info.displayValues())
    }

    @Test
    fun metadataIsOnlyExposedForTheSongItBelongsTo() {
        val previousSongInfo = SongAudioTechnicalInfo(
            songId = "song-a",
            info = AudioTechnicalInfo(sampleRateHz = 44_100)
        )

        assertEquals(null, previousSongInfo.forSong("song-b"))
        assertEquals(AudioTechnicalInfo(sampleRateHz = 44_100), previousSongInfo.forSong("song-a"))
    }
}
