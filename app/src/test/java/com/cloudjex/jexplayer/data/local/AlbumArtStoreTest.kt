package com.cloudjex.jexplayer.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlbumArtStoreTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val directory: File get() = File(temporary.root, "album_art")

    private fun store() = AlbumArtStore(directory)

    @Test
    fun fileForIsDeterministicForSameInputs() {
        val store = store()

        assertEquals(
            store.fileFor(SONG, 100L, 1_000L),
            store.fileFor(SONG, 100L, 1_000L)
        )
    }

    @Test
    fun differentSongsNeverShareFile() {
        val store = store()

        assertNotEquals(
            store.fileFor("content://tree/a.mp3", 100L, 1_000L),
            store.fileFor("content://tree/b.mp3", 100L, 1_000L)
        )
    }

    @Test
    fun changingFileStampProducesNewNameSoOldCoverIsInvalidated() {
        val store = store()

        val original = store.fileFor(SONG, 100L, 1_000L)
        assertNotEquals(original, store.fileFor(SONG, 200L, 1_000L))
        assertNotEquals(original, store.fileFor(SONG, 100L, 2_000L))
        // Valores ausentes (proveedores sin metadatos fiables) no colisionan con 0/-1.
        assertNotEquals(store.fileFor(SONG, null, null), store.fileFor(SONG, -1L, -1L))
    }

    @Test
    fun storeWritesBytesAndExists() {
        val store = store()
        val bytes = byteArrayOf(1, 2, 3, 4)

        val file = store.store(SONG, 100L, 1_000L, bytes)

        assertTrue(file != null && file.isFile)
        assertArrayEquals(bytes, file!!.readBytes())
        assertTrue(store.exists(SONG, 100L, 1_000L))
        assertFalse(store.exists(SONG, 200L, 1_000L))
    }

    @Test
    fun storeOverwritesSameDeterministicEntryAtomically() {
        val store = store()

        store.store(SONG, 100L, 1_000L, byteArrayOf(1))
        store.store(SONG, 100L, 1_000L, byteArrayOf(9, 9, 9))

        val file = store.fileFor(SONG, 100L, 1_000L)
        assertArrayEquals(byteArrayOf(9, 9, 9), file.readBytes())
        // No deben quedar temporales tras una escritura correcta.
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun pruneKeepsReferencedFilesAndRemovesTheRest() {
        val store = store()
        val kept = store.store("content://a/1", 10L, 20L, byteArrayOf(1))!!
        val orphan = store.store("content://a/2", 10L, 20L, byteArrayOf(2))!!

        val removed = store.prune(listOf(kept.toURI().toString()))

        assertEquals(1, removed)
        assertTrue(kept.isFile)
        assertFalse(orphan.exists())
    }

    @Test
    fun pruneRemovesTemporaryFilesAndClearsWhenNothingReferenced() {
        val store = store()
        store.store("content://a/1", 10L, 20L, byteArrayOf(1))
        directory.resolve("leftover.img.tmp").writeBytes(byteArrayOf(0))

        val removed = store.prune(emptyList())

        assertEquals(2, removed)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun pruneHandlesMissingReferencesAndForeignFiles() {
        val store = store()
        val kept = store.store("content://a/1", 10L, 20L, byteArrayOf(1))!!
        val foreign = directory.resolve("not_ours.txt").apply { writeText("x") }

        val removed = store.prune(listOf(kept.toURI().toString(), null, ""))

        assertEquals(0, removed)
        assertTrue(kept.isFile)
        assertTrue(foreign.isFile)
    }

    @Test
    fun clearRemovesAllOwnFiles() {
        val store = store()
        store.store("content://a/1", 10L, 20L, byteArrayOf(1))
        store.store("content://a/2", 10L, 20L, byteArrayOf(2))

        assertEquals(2, store.clear())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sharedStoreReusesOneFileForIdenticalArtwork() {
        val store = store()
        val bytes = byteArrayOf(7, 7, 7, 7)

        val first = store.storeShared(bytes)
        val second = store.storeShared(bytes)

        assertTrue(first != null && first.isFile)
        assertEquals(first, second)
        assertEquals(1, directory.listFiles().orEmpty().count { it.extension == "img" })
    }

    @Test
    fun sharedStoreWritesDifferentContentToDifferentFiles() {
        val store = store()

        val first = store.storeShared(byteArrayOf(1, 2, 3))
        val second = store.storeShared(byteArrayOf(4, 5, 6))

        assertTrue(first != null && second != null)
        assertNotEquals(first, second)
    }

    @Test
    fun legacyArtworkMigrationDeduplicatesByContentAndIsIdempotent() {
        val store = store()
        val bytes = byteArrayOf(9, 8, 7, 6)
        val legacyA = store.store("content://a/1", 10L, 20L, bytes)!!
        val legacyB = store.store("content://a/2", 11L, 21L, bytes)!!

        val sharedA = store.migrateToShared(legacyA.toURI().toString())
        val sharedB = store.migrateToShared(legacyB.toURI().toString())

        assertTrue(sharedA != null && sharedA.isFile)
        assertEquals(sharedA, sharedB)
        assertEquals(sharedA, store.migrateToShared(sharedA!!.toURI().toString()))
    }

    @Test
    fun migrationKeepsUriForMissingOrForeignFiles() {
        val store = store()

        assertEquals(null, store.migrateToShared(null))
        assertEquals(null, store.migrateToShared("file:/definitely/missing.img"))
        assertFalse(store.existsUri("file:/definitely/missing.img"))
    }

    @Test
    fun contentCacheKeyIsStableAcrossDirectories() {
        val keyA = AlbumArtStore.contentCacheKey("file:/one/sha-abc.img")
        val keyB = AlbumArtStore.contentCacheKey("file:///two/sha-abc.img")

        assertEquals("sha-abc", keyA)
        assertEquals(keyA, keyB)
    }

    @Test
    fun storeReturnsNullWhenDirectoryCannotBeCreated() {
        // El "directorio" es en realidad un archivo: la escritura debe fallar sin lanzar.
        val blocked = File(temporary.root, "blocked")
        blocked.writeText("no soy un directorio")

        val file = AlbumArtStore(blocked).store(SONG, 100L, 1_000L, byteArrayOf(1))

        assertEquals(null, file)
    }

    private companion object {
        const val SONG = "content://com.android.externalstorage.documents/tree/music/document/music%3Aa.mp3"
    }
}
