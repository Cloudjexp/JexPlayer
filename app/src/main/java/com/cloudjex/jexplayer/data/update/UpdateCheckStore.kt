package com.cloudjex.jexplayer.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal interface UpdateCheckStore {
    suspend fun lastSuccessfulCheck(): Long?
    suspend fun lastAttempt(): Long?
    suspend fun dismissedVersion(): String?
    suspend fun saveSuccess(timestamp: Long)
    suspend fun saveAttempt(timestamp: Long)
    suspend fun dismiss(version: String)
}

internal class DataStoreUpdateCheckStore(private val dataStore: DataStore<Preferences>) : UpdateCheckStore {
    override suspend fun lastSuccessfulCheck() = dataStore.data.first()[LAST_SUCCESS]
    override suspend fun lastAttempt() = dataStore.data.first()[LAST_ATTEMPT]
    override suspend fun dismissedVersion() = dataStore.data.first()[DISMISSED]
    override suspend fun saveSuccess(timestamp: Long) { dataStore.edit { it[LAST_SUCCESS] = timestamp } }
    override suspend fun saveAttempt(timestamp: Long) { dataStore.edit { it[LAST_ATTEMPT] = timestamp } }
    override suspend fun dismiss(version: String) { dataStore.edit { it[DISMISSED] = version } }

    private companion object {
        val LAST_SUCCESS = longPreferencesKey("last_successful_check")
        val LAST_ATTEMPT = longPreferencesKey("last_attempt")
        val DISMISSED = stringPreferencesKey("dismissed_version")
    }
}

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "jexplayer_updates")

internal fun updateCheckStore(context: Context): UpdateCheckStore =
    DataStoreUpdateCheckStore(context.applicationContext.updateDataStore)

internal const val UPDATE_INTERVAL_MS = 72L * 60 * 60 * 1000
internal const val RETRY_INTERVAL_MS = 30L * 60 * 1000
