package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatcherTest {

    private val carpetaCancion = "primary:Music/Artist"
    private val cancion = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/" +
        "primary%3AMusic%2FArtist%2FSong.flac"

    @Test
    fun `prefiere la misma carpeta y el formato TTML`() {
        val sidecars = listOf(
            sidecar("Song.lrc", carpetaCancion, LyricsFormat.LRC),
            sidecar("Song.ttml", carpetaCancion, LyricsFormat.TTML),
            sidecar("Song.lrc", "primary:Music/Otra", LyricsFormat.LRC),
            sidecar("Otra.ttml", carpetaCancion, LyricsFormat.TTML)
        )

        val coincidencias = LyricsMatcher.match(cancion, sidecars)

        assertEquals(3, coincidencias.size)
        assertEquals("Song.ttml", coincidencias[0].sidecar.displayName)
        assertEquals("Song.lrc", coincidencias[1].sidecar.displayName)
        assertEquals("Song.lrc", coincidencias[2].sidecar.displayName)
        assertTrue(coincidencias[0].score > coincidencias[1].score)
        assertTrue(coincidencias[1].score > coincidencias[2].score)
    }

    @Test
    fun `reconoce sufijos de idioma pero no otros sufijos`() {
        val sidecars = listOf(
            sidecar("Song.es.lrc", carpetaCancion, LyricsFormat.LRC),
            sidecar("Song.en-US.ttml", carpetaCancion, LyricsFormat.TTML),
            sidecar("Song.2024.lrc", carpetaCancion, LyricsFormat.LRC),
            sidecar("Song 2.lrc", carpetaCancion, LyricsFormat.LRC)
        )

        val coincidencias = LyricsMatcher.match(cancion, sidecars)

        assertEquals(2, coincidencias.size)
        assertEquals("en-us", coincidencias[0].language)
        assertEquals("es", coincidencias[1].language)
    }

    @Test
    fun `los parentesis del nombre forman parte del nombre base`() {
        val cancionLive = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/" +
            "primary%3AMusic%2FArtist%2FSong%20%28Live%29.flac"
        val sidecar = sidecar(
            "Song (Live).lrc",
            carpetaCancion,
            LyricsFormat.LRC
        )

        val coincidencias = LyricsMatcher.match(cancionLive, listOf(sidecar))

        assertEquals(1, coincidencias.size)
        assertEquals("Song (Live).lrc", coincidencias[0].sidecar.displayName)
    }

    @Test
    fun `conserva los dos puntos que forman parte del nombre`() {
        val cancion = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/" +
            "primary%3ASong%3A%20Live.flac"
        val sidecar = sidecar(
            "Song: Live.lrc",
            "primary:",
            LyricsFormat.LRC
        )

        val coincidencias = LyricsMatcher.match(cancion, listOf(sidecar))

        assertEquals(1, coincidencias.size)
        assertEquals("Song: Live.lrc", coincidencias[0].sidecar.displayName)
    }

    private fun sidecar(
        nombre: String,
        carpeta: String,
        formato: LyricsFormat
    ): LyricsSidecar = LyricsSidecar(
        uri = "content://letras/$carpeta/$nombre",
        displayName = nombre,
        parentDocumentId = carpeta,
        format = formato
    )
}
