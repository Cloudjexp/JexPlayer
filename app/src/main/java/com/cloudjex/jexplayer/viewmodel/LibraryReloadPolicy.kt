package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.model.CachedSong

/** Motivo por el que se relanza la carga de la biblioteca. */
enum class LibraryReloadCause {
    /** Arranque de la app: revalidación automática de la caché ya pintada. */
    Startup,
    /** «Actualizar biblioteca» pulsado por el usuario en ajustes. */
    Manual,
    /** Alta o baja de una carpeta SAF. */
    FoldersChanged
}

/**
 * El scan de arranque es una revalidación, no una carga inicial: se oculta el
 * indicador de escaneo cuando la caché ya pintó la biblioteca. El refresco
 * manual y los cambios de carpetas son acciones del usuario y conservan el
 * indicador; una importación inicial (caché visible vacía) también lo muestra.
 */
internal fun shouldShowScanIndicator(
    cause: LibraryReloadCause,
    cacheHasSongs: Boolean
): Boolean = cause != LibraryReloadCause.Startup || !cacheHasSongs

/**
 * Re-publicar la biblioteca (re-emitir la lista ordenada y recomponer) solo
 * cuando el scan produjo un cambio real frente a las entradas ya publicadas.
 */
internal fun shouldRepublishLibrary(
    scanned: List<CachedSong>,
    published: List<CachedSong>
): Boolean = scanned != published

/**
 * Una lista filtrada vacía solo se comunica como estado vacío cuando hay un
 * escaneo activo o la caché ya publicó su primera lista real; antes de eso
 * la biblioteca se está restaurando y no debe tratarse como vacía.
 */
internal fun shouldShowEmptyLibrary(
    isScanning: Boolean,
    isLibraryInitialized: Boolean
): Boolean = isScanning || isLibraryInitialized
