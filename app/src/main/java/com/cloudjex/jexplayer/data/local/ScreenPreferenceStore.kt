package com.cloudjex.jexplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistencia de la última pantalla visitada con DataStore Preferences.
 * `true` = reproductor completo; `false` (valor por defecto) = biblioteca.
 */
internal class ScreenPreferenceStore(private val dataStore: DataStore<Preferences>) {

    val lastScreen: Flow<Boolean> = dataStore.data.map { it[SHOW_PLAYER] ?: false }

    suspend fun save(showPlayer: Boolean) {
        dataStore.edit { it[SHOW_PLAYER] = showPlayer }
    }

    companion object {
        private val SHOW_PLAYER = booleanPreferencesKey("show_player_screen")

        fun from(context: Context): ScreenPreferenceStore =
            ScreenPreferenceStore(context.applicationContext.screenDataStore)
    }
}

private val Context.screenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jexplayer_screen"
)
