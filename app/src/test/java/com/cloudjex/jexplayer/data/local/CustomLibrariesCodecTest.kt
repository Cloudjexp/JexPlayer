package com.cloudjex.jexplayer.data.local

import com.cloudjex.jexplayer.data.model.CustomLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomLibrariesCodecTest {

    private val codec = CustomLibrariesCodec()

    private fun library(
        id: String,
        name: String,
        songIds: List<String> = emptyList()
    ) = CustomLibrary(id = id, name = name, songIds = songIds)

    @Test
    fun roundTripPreservesFieldsAndOrder() {
        val original = listOf(
            library("1", "Primera", listOf("song-a", "song-b")),
            library("2", "Segunda", listOf("song-c"))
        )

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun roundTripSupportsEmptyCollection() {
        assertEquals(emptyList<CustomLibrary>(), codec.decode(codec.encode(emptyList())))
    }

    @Test
    fun roundTripSupportsLibrariesWithNoSongs() {
        val original = listOf(library("1", "Vacía"))

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun roundTripPreservesSpecialCharacters() {
        val original = listOf(
            library(
                id = "content://carpeta/lista\"1\"",
                name = "Rock\n\"clásico\"\\2026",
                songIds = listOf("content://audio/1", "content://audio/2")
            )
        )

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun encodeUsesVersionOneAndExpectedKeys() {
        val encoded = codec.encode(listOf(library("1", "Lista", listOf("a"))))

        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"libraries\""))
        assertTrue(encoded.contains("\"id\""))
        assertTrue(encoded.contains("\"name\""))
        assertTrue(encoded.contains("\"songIds\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsCorruptedJson() {
        codec.decode("""{"version":1,"libraries":[{""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsIncompatibleVersion() {
        codec.decode("""{"version":999,"libraries":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsMissingVersion() {
        codec.decode("""{"libraries":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsMissingLibrariesList() {
        codec.decode("""{"version":1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsMissingId() {
        codec.decode(
            """{"version":1,"libraries":[{"name":"Lista","songIds":[]}]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsMissingName() {
        codec.decode(
            """{"version":1,"libraries":[{"id":"1","songIds":[]}]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsMissingSongIds() {
        codec.decode(
            """{"version":1,"libraries":[{"id":"1","name":"Lista"}]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsInvalidSongIdValue() {
        codec.decode(
            """{"version":1,"libraries":[{"id":"1","name":"Lista","songIds":[1]}]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsDuplicateLibraryIds() {
        codec.decode(
            """
            {"version":1,"libraries":[
              {"id":"1","name":"Primera","songIds":[]},
              {"id":"1","name":"Segunda","songIds":[]}
            ]}
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsBlankLibraryId() {
        codec.decode(
            """{"version":1,"libraries":[{"id":"   ","name":"Lista","songIds":[]}]}"""
        )
    }

    @Test
    fun decodeRemovesDuplicateSongIdsPreservingFirstOccurrence() {
        val decoded = codec.decode(
            """
            {"version":1,"libraries":[
              {"id":"1","name":"Lista","songIds":["a","b","a","c","b"]}
            ]}
            """.trimIndent()
        )

        assertEquals(listOf("a", "b", "c"), decoded.single().songIds)
    }

    @Test
    fun decodeIgnoresUnknownFields() {
        val decoded = codec.decode(
            """
            {"version":1,"libraries":[
              {"id":"1","name":"Lista","songIds":["a"],"createdAt":123,"extra":"ignorado"}
            ],"unknown":true}
            """.trimIndent()
        )

        assertEquals(
            listOf(library("1", "Lista", listOf("a"))),
            decoded
        )
    }
}
