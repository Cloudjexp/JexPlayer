package com.cloudjex.jexplayer.data.model

import android.net.Uri

data class CachedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArtUri: String?,
    val size: Long?,
    val lastModified: Long?,
    val albumArtScanned: Boolean? = null,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int? = null,
    val metadataScanned: Boolean? = null
)

fun CachedSong.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    uri = Uri.parse(uri),
    albumArtUri = albumArtUri?.let(Uri::parse),
    albumArtist = albumArtist,
    genre = genre,
    year = year
)
