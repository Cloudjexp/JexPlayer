package com.cloudjex.jexplayer.data.model

/**
 * Colección manual de canciones creada por el usuario.
 *
 * [songIds] conserva el orden de visualización. Los IDs pueden no existir en la
 * biblioteca actual; se filtran al construir la vista y la cola, pero no se
 * eliminan de la lista.
 */
data class CustomLibrary(
    val id: String,
    val name: String,
    val songIds: List<String>
)

/** Errores posibles al validar el nombre de una biblioteca personalizada. */
enum class CustomLibraryNameError {
    Blank,
    TooLong,
    Reserved,
    Duplicate
}

/** Resultado de aplicar una regla de edición sobre la colección de bibliotecas. */
sealed interface CustomLibraryEditResult {
    data class Updated(val libraries: List<CustomLibrary>) : CustomLibraryEditResult

    data class InvalidName(val error: CustomLibraryNameError) : CustomLibraryEditResult

    data object NotFound : CustomLibraryEditResult
}

/**
 * Reglas puras de las bibliotecas personalizadas. No dependen de Android ni
 * realizan E/S, de modo que se pueden probar en JVM.
 *
 * El llamador es responsable de generar un [CustomLibrary.id] único y estable.
 */
object CustomLibraryRules {

    const val MAX_NAME_LENGTH = 80

    private const val RESERVED_NAME = "Favoritos"

    fun normalizeName(rawName: String): String = rawName.trim()

    fun nameError(
        libraries: List<CustomLibrary>,
        rawName: String,
        excludingId: String? = null
    ): CustomLibraryNameError? {
        val name = normalizeName(rawName)

        return when {
            name.isEmpty() -> CustomLibraryNameError.Blank
            name.length > MAX_NAME_LENGTH -> CustomLibraryNameError.TooLong
            name.equals(RESERVED_NAME, ignoreCase = true) -> CustomLibraryNameError.Reserved
            libraries.any { library ->
                library.id != excludingId && library.name.equals(name, ignoreCase = true)
            } -> CustomLibraryNameError.Duplicate
            else -> null
        }
    }

    fun create(
        libraries: List<CustomLibrary>,
        id: String,
        rawName: String
    ): CustomLibraryEditResult {
        nameError(libraries, rawName)?.let {
            return CustomLibraryEditResult.InvalidName(it)
        }

        val library = CustomLibrary(
            id = id,
            name = normalizeName(rawName),
            songIds = emptyList()
        )
        return CustomLibraryEditResult.Updated(libraries + library)
    }

    /**
     * Crea una biblioteca y la deja ya conteniendo [songId] en una sola
     * operación. Si el nombre no es válido no se crea ninguna lista vacía.
     *
     * El llamador debe proporcionar un [songId] no vacío.
     */
    fun createWithSong(
        libraries: List<CustomLibrary>,
        id: String,
        rawName: String,
        songId: String
    ): CustomLibraryEditResult {
        val created = create(libraries, id, rawName)
        if (created !is CustomLibraryEditResult.Updated) return created

        return addSong(created.libraries, id, songId)
    }

    fun rename(
        libraries: List<CustomLibrary>,
        libraryId: String,
        rawName: String
    ): CustomLibraryEditResult {
        val index = libraries.indexOfFirst { it.id == libraryId }
        if (index == -1) return CustomLibraryEditResult.NotFound

        nameError(libraries, rawName, excludingId = libraryId)?.let {
            return CustomLibraryEditResult.InvalidName(it)
        }

        val name = normalizeName(rawName)
        return updateAt(libraries, index) { it.copy(name = name) }
    }

    fun delete(
        libraries: List<CustomLibrary>,
        libraryId: String
    ): CustomLibraryEditResult {
        if (libraries.none { it.id == libraryId }) {
            return CustomLibraryEditResult.NotFound
        }

        return CustomLibraryEditResult.Updated(
            libraries.filterNot { it.id == libraryId }
        )
    }

    fun addSong(
        libraries: List<CustomLibrary>,
        libraryId: String,
        songId: String
    ): CustomLibraryEditResult {
        val index = libraries.indexOfFirst { it.id == libraryId }
        if (index == -1) return CustomLibraryEditResult.NotFound

        val library = libraries[index]
        if (songId.isBlank() || songId in library.songIds) {
            return CustomLibraryEditResult.Updated(libraries)
        }

        return updateAt(libraries, index) {
            it.copy(songIds = it.songIds + songId)
        }
    }

    fun removeSong(
        libraries: List<CustomLibrary>,
        libraryId: String,
        songId: String
    ): CustomLibraryEditResult {
        val index = libraries.indexOfFirst { it.id == libraryId }
        if (index == -1) return CustomLibraryEditResult.NotFound

        val library = libraries[index]
        if (songId !in library.songIds) {
            return CustomLibraryEditResult.Updated(libraries)
        }

        return updateAt(libraries, index) {
            it.copy(songIds = it.songIds.filterNot { current -> current == songId })
        }
    }

    fun toggleSong(
        libraries: List<CustomLibrary>,
        libraryId: String,
        songId: String
    ): CustomLibraryEditResult {
        val index = libraries.indexOfFirst { it.id == libraryId }
        if (index == -1) return CustomLibraryEditResult.NotFound
        if (songId.isBlank()) return CustomLibraryEditResult.Updated(libraries)

        val library = libraries[index]
        return if (songId in library.songIds) {
            updateAt(libraries, index) {
                it.copy(songIds = it.songIds.filterNot { current -> current == songId })
            }
        } else {
            updateAt(libraries, index) {
                it.copy(songIds = it.songIds + songId)
            }
        }
    }

    private inline fun updateAt(
        libraries: List<CustomLibrary>,
        index: Int,
        transform: (CustomLibrary) -> CustomLibrary
    ): CustomLibraryEditResult {
        val current = libraries[index]
        val updated = transform(current)
        if (updated == current) {
            return CustomLibraryEditResult.Updated(libraries)
        }

        return CustomLibraryEditResult.Updated(
            libraries.mapIndexed { currentIndex, library ->
                if (currentIndex == index) updated else library
            }
        )
    }
}
