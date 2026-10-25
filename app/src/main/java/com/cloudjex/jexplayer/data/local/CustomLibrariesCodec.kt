package com.cloudjex.jexplayer.data.local

import com.cloudjex.jexplayer.data.model.CustomLibrary

/**
 * Serialización de las bibliotecas personalizadas.
 *
 * El formato es versionado y no realiza ninguna operación de E/S. La validación
 * es estructural: no aplica las reglas de nombre de [CustomLibrary].
 */
internal class CustomLibrariesCodec(private val version: Int = CURRENT_VERSION) {

    fun encode(libraries: List<CustomLibrary>): String {
        val builder = StringBuilder()
        builder.append("{\"version\":").append(version).append(",\"libraries\":[")

        libraries.forEachIndexed { index, library ->
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"id\":").append(MiniJson.writeString(library.id)).append(',')
            builder.append("\"name\":").append(MiniJson.writeString(library.name)).append(',')
            builder.append("\"songIds\":[")
            library.songIds.forEachIndexed { songIndex, songId ->
                if (songIndex > 0) builder.append(',')
                builder.append(MiniJson.writeString(songId))
            }
            builder.append("]}")
        }

        builder.append("]}")
        return builder.toString()
    }

    fun decode(raw: String): List<CustomLibrary> {
        val root = MiniJson.parse(raw) as? Map<*, *>
            ?: throw IllegalArgumentException("La raíz de las bibliotecas no es un objeto")

        val fileVersion = (root["version"] as? Long)?.toInt()
            ?: throw IllegalArgumentException("Las bibliotecas no tienen versión")

        require(fileVersion == version) {
            "Versión de bibliotecas incompatible: $fileVersion"
        }

        val libraries = root["libraries"] as? List<*>
            ?: throw IllegalArgumentException("El archivo no contiene la lista de bibliotecas")

        val seenIds = mutableSetOf<String>()

        return libraries.map { entry ->
            val library = entry as? Map<*, *>
                ?: throw IllegalArgumentException("Entrada de biblioteca inválida")

            val id = library.requireString("id")
            require(id.isNotBlank()) {
                "ID de biblioteca vacío"
            }
            val name = library.requireString("name")
            val songIds = library.requireStringList("songIds").distinct()

            require(seenIds.add(id)) {
                "ID de biblioteca duplicado: $id"
            }

            CustomLibrary(
                id = id,
                name = name,
                songIds = songIds
            )
        }
    }

    private fun Map<*, *>.requireString(key: String): String =
        this[key] as? String
            ?: throw IllegalArgumentException("Falta el campo '$key'")

    private fun Map<*, *>.requireStringList(key: String): List<String> {
        val values = this[key] as? List<*>
            ?: throw IllegalArgumentException("Falta la lista '$key'")

        return values.map { value ->
            value as? String
                ?: throw IllegalArgumentException("La lista '$key' contiene un valor inválido")
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
