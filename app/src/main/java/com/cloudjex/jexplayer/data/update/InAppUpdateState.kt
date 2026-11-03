package com.cloudjex.jexplayer.data.update

import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/** Estado del flujo de actualización in-app que dibuja [JexUpdateDialog]. */
sealed interface InAppUpdateState {
    data object Idle : InAppUpdateState

    data class Available(
        val currentVersion: String,
        val newVersion: String,
        val changelog: String,
        val downloadUrl: String
    ) : InAppUpdateState {
        /** `false` cuando la release no trae APK: solo queda abrir la página en el navegador. */
        val isApk: Boolean get() = downloadUrl.endsWith(".apk", ignoreCase = true)
    }

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    ) : InAppUpdateState
}

internal fun formatMegabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

/**
 * Copia [input] en [output] por bloques de 8 KB y avisa del avance. Devuelve los
 * bytes copiados. [totalBytes] puede ser 0 si el servidor no envió `Content-Length`.
 */
internal fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    onProgress: (downloaded: Long, total: Long) -> Unit
): Long {
    val buffer = ByteArray(8 * 1024)
    var downloaded = 0L
    onProgress(0L, totalBytes)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        if (read == 0) continue
        output.write(buffer, 0, read)
        downloaded += read
        onProgress(downloaded, totalBytes)
    }
    return downloaded
}
