package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.model.Song
import kotlin.random.Random

/** Lista que dio origen a la reproducción. La cola no debe depender de la pantalla visible. */
sealed interface PlaybackContext {
    data object Library : PlaybackContext
    data object Favorites : PlaybackContext

    /** Preparado para listas persistidas: conserva únicamente sus IDs en la biblioteca actual. */
    data class Custom(val songIds: List<String>) : PlaybackContext
}

internal object PlaybackQueue {
    fun songsFor(
        context: PlaybackContext,
        library: List<Song>,
        favoriteIds: Set<String>
    ): List<Song> {
        val byId = library.associateBy { it.id }
        return idsFor(context, library.map(Song::id), favoriteIds).mapNotNull(byId::get)
    }

    internal fun idsFor(
        context: PlaybackContext,
        libraryIds: List<String>,
        favoriteIds: Set<String>
    ): List<String> = when (context) {
        PlaybackContext.Library -> libraryIds
        PlaybackContext.Favorites -> libraryIds.filter { it in favoriteIds }
        is PlaybackContext.Custom -> context.songIds.filter { it in libraryIds }
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
