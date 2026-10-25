package com.cloudjex.jexplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomLibraryRulesTest {

    private fun library(
        id: String,
        name: String,
        songIds: List<String> = emptyList()
    ) = CustomLibrary(id = id, name = name, songIds = songIds)

    private fun updated(result: CustomLibraryEditResult): List<CustomLibrary> {
        assertTrue(
            "Se esperaba Updated, se obtuvo $result",
            result is CustomLibraryEditResult.Updated
        )
        return (result as CustomLibraryEditResult.Updated).libraries
    }

    private fun assertInvalidName(
        result: CustomLibraryEditResult,
        expected: CustomLibraryNameError
    ) {
        assertTrue(
            "Se esperaba InvalidName, se obtuvo $result",
            result is CustomLibraryEditResult.InvalidName
        )
        assertEquals(
            expected,
            (result as CustomLibraryEditResult.InvalidName).error
        )
    }

    @Test
    fun createTrimsNameAndAppendsAtTheEnd() {
        val original = listOf(library("1", "Primera"))

        val result = CustomLibraryRules.create(original, id = "2", rawName = "  Segunda  ")
        val libraries = updated(result)

        assertEquals(listOf("Primera", "Segunda"), libraries.map { it.name })
        assertEquals(listOf("1", "2"), libraries.map { it.id })
        assertEquals(emptyList<String>(), libraries[1].songIds)
        assertEquals(1, original.size)
    }

    @Test
    fun createRejectsBlankName() {
        assertInvalidName(
            CustomLibraryRules.create(emptyList(), "1", "   "),
            CustomLibraryNameError.Blank
        )
    }

    @Test
    fun createRejectsTooLongName() {
        assertInvalidName(
            CustomLibraryRules.create(emptyList(), "1", "a".repeat(81)),
            CustomLibraryNameError.TooLong
        )
    }

    @Test
    fun createAcceptsNameAtTheLimit() {
        val libraries = updated(
            CustomLibraryRules.create(emptyList(), "1", "a".repeat(80))
        )

        assertEquals(80, libraries.single().name.length)
    }

    @Test
    fun createRejectsReservedNameIgnoringCase() {
        assertInvalidName(
            CustomLibraryRules.create(emptyList(), "1", "  favoritos "),
            CustomLibraryNameError.Reserved
        )
    }

    @Test
    fun createRejectsDuplicateNameIgnoringCaseAndSpaces() {
        val original = listOf(library("1", "Rock"))

        assertInvalidName(
            CustomLibraryRules.create(original, "2", "  rock  "),
            CustomLibraryNameError.Duplicate
        )
    }

    @Test
    fun nameErrorReturnsNullForValidName() {
        assertNull(CustomLibraryRules.nameError(emptyList(), "Para entrenar"))
    }

    @Test
    fun nameErrorAllowsTheSameLibraryWhenExcludingIt() {
        val original = listOf(library("1", "Rock"))

        assertNull(
            CustomLibraryRules.nameError(original, "rock", excludingId = "1")
        )
    }

    @Test
    fun renameUpdatesOnlyTheTargetAndPreservesSongsAndOrder() {
        val original = listOf(
            library("1", "Primera", listOf("a", "b")),
            library("2", "Segunda", listOf("c"))
        )

        val libraries = updated(
            CustomLibraryRules.rename(original, libraryId = "1", rawName = "  Nueva  ")
        )

        assertEquals(listOf("Nueva", "Segunda"), libraries.map { it.name })
        assertEquals(listOf("a", "b"), libraries[0].songIds)
        assertEquals(listOf("c"), libraries[1].songIds)
        assertEquals(listOf("a", "b"), original[0].songIds)
    }

    @Test
    fun renameRejectsDuplicateNameFromAnotherLibrary() {
        val original = listOf(
            library("1", "Rock"),
            library("2", "Jazz")
        )

        assertInvalidName(
            CustomLibraryRules.rename(original, "2", "rock"),
            CustomLibraryNameError.Duplicate
        )
    }

    @Test
    fun renameAllowsChangingOnlyTheCaseOfTheSameName() {
        val original = listOf(library("1", "Rock"))

        val libraries = updated(
            CustomLibraryRules.rename(original, "1", "rock")
        )

        assertEquals("rock", libraries.single().name)
    }

    @Test
    fun renameReturnsNotFoundForUnknownLibrary() {
        assertEquals(
            CustomLibraryEditResult.NotFound,
            CustomLibraryRules.rename(emptyList(), "missing", "Nueva")
        )
    }

    @Test
    fun deleteRemovesOnlyTheTargetLibrary() {
        val original = listOf(
            library("1", "Primera", listOf("a")),
            library("2", "Segunda", listOf("b"))
        )

        val libraries = updated(
            CustomLibraryRules.delete(original, "1")
        )

        assertEquals(listOf("2"), libraries.map { it.id })
        assertEquals(2, original.size)
    }

    @Test
    fun deleteReturnsNotFoundForUnknownLibrary() {
        assertEquals(
            CustomLibraryEditResult.NotFound,
            CustomLibraryRules.delete(emptyList(), "missing")
        )
    }

    @Test
    fun addSongAppendsToTheEndAndPreservesExistingOrder() {
        val original = listOf(library("1", "Lista", listOf("a", "b")))

        val libraries = updated(
            CustomLibraryRules.addSong(original, "1", "c")
        )

        assertEquals(listOf("a", "b", "c"), libraries.single().songIds)
        assertEquals(listOf("a", "b"), original.single().songIds)
    }

    @Test
    fun addSongIsIdempotent() {
        val original = listOf(library("1", "Lista", listOf("a", "b")))

        val libraries = updated(
            CustomLibraryRules.addSong(original, "1", "b")
        )

        assertEquals(listOf("a", "b"), libraries.single().songIds)
    }

    @Test
    fun addSongReturnsNotFoundForUnknownLibrary() {
        assertEquals(
            CustomLibraryEditResult.NotFound,
            CustomLibraryRules.addSong(emptyList(), "missing", "a")
        )
    }

    @Test
    fun removeSongPreservesTheOrderOfTheRemainingSongs() {
        val original = listOf(library("1", "Lista", listOf("a", "b", "c")))

        val libraries = updated(
            CustomLibraryRules.removeSong(original, "1", "b")
        )

        assertEquals(listOf("a", "c"), libraries.single().songIds)
    }

    @Test
    fun removeSongMissingIsANoOp() {
        val original = listOf(library("1", "Lista", listOf("a")))

        val libraries = updated(
            CustomLibraryRules.removeSong(original, "1", "missing")
        )

        assertEquals(listOf("a"), libraries.single().songIds)
    }

    @Test
    fun removeSongReturnsNotFoundForUnknownLibrary() {
        assertEquals(
            CustomLibraryEditResult.NotFound,
            CustomLibraryRules.removeSong(emptyList(), "missing", "a")
        )
    }

    @Test
    fun toggleSongAddsThenRemoves() {
        val original = listOf(library("1", "Lista", listOf("a")))

        val added = updated(CustomLibraryRules.toggleSong(original, "1", "b"))
        assertEquals(listOf("a", "b"), added.single().songIds)

        val removed = updated(CustomLibraryRules.toggleSong(added, "1", "a"))
        assertEquals(listOf("b"), removed.single().songIds)
    }

    @Test
    fun toggleSongReturnsNotFoundForUnknownLibrary() {
        assertEquals(
            CustomLibraryEditResult.NotFound,
            CustomLibraryRules.toggleSong(emptyList(), "missing", "a")
        )
    }

    @Test
    fun oneSongCanBelongToMultipleLibrariesWithoutCrossContamination() {
        val original = listOf(
            library("1", "Primera"),
            library("2", "Segunda")
        )

        val withFirst = updated(CustomLibraryRules.addSong(original, "1", "song"))
        val withBoth = updated(CustomLibraryRules.addSong(withFirst, "2", "song"))

        assertEquals(listOf("song"), withBoth[0].songIds)
        assertEquals(listOf("song"), withBoth[1].songIds)

        val withoutFirst = updated(CustomLibraryRules.removeSong(withBoth, "1", "song"))

        assertEquals(emptyList<String>(), withoutFirst[0].songIds)
        assertEquals(listOf("song"), withoutFirst[1].songIds)
    }

    @Test
    fun emptyLibraryCanBeCreatedAndIsValid() {
        val libraries = updated(
            CustomLibraryRules.create(emptyList(), "1", "Vacía")
        )

        assertEquals(emptyList<String>(), libraries.single().songIds)
    }
}
