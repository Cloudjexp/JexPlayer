package com.cloudjex.jexplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudjex.jexplayer.data.model.LibrarySortDirection
import com.cloudjex.jexplayer.data.model.LibrarySortField
import com.cloudjex.jexplayer.data.model.LibrarySortSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class LibrarySortPreferenceStore(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<LibrarySortSettings> = dataStore.data.map { preferences ->
        LibrarySortSettings(
            field = LibrarySortField.fromStorageKey(preferences[FIELD_KEY]),
            direction = LibrarySortDirection.fromStorageKey(preferences[DIRECTION_KEY])
        )
    }

    suspend fun save(settings: LibrarySortSettings) {
        dataStore.edit { preferences ->
            preferences[FIELD_KEY] = settings.field.storageKey
            preferences[DIRECTION_KEY] = settings.direction.storageKey
        }
    }

    companion object {
        private val FIELD_KEY = stringPreferencesKey("sort_field")
        private val DIRECTION_KEY = stringPreferencesKey("sort_direction")

        fun from(context: Context): LibrarySortPreferenceStore =
            LibrarySortPreferenceStore(context.applicationContext.librarySortDataStore)
    }
}

private val Context.librarySortDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jexplayer_library_sort"
)
