package com.cloudjex.jexplayer.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.AppearanceAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AppearancePreferenceStoreTest {
    private var scope: CoroutineScope? = null

    private fun newStore(file: File): AppearancePreferenceStore {
        val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = storeScope
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = storeScope) { file }
        return AppearancePreferenceStore(dataStore)
    }

    @Test
    fun defaultsToDark() = runBlocking {
        val file = File.createTempFile("appearance", ".preferences_pb").apply { delete() }
        try {
            assertEquals(AppearanceTheme.DARK, newStore(file).selectedTheme.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun persistsSelectedTheme() = runBlocking {
        val file = File.createTempFile("appearance", ".preferences_pb").apply { delete() }
        try {
            val store = newStore(file)
            store.saveTheme(AppearanceTheme.OCEAN_BLUE)
            assertEquals(AppearanceTheme.OCEAN_BLUE, store.selectedTheme.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun defaultsToAmberAccentAndPersistsSelectedAccent() = runBlocking {
        val file = File.createTempFile("appearance", ".preferences_pb").apply { delete() }
        try {
            val store = newStore(file)
            assertEquals(AppearanceAccent.AMBER, store.selectedAccent.first())
            store.saveAccent(AppearanceAccent.OCEAN_BLUE)
            assertEquals(AppearanceAccent.OCEAN_BLUE, store.selectedAccent.first())
        } finally {
            scope?.cancel()
        }
    }

    @Test
    fun fallsBackToDarkForUnknownThemeKey() {
        assertEquals(AppearanceTheme.DARK, AppearanceTheme.fromStorageKey("unknown"))
    }

    @Test
    fun migratesLegacyThemeKeysToDark() {
        assertEquals(AppearanceTheme.DARK, AppearanceTheme.fromStorageKey("minimalist_gray"))
        assertEquals(AppearanceTheme.DARK, AppearanceTheme.fromStorageKey("amoled_black"))
    }

    @Test
    fun everyThemeStorageKeyRoundTrips() {
        AppearanceTheme.entries.forEach { theme ->
            assertEquals(theme, AppearanceTheme.fromStorageKey(theme.storageKey))
        }
    }
}
