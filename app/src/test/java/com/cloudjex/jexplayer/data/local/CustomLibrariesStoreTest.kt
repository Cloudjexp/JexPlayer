package com.cloudjex.jexplayer.data.local

import com.cloudjex.jexplayer.data.model.CustomLibrary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CustomLibrariesStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun libraryFile() = File(temporaryFolder.root, "custom_libraries.json")

    private fun backupFile() = File(temporaryFolder.root, "custom_libraries.corrupt.json")

    private fun store(file: File = libraryFile()) = CustomLibrariesStore(file)

    private fun library(
        id: String,
        name: String,
        songIds: List<String> = emptyList()
    ) = CustomLibrary(id = id, name = name, songIds = songIds)

    @Test
    fun loadReturnsEmptyWhenFileDoesNotExist() = runBlocking {
        val result = store().load()

        assertEquals(emptyList<CustomLibrary>(), result.libraries)
        assertFalse(result.corrupted)
    }

    @Test
    fun saveThenLoadRoundTrip() = runBlocking {
        val original = listOf(
            library("1", "Primera", listOf("a", "b")),
            library("2", "Segunda", listOf("c"))
        )
        val store = store()

        assertTrue(store.save(original))

        val result = store.load()
        assertFalse(result.corrupted)
        assertEquals(original, result.libraries)
    }

    @Test
    fun saveAndLoadSupportsEmptyCollection() = runBlocking {
        val store = store()

        assertTrue(store.save(emptyList()))

        val result = store.load()
        assertFalse(result.corrupted)
        assertEquals(emptyList<CustomLibrary>(), result.libraries)
    }

    @Test
    fun saveCreatesParentDirectories() = runBlocking {
        val file = File(temporaryFolder.root, "nested/dir/custom_libraries.json")
        val store = store(file)

        assertTrue(store.save(listOf(library("1", "Lista"))))
        assertTrue(file.exists())
    }

    @Test
    fun saveLeavesNoTemporaryFile() = runBlocking {
        val file = libraryFile()
        val store = store(file)

        assertTrue(store.save(listOf(library("1", "Lista"))))

        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
    }

    @Test
    fun saveReturnsFalseWhenParentCannotBeCreated() = runBlocking {
        val parentFile = File(temporaryFolder.root, "not-a-directory").apply {
            writeText("contenido")
        }
        val store = store(File(parentFile, "custom_libraries.json"))

        assertFalse(store.save(listOf(library("1", "Lista"))))
    }

    @Test
    fun loadTreatsCorruptedFileAsCorruptedAndKeepsBackup() = runBlocking {
        val file = libraryFile()
        val corruptedContent = "{ esto no es json"
        file.writeText(corruptedContent)

        val result = store(file).load()

        assertEquals(emptyList<CustomLibrary>(), result.libraries)
        assertTrue(result.corrupted)
        assertTrue(file.exists())
        assertTrue(backupFile().exists())
        assertEquals(corruptedContent, backupFile().readText())
    }

    @Test
    fun loadDoesNotOverwriteAnExistingBackup() = runBlocking {
        val file = libraryFile()
        val firstCorruption = "{ primera corrupción"
        val secondCorruption = "{ segunda corrupción"

        file.writeText(firstCorruption)
        store(file).load()
        assertEquals(firstCorruption, backupFile().readText())

        file.writeText(secondCorruption)
        store(file).load()

        assertEquals(firstCorruption, backupFile().readText())
    }

    @Test
    fun loadTreatsIncompatibleVersionAsCorrupted() = runBlocking {
        val file = libraryFile()
        file.writeText("""{"version":999,"libraries":[]}""")

        val result = store(file).load()

        assertEquals(emptyList<CustomLibrary>(), result.libraries)
        assertTrue(result.corrupted)
        assertTrue(backupFile().exists())
    }

    @Test
    fun loadTreatsEmptyFileAsCorrupted() = runBlocking {
        val file = libraryFile()
        file.writeText("")

        val result = store(file).load()

        assertEquals(emptyList<CustomLibrary>(), result.libraries)
        assertTrue(result.corrupted)
        assertTrue(backupFile().exists())
    }

    @Test
    fun loadValidFileDoesNotCreateBackup() = runBlocking {
        val store = store()
        assertTrue(store.save(listOf(library("1", "Lista"))))

        val result = store.load()

        assertFalse(result.corrupted)
        assertFalse(backupFile().exists())
    }

    @Test
    fun loadValidFileKeepsAnExistingBackup() = runBlocking {
        val file = libraryFile()
        file.writeText("{ corrupción anterior")
        store(file).load()
        assertTrue(backupFile().exists())

        assertTrue(store(file).save(listOf(library("1", "Lista"))))
        val result = store(file).load()

        assertFalse(result.corrupted)
        assertEquals(listOf(library("1", "Lista")), result.libraries)
        assertTrue(backupFile().exists())
    }
}
