
package com.cloudjex.jexplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FavoritesStore(
    private val dataStore: DataStore<Preferences>
) {

    val favoriteIds: Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[FAVORITES_KEY] ?: emptySet()
        }

    suspend fun toggleFavorite(songId: String) {
        dataStore.edit { preferences ->
            val currentFavorites =
                preferences[FAVORITES_KEY] ?: emptySet()

            preferences[FAVORITES_KEY] =
                if (songId in currentFavorites) {
                    currentFavorites - songId
                } else {
                    currentFavorites + songId
                }
        }
    }

    companion object {
        private val FAVORITES_KEY =
            stringSetPreferencesKey("favorite_song_ids")

        fun from(context: Context): FavoritesStore =
            FavoritesStore(
                context.applicationContext.favoritesDataStore
            )
    }
}

private val Context.favoritesDataStore: DataStore<Preferences>
        by preferencesDataStore(
            name = "jexplayer_favorites"
        )
