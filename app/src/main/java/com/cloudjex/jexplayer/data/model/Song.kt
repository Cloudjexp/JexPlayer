package com.cloudjex.jexplayer.data.model

import android.net.Uri

data class Song(
    /**
     * Identificador estable y único. Para las canciones obtenidas por SAF es la
     * URI del documento, que no colisiona entre archivos ni entre carpetas.
     */
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int? = null
)
