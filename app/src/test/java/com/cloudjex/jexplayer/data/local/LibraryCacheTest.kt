package com.cloudjex.jexplayer.data.local

import com.cloudjex.jexplayer.data.model.CachedSong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryCacheTest {

    private val codec = LibraryCacheCodec()

    private fun song(
        id: String,
        size: Long? = 100L,
        lastModified: Long? = 1_000L,
        albumArtUri: String? = null,
        albumArtScanned: Boolean? = null
    ) = CachedSong(
        id = id,
        title = "Título \"especial\"\ncon salto",
        artist = "Artista",
        album = "Álbum",
        duration = 12_345L,
        uri = "$id/uri",
        albumArtUri = albumArtUri,
        size = size,
        lastModified = lastModified,
        albumArtScanned = albumArtScanned
    )

    @Test
    fun codecRoundTripPreservesFields() {
        val original = listOf(
            song("content://a/1"),
            song("content://a/2").copy(albumArtUri = "content://art/2")
        )

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun codecRoundTripPreservesAlbumArtScanState() {
        val original = listOf(
            song("content://a/1", albumArtUri = "file:///art/1.img", albumArtScanned = true),
            song("content://a/2", albumArtScanned = true),
            song("content://a/3", albumArtScanned = null)
        )

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun decodeTreatsOldEntriesWithoutScanFieldAsUnchecked() {
        // JSON tal y como lo escribían las versiones anteriores (sin albumArtScanned).
        val legacy = """
            {"version":1,"songs":[
              {"id":"content://a/1","title":"t","artist":"a","album":"b",
               "duration":10,"uri":"content://a/1","albumArtUri":null,
               "size":100,"lastModified":1000},
              {"id":"content://a/2","title":"t","artist":"a","album":"b",
               "duration":10,"uri":"content://a/2","albumArtUri":"file:///art/2.img",
               "size":100,"lastModified":1000}
            ]}
        """.trimIndent()

        val decoded = codec.decode(legacy)

        assertEquals(listOf(null, null), decoded.map { it.albumArtScanned })
        assertEquals("file:///art/2.img", decoded[1].albumArtUri)
    }

    @Test
    fun codecSupportsNullValidationFields() {
        val original = listOf(song("content://a/1", size = null, lastModified = null))

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun codecRejectsIncompatibleVersion() {
        codec.decode("""{"version":999,"songs":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun codecRejectsCorruptedJson() {
        codec.decode("""{"version":1,"songs":[{""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun codecRejectsMissingRequiredField() {
        codec.decode("""{"version":1,"songs":[{"id":"solo-id"}]}""")
    }

    @Test
    fun loadReturnsEmptyWhenFileMissing() = runBlocking {
        val file = File.createTempFile("library", ".json")
        file.delete()
        val cache = LibraryCache(file)

        assertEquals(emptyList<CachedSong>(), cache.load())
    }

    @Test
    fun loadReturnsEmptyWhenFileCorrupted() = runBlocking {
        val file = File.createTempFile("library", ".json")
        file.writeText("{ esto no es json")
        val cache = LibraryCache(file)

        assertEquals(emptyList<CachedSong>(), cache.load())
    }

    @Test
    fun saveThenLoadRoundTrip() = runBlocking {
        val file = File.createTempFile("library", ".json")
        file.delete()
        val cache = LibraryCache(file)
        val songs = listOf(song("content://a/1"), song("content://a/2"))

        cache.save(songs)

        assertTrue(file.exists())
        assertEquals(songs, cache.load())
    }
}
