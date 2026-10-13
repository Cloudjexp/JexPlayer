package com.cloudjex.jexplayer.data.local

import com.cloudjex.jexplayer.data.model.CachedSong

internal class LibraryCacheCodec(private val version: Int = CURRENT_VERSION) {

    fun encode(songs: List<CachedSong>): String {
        val builder = StringBuilder()
        builder.append("{\"version\":").append(version).append(",\"songs\":[")

        songs.forEachIndexed { index, song ->
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"id\":").append(MiniJson.writeString(song.id)).append(',')
            builder.append("\"title\":").append(MiniJson.writeString(song.title)).append(',')
            builder.append("\"artist\":").append(MiniJson.writeString(song.artist)).append(',')
            builder.append("\"album\":").append(MiniJson.writeString(song.album)).append(',')
            builder.append("\"duration\":").append(song.duration).append(',')
            builder.append("\"uri\":").append(MiniJson.writeString(song.uri)).append(',')
            builder.append("\"albumArtUri\":")
                .append(song.albumArtUri?.let(MiniJson::writeString) ?: "null").append(',')
            builder.append("\"size\":").append(song.size?.toString() ?: "null").append(',')
            builder.append("\"lastModified\":")
                .append(song.lastModified?.toString() ?: "null").append(',')
            builder.append("\"albumArtScanned\":")
                .append(song.albumArtScanned?.toString() ?: "null")
            builder.append('}')
        }

        builder.append("]}")
        return builder.toString()
    }

    fun decode(raw: String): List<CachedSong> {
        val root = MiniJson.parse(raw) as? Map<*, *>
            ?: throw IllegalArgumentException("La raíz de la caché no es un objeto")

        val fileVersion = (root["version"] as? Long)?.toInt()
            ?: throw IllegalArgumentException("La caché no tiene versión")

        require(fileVersion == version) { "Versión de caché incompatible: $fileVersion" }

        val songs = root["songs"] as? List<*>
            ?: throw IllegalArgumentException("La caché no contiene lista de canciones")

        return songs.map { entry ->
            val song = entry as? Map<*, *>
                ?: throw IllegalArgumentException("Entrada de canción inválida")
            CachedSong(
                id = song.requireString("id"),
                title = song.requireString("title"),
                artist = song.requireString("artist"),
                album = song.requireString("album"),
                duration = song.requireLong("duration"),
                uri = song.requireString("uri"),
                albumArtUri = song["albumArtUri"] as? String,
                size = song.optionalLong("size"),
                lastModified = song.optionalLong("lastModified"),
                // Ausente en cachés antiguas: se tratará como "sin comprobar".
                albumArtScanned = song["albumArtScanned"] as? Boolean
            )
        }
    }

    private fun Map<*, *>.requireString(key: String): String =
        this[key] as? String ?: throw IllegalArgumentException("Falta el campo '$key'")

    private fun Map<*, *>.requireLong(key: String): Long =
        this[key] as? Long ?: throw IllegalArgumentException("Falta el campo numérico '$key'")

    private fun Map<*, *>.optionalLong(key: String): Long? = when (val value = this[key]) {
        null -> null
        is Long -> value
        else -> throw IllegalArgumentException("Campo '$key' inválido")
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
