package com.cloudjex.jexplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persistencia de la apariencia elegida, independiente de la sesión de reproducción. */
internal class AppearancePreferenceStore(
    private val dataStore: DataStore<Preferences>
) {
    val selectedTheme: Flow<AppearanceTheme> = dataStore.data.map { preferences ->
        AppearanceTheme.fromStorageKey(preferences[THEME_KEY])
    }

    suspend fun saveTheme(theme: AppearanceTheme) {
        dataStore.edit { preferences -> preferences[THEME_KEY] = theme.storageKey }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("appearance_theme")

        fun from(context: Context): AppearancePreferenceStore =
            AppearancePreferenceStore(context.applicationContext.appearanceDataStore)
    }
}

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jexplayer_appearance"
)
