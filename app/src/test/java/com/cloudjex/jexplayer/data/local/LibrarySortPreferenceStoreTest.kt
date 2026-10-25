package com.cloudjex.jexplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cloudjex.jexplayer.data.model.LibrarySortDirection
import com.cloudjex.jexplayer.data.model.LibrarySortField
import com.cloudjex.jexplayer.data.model.LibrarySortSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LibrarySortPreferenceStoreTest {
    private var scope: CoroutineScope? = null

    private fun newStore(file: File): LibrarySortPreferenceStore {
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = storeScope
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = storeScope) { file }
        return LibrarySortPreferenceStore(dataStore)
    }

    @Test
    fun defaultsToTitleAscending() = runBlocking {
        val file = File.createTempFile("library-sort", ".preferences_pb").apply { delete() }
        try {
            assertEquals(LibrarySortSettings(), newStore(file).settings.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun persistsFieldAndDirection() = runBlocking {
        val file = File.createTempFile("library-sort", ".preferences_pb").apply { delete() }
        try {
            val store = newStore(file)
            val expected = LibrarySortSettings(
                field = LibrarySortField.YEAR,
                direction = LibrarySortDirection.DESCENDING
            )

            store.save(expected)

            assertEquals(expected, store.settings.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun unknownStorageValuesUseDefaults() {
        assertEquals(LibrarySortField.TITLE, LibrarySortField.fromStorageKey("unknown"))
        assertEquals(
            LibrarySortDirection.ASCENDING,
            LibrarySortDirection.fromStorageKey("unknown")
        )
    }
}
