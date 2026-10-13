package com.cloudjex.jexplayer.data.repository

import com.cloudjex.jexplayer.data.model.CachedSong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReusePolicyTest {

    private fun cached(
        size: Long? = 100L,
        lastModified: Long? = 1_000L,
        albumArtUri: String? = null,
        albumArtScanned: Boolean? = null
    ) = CachedSong(
        id = "content://a/1",
        title = "t",
        artist = "a",
        album = "b",
        duration = 1L,
        uri = "content://a/1/document",
        albumArtUri = albumArtUri,
        size = size,
        lastModified = lastModified,
        albumArtScanned = albumArtScanned
    )

    @Test
    fun reusesWhenSizeAndDateMatch() {
        assertTrue(ScanReusePolicy.canReuse(cached(), size = 100L, lastModified = 1_000L))
    }

    @Test
    fun rejectsWhenSizeChanged() {
        assertFalse(ScanReusePolicy.canReuse(cached(), size = 200L, lastModified = 1_000L))
    }

    @Test
    fun rejectsWhenDateChanged() {
        assertFalse(ScanReusePolicy.canReuse(cached(), size = 100L, lastModified = 2_000L))
    }

    @Test
    fun rejectsWhenNoCachedEntry() {
        assertFalse(ScanReusePolicy.canReuse(null, size = 100L, lastModified = 1_000L))
    }

    @Test
    fun rejectsWhenCachedValidationMissing() {
        assertFalse(ScanReusePolicy.canReuse(cached(size = null), 100L, 1_000L))
        assertFalse(ScanReusePolicy.canReuse(cached(lastModified = null), 100L, 1_000L))
    }

    @Test
    fun rejectsWhenCurrentValidationMissing() {
        assertFalse(ScanReusePolicy.canReuse(cached(), null, 1_000L))
        assertFalse(ScanReusePolicy.canReuse(cached(), 100L, null))
    }

    @Test
    fun rejectsWhenValidationUnreliable() {
        assertFalse(ScanReusePolicy.canReuse(cached(size = 0L, lastModified = 0L), 0L, 0L))
    }

    @Test
    fun reusesEntryWithoutCoverWhenAlreadyScanned() {
        val song = cached(albumArtScanned = true)

        assertTrue(ScanReusePolicy.canReuseSong(song, 100L, 1_000L))
    }

    @Test
    fun rejectsLegacyEntryNeverScannedForCover() {
        // Caché antigua: aunque los metadatos sigan vigentes, falta por comprobar la portada.
        assertFalse(ScanReusePolicy.canReuseSong(cached(), 100L, 1_000L))
        assertFalse(ScanReusePolicy.canReuseSong(cached(albumArtScanned = null), 100L, 1_000L))
    }

    @Test
    fun rejectsUnreadableEntrySoCoverIsRetried() {
        assertFalse(ScanReusePolicy.canReuseSong(cached(albumArtScanned = false), 100L, 1_000L))
    }

    @Test
    fun reusesEntryWithCoverOnlyIfFileStillExists() {
        val song = cached(albumArtUri = "file:///art/1.img", albumArtScanned = true)

        assertTrue(ScanReusePolicy.canReuseSong(song, 100L, 1_000L) { true })
        assertFalse(ScanReusePolicy.canReuseSong(song, 100L, 1_000L) { false })
    }

    @Test
    fun doesNotTouchDiskWhenEntryHasNoCover() {
        var checked = false

        val reused = ScanReusePolicy.canReuseSong(cached(albumArtScanned = true), 100L, 1_000L) {
            checked = true
            true
        }

        assertTrue(reused)
        assertFalse(checked)
    }

    @Test
    fun rejectsChangedFileEvenWithCover() {
        val song = cached(albumArtUri = "file:///art/1.img", albumArtScanned = true)

        assertFalse(ScanReusePolicy.canReuseSong(song, 200L, 1_000L) { true })
        assertFalse(ScanReusePolicy.canReuseSong(song, 100L, 2_000L) { true })
    }
}
