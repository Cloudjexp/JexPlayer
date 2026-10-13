package com.cloudjex.jexplayer.data.repository

import android.content.Context
import com.cloudjex.jexplayer.data.model.MusicFolder
import org.json.JSONArray
import org.json.JSONObject


class FolderRepository(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getFolders(): List<MusicFolder> {
        val raw = preferences.getString(KEY_FOLDERS, null) ?: return emptyList()

        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString(KEY_JSON_URI)
                if (uri.isBlank()) {
                    null
                } else {
                    MusicFolder(
                        uri = uri,
                        name = item.optString(KEY_JSON_NAME).ifBlank { uri }
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addFolder(uri: String, name: String): Boolean {
        val folders = getFolders()

        if (folders.any { it.uri == uri }) {
            return false
        }

        save(folders + MusicFolder(uri = uri, name = name))
        return true
    }

    fun removeFolder(uri: String) {
        save(getFolders().filterNot { it.uri == uri })
    }

    private fun save(folders: List<MusicFolder>) {
        val json = JSONArray()
        folders.forEach { folder ->
            json.put(
                JSONObject()
                    .put(KEY_JSON_URI, folder.uri)
                    .put(KEY_JSON_NAME, folder.name)
            )
        }

        preferences.edit().putString(KEY_FOLDERS, json.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "jexplayer_folders"
        const val KEY_FOLDERS = "selected_folders"
        const val KEY_JSON_URI = "uri"
        const val KEY_JSON_NAME = "name"
    }
}
