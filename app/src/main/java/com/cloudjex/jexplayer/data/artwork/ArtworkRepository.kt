package com.cloudjex.jexplayer.data.artwork

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Carga las miniaturas de portada fuera del hilo principal y comparte el trabajo
 * entre filas que apuntan a la misma portada.
 *
 * La caché de memoria sigue siendo la de Coil. Este repositorio solo resuelve dos
 * problemas que Coil no cubre por sí solo: deduplicar peticiones simultáneas y
 * permitir que una fila consulte una imagen ya cacheada sin lanzar una petición.
 */
class ArtworkRepository private constructor(
    private val imageLoader: coil3.ImageLoader
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Coil limita las decodificaciones, pero no la ráfaga completa de fetch +
    // decode que se inicia al detenerse el scroll. Este permiso acota también la
    // cola de trabajo y deja CPU libre para que Compose termine el frame.
    private val loadPermits = Semaphore(2)

    private val coordinator = ArtworkRequestCoordinator<String, ImageBitmap?>(scope) { _, load ->
        load()
    }

    /** Devuelve una imagen ya cacheada, sin iniciar E/S ni decodificación. */
    fun peek(cacheKey: String): ImageBitmap? {
        val value = imageLoader.memoryCache?.get(MemoryCache.Key(cacheKey)) ?: return null
        return runCatching { value.image.toBitmap().asImageBitmap() }.getOrNull()
    }

    /**
     * Espera la imagen asociada a [cacheKey]. Varias filas de la misma portada
     * comparten una única petición; al cancelarse la última, la petición se
     * cancela también (Coil limpia de forma segura el trabajo pendiente).
     */
    suspend fun load(
        cacheKey: String,
        request: ImageRequest
    ): ImageBitmap? {
        return try {
            coordinator.await(cacheKey) {
                loadPermits.withPermit {
                    when (val result = imageLoader.execute(request)) {
                        is SuccessResult -> result.image.toBitmap().asImageBitmap()
                        else -> null
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: ArtworkRepository? = null

        fun from(context: Context): ArtworkRepository {
            return instance ?: synchronized(this) {
                instance ?: ArtworkRepository(
                    imageLoader = SingletonImageLoader.get(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}
