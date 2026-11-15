package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.model.CachedSong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryReloadPolicyTest {

    private fun entry(id: String, title: String = "t") = CachedSong(
        id = id,
        title = title,
        artist = "a",
        album = "b",
        duration = 1L,
        uri = id,
        albumArtUri = null,
        size = 100L,
        lastModified = 1_000L
    )

    @Test
    fun hidesIndicatorOnStartupRevalidationWithCache() {
        assertFalse(shouldShowScanIndicator(LibraryReloadCause.Startup, cacheHasSongs = true))
    }

    @Test
    fun showsIndicatorOnStartupWhenCacheIsEmpty() {
        assertTrue(shouldShowScanIndicator(LibraryReloadCause.Startup, cacheHasSongs = false))
    }

    @Test
    fun showsIndicatorOnManualRefreshEvenWithCache() {
        assertTrue(shouldShowScanIndicator(LibraryReloadCause.Manual, cacheHasSongs = true))
    }

    @Test
    fun showsIndicatorOnFolderChangeEvenWithCache() {
        assertTrue(shouldShowScanIndicator(LibraryReloadCause.FoldersChanged, cacheHasSongs = true))
    }

    @Test
    fun skipsRepublishWhenScanChangedNothing() {
        // Instancias distintas pero contenido idéntico: revalidación sin cambios.
        val published = listOf(entry("a"), entry("b"))
        val scanned = listOf(entry("a"), entry("b"))
        assertFalse(shouldRepublishLibrary(scanned, published))
    }

    @Test
    fun republishesWhenSongAppears() {
        assertTrue(shouldRepublishLibrary(listOf(entry("a"), entry("b")), listOf(entry("a"))))
    }

    @Test
    fun republishesWhenSongDisappears() {
        assertTrue(shouldRepublishLibrary(listOf(entry("a")), listOf(entry("a"), entry("b"))))
    }

    @Test
    fun republishesWhenMetadataChanges() {
        assertTrue(
            shouldRepublishLibrary(
                listOf(entry("a", title = "nuevo")),
                listOf(entry("a", title = "viejo"))
            )
        )
    }

    @Test
    fun republishesWhenOrderChanges() {
        assertTrue(
            shouldRepublishLibrary(listOf(entry("b"), entry("a")), listOf(entry("a"), entry("b")))
        )
    }

    @Test
    fun republishesOnFirstImportOverEmptyCache() {
        assertTrue(shouldRepublishLibrary(listOf(entry("a")), emptyList()))
    }

    @Test
    fun hidesEmptyStateWhileCacheRestores() {
        // Sin scan activo ni primera publicación: la biblioteca se restaura,
        // no está vacía.
        assertFalse(shouldShowEmptyLibrary(isScanning = false, isLibraryInitialized = false))
    }

    @Test
    fun showsEmptyStateWhileScanning() {
        // Primera importación: el escaneo es una operación real que informar.
        assertTrue(shouldShowEmptyLibrary(isScanning = true, isLibraryInitialized = false))
    }

    @Test
    fun showsEmptyStateAfterFirstPublish() {
        // La caché publicó una lista real: si sigue vacía, es vacía verdadera.
        assertTrue(shouldShowEmptyLibrary(isScanning = false, isLibraryInitialized = true))
    }

    @Test
    fun keepsEmptyStateAfterSilentScanEnds() {
        // Revalidación silenciosa terminada: el estado vacío persiste.
        assertTrue(shouldShowEmptyLibrary(isScanning = false, isLibraryInitialized = true))
    }
}
