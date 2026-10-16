package com.cloudjex.jexplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ScreenPreferenceStoreTest {

    private var scope: CoroutineScope? = null

    private fun newStore(file: File): ScreenPreferenceStore {
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = storeScope
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = storeScope) { file }
        return ScreenPreferenceStore(dataStore)
    }

    @Test
    fun defaultsToLibraryWhenNothingSaved() = runBlocking {
        val file = File.createTempFile("screen", ".preferences_pb").apply { delete() }
        try {
            assertEquals(false, newStore(file).lastScreen.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun persistsPlayerAndLibrarySelections() = runBlocking {
        val file = File.createTempFile("screen", ".preferences_pb").apply { delete() }
        try {
            val store = newStore(file)
            store.save(true)
            assertEquals(true, store.lastScreen.first())
            store.save(false)
            assertEquals(false, store.lastScreen.first())
        } finally {
            scope?.cancel()
        }
    }
}
