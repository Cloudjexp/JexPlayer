package com.cloudjex.jexplayer.data.local

import android.content.Context
import android.net.Uri
import com.cloudjex.jexplayer.data.model.Song
import org.json.JSONObject

/** Persiste el estado visual mínimo necesario para reconstruir el mini reproductor. */
class LastSongStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    data class State(val song: Song, val position: Long)

    fun load(): State? = runCatching {
        val json = JSONObject(preferences.getString(KEY_SONG, null) ?: return null)
        val id = json.optString(KEY_ID)
        val uri = json.optString(KEY_URI)
        if (id.isBlank() || uri.isBlank()) return null
        State(
            Song(
                id = id,
                title = json.optString(KEY_TITLE).ifBlank { "Sin título" },
                artist = json.optString(KEY_ARTIST),
                album = json.optString(KEY_ALBUM),
                duration = json.optLong(KEY_DURATION, 0L).coerceAtLeast(0L),
                uri = Uri.parse(uri),
                albumArtUri = json.optString(KEY_ARTWORK).takeIf { it.isNotBlank() }?.let(Uri::parse)
            ),
            json.optLong(KEY_POSITION, 0L).coerceAtLeast(0L)
        )
    }.getOrNull()

    fun save(song: Song, position: Long) {
        preferences.edit().putString(
            KEY_SONG,
            JSONObject()
                .put(KEY_ID, song.id)
                .put(KEY_TITLE, song.title)
                .put(KEY_ARTIST, song.artist)
                .put(KEY_ALBUM, song.album)
                .put(KEY_DURATION, song.duration)
                .put(KEY_URI, song.uri.toString())
                .put(KEY_ARTWORK, song.albumArtUri?.toString() ?: "")
                .put(KEY_POSITION, position.coerceAtLeast(0L))
                .toString()
        ).apply()
    }

    fun clear() = preferences.edit().remove(KEY_SONG).apply()

    private companion object {
        const val NAME = "jexplayer_last_song"
        const val KEY_SONG = "song"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_DURATION = "duration"
        const val KEY_URI = "uri"
        const val KEY_ARTWORK = "artwork"
        const val KEY_POSITION = "position"
    }
}
