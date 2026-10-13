package com.cloudjex.jexplayer.data.repository

import com.cloudjex.jexplayer.data.model.CachedSong

object ScanReusePolicy {

    fun canReuse(cached: CachedSong?, size: Long?, lastModified: Long?): Boolean {
        if (cached == null) return false
        if (!isReliable(cached.size, cached.lastModified)) return false
        if (!isReliable(size, lastModified)) return false
        return cached.size == size && cached.lastModified == lastModified
    }

    /**
     * Decisión completa de reutilización de una entrada de caché.
     *
     * Además de que los metadatos sigan vigentes ([canReuse]), exige que la
     * portada de esa versión del archivo ya se haya comprobado. Las entradas
     * antiguas (`albumArtScanned == null`) y las que no se pudieron leer se
     * vuelven a procesar una vez. Cuando la entrada declara una portada, su
     * archivo debe seguir existiendo para no mostrar una imagen rota.
     *
     * [albumArtFileExists] solo se invoca si hace falta comprobar el archivo,
     * de modo que el caso "sin portada" no toca el disco.
     */
    fun canReuseSong(
        cached: CachedSong?,
        size: Long?,
        lastModified: Long?,
        albumArtFileExists: (CachedSong) -> Boolean = { false }
    ): Boolean {
        if (!canReuse(cached, size, lastModified)) return false
        if (cached!!.albumArtScanned != true) return false
        if (cached.albumArtUri == null) return true
        return albumArtFileExists(cached)
    }

    fun isReliable(size: Long?, lastModified: Long?): Boolean =
        size != null && size > 0 && lastModified != null && lastModified > 0
}
