package com.cloudjex.jexplayer.data.artwork

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Comparte un trabajo asíncrono entre todos los consumidores de la misma clave.
 *
 * Coil no deduplica peticiones en vuelo: si dos filas del mismo álbum entran a la
 * vez, ambas decodifican la misma imagen. Aquí la primera crea el trabajo y las
 * demás esperan el mismo [Deferred]. Cuando el último consumidor cancela (porque
 * su fila salió de pantalla), el trabajo se cancela; si queda al menos uno,
 * continúa. Un resultado completado se elimina del mapa y queda disponible en la
 * caché de Coil.
 */
internal class ArtworkRequestCoordinator<K, T>(
    private val scope: CoroutineScope,
    private val loader: suspend (K, suspend () -> T?) -> T?
) {

    private class Entry<T>(val deferred: Deferred<T?>) {
        var waiters = 0
    }

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Entry<T>>()

    suspend fun await(key: K, load: suspend () -> T?): T? {
        // La adquisición no debe quedar a medias si el consumidor se cancela
        // justo al entrar: el contador de esperas se incrementa y el `finally`
        // posterior siempre lo revierte.
        val entry = withContext(NonCancellable) {
            mutex.withLock {
                inFlight[key]?.also { existing ->
                    existing.waiters++
                } ?: createEntry(key, load)
            }
        }

        try {
            return entry.deferred.await()
        } finally {
            // La limpieza debe ocurrir incluso si el consumidor fue cancelado.
            withContext(NonCancellable) {
                mutex.withLock {
                    entry.waiters--
                    if (entry.waiters <= 0) {
                        if (inFlight[key] === entry) inFlight.remove(key)
                        entry.deferred.cancel()
                    }
                }
            }
        }
    }

    private fun createEntry(key: K, load: suspend () -> T?): Entry<T> {
        lateinit var entry: Entry<T>
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                loader(key, load)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                mutex.withLock {
                    if (inFlight[key] === entry) inFlight.remove(key)
                }
            }
        }
        entry = Entry(deferred).also { it.waiters = 1 }
        inFlight[key] = entry
        deferred.start()
        return entry
    }
}
