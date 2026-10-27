package com.cloudjex.jexplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.memory.MemoryCache

class JexPlayerApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            // Coil usa 4 decodificaciones en paralelo por defecto. Dos acota la
            // ráfaga de miniaturas para que no sature la CPU ni el asignador.
            .bitmapFactoryMaxParallelism(2)
            .memoryCache {
                MemoryCache.Builder()
                    // Mantiene el valor predeterminado de Coil, pero explícito:
                    // las portadas compartidas por álbum caben muchas más veces.
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .build()
    }
}
