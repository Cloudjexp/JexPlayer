package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.model.Song
import kotlin.random.Random

/** Lista que dio origen a la reproducción. La cola no debe depender de la pantalla visible. */
sealed interface PlaybackContext {
    data object Library : PlaybackContext
    data object Favorites : PlaybackContext

    /** Biblioteca personalizada identificada por su ID persistido. */
    data class Custom(val libraryId: String) : PlaybackContext

    companion object {
        const val METADATA_CONTEXT_TYPE =
            "com.cloudjex.jexplayer.playback.CONTEXT_TYPE"
        const val METADATA_LIBRARY_ID =
            "com.cloudjex.jexplayer.playback.LIBRARY_ID"

        private const val TYPE_LIBRARY = "library"
        private const val TYPE_FAVORITES = "favorites"
        private const val TYPE_CUSTOM = "custom"

        fun metadataType(context: PlaybackContext): String = when (context) {
            PlaybackContext.Library -> TYPE_LIBRARY
            PlaybackContext.Favorites -> TYPE_FAVORITES
            is PlaybackContext.Custom -> TYPE_CUSTOM
        }

        fun metadataLibraryId(context: PlaybackContext): String? =
            (context as? PlaybackContext.Custom)?.libraryId

        fun fromMetadata(type: String?, libraryId: String?): PlaybackContext? = when (type) {
            TYPE_LIBRARY -> PlaybackContext.Library
            TYPE_FAVORITES -> PlaybackContext.Favorites
            TYPE_CUSTOM -> libraryId
                ?.takeIf { it.isNotBlank() }
                ?.let { PlaybackContext.Custom(it) }
            else -> null
        }
    }
}

internal object PlaybackQueue {
    fun songsFor(
        context: PlaybackContext,
        library: List<Song>,
        favoriteIds: Set<String>,
        customLibrarySongIds: List<String>
    ): List<Song> {
        val byId = library.associateBy { it.id }
        return idsFor(
            context = context,
            libraryIds = library.map(Song::id),
            favoriteIds = favoriteIds,
            customLibrarySongIds = customLibrarySongIds
        ).mapNotNull(byId::get)
    }

    internal fun idsFor(
        context: PlaybackContext,
        libraryIds: List<String>,
        favoriteIds: Set<String>,
        customLibrarySongIds: List<String>
    ): List<String> = when (context) {
        PlaybackContext.Library -> libraryIds
        PlaybackContext.Favorites -> libraryIds.filter { it in favoriteIds }
        is PlaybackContext.Custom -> {
            val available = libraryIds.toSet()
            customLibrarySongIds.filter { it in available }
        }
    }

    /**
     * Índice que debe quedar seleccionado después de cambiar la cola.
     *
     * Si la canción actual sigue presente se conserva. Si desapareció se usa el
     * índice anterior más cercano. Devuelve -1 solo para una cola vacía.
     */
    internal fun resolveIndexAfterChange(
        ids: List<String>,
        currentId: String?,
        previousIndex: Int
    ): Int {
        if (ids.isEmpty()) return -1

        val currentIndex = ids.indexOf(currentId)
        if (currentIndex >= 0) return currentIndex

        return previousIndex.coerceIn(0, ids.lastIndex)
    }

    fun navigate(
        songs: List<Song>,
        currentSongId: String?,
        offset: Int,
        shuffle: Boolean,
        random: Random = Random.Default
    ): Song? {
        val targetId = navigateIds(songs.map(Song::id), currentSongId, offset, shuffle, random)
        return songs.firstOrNull { it.id == targetId }
    }

    internal fun navigateIds(
        ids: List<String>,
        currentId: String?,
        offset: Int,
        shuffle: Boolean,
        random: Random = Random.Default
    ): String? {
        if (ids.isEmpty()) return null
        if (shuffle) {
            return ids.filter { it != currentId }.ifEmpty { ids }.random(random)
        }

        val currentIndex = ids.indexOf(currentId)
        if (currentIndex == -1) return null
        return ids[(currentIndex + offset).mod(ids.size)]
    }

    fun automaticNext(
        songs: List<Song>,
        currentSongId: String?,
        repeatMode: RepeatMode,
        shuffle: Boolean,
        random: Random = Random.Default
    ): Song? = songs.firstOrNull {
        it.id == automaticNextIds(
            songs.map(Song::id), currentSongId, repeatMode, shuffle, random
        )
    }

    internal fun automaticNextIds(
        ids: List<String>,
        currentId: String?,
        repeatMode: RepeatMode,
        shuffle: Boolean,
        random: Random = Random.Default
    ): String? = when (repeatMode) {
        RepeatMode.ONE -> null // ExoPlayer lo repite nativamente.
        RepeatMode.ALL -> navigateIds(ids, currentId, 1, shuffle, random)
        RepeatMode.OFF -> if (shuffle) {
            navigateIds(ids, currentId, 1, true, random)
        } else ids.getOrNull(ids.indexOf(currentId) + 1)
    }
}
