package com.cloudjex.jexplayer.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.cloudjex.jexplayer.data.local.AlbumArtStore
import com.cloudjex.jexplayer.data.lyrics.LyricsFormat
import com.cloudjex.jexplayer.data.lyrics.LyricsSidecar
import com.cloudjex.jexplayer.data.model.CachedSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ScanOutcome(
    val scannedByFolder: Map<String, List<CachedSong>>,
    val sidecarsByFolder: Map<String, List<LyricsSidecar>>,
    val failedFolders: Set<String>
)

class SafMusicScanner(
    private val context: Context,
    private val albumArtStore: AlbumArtStore = AlbumArtStore.from(context)
) {

    suspend fun scan(
        folderUris: List<Uri>,
        cached: Map<String, CachedSong>
    ): ScanOutcome = withContext(Dispatchers.IO) {
        val scannedByFolder = LinkedHashMap<String, List<CachedSong>>()
        val sidecarsByFolder = LinkedHashMap<String, List<LyricsSidecar>>()
        val failedFolders = mutableSetOf<String>()

        folderUris.forEach { treeUri ->
            val key = treeUri.toString()
            try {
                val songs = mutableListOf<CachedSong>()
                val sidecars = mutableListOf<LyricsSidecar>()
                val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                scanDirectory(
                    treeUri = treeUri,
                    parentDocumentId = rootDocumentId,
                    cached = cached,
                    out = songs,
                    sidecars = sidecars,
                    depth = 0
                )
                scannedByFolder[key] = songs
                    .distinctBy { it.id }
                    .sortedBy { it.title.lowercase() }
                sidecarsByFolder[key] = sidecars
                    .distinctBy { it.uri }
                    .sortedBy { it.displayName.lowercase() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {

                failedFolders += key
            }
        }

        ScanOutcome(
            scannedByFolder = scannedByFolder,
            sidecarsByFolder = sidecarsByFolder,
            failedFolders = failedFolders
        )
    }

    private suspend fun scanDirectory(
        treeUri: Uri,
        parentDocumentId: String,
        cached: Map<String, CachedSong>,
        out: MutableList<CachedSong>,
        sidecars: MutableList<LyricsSidecar>,
        depth: Int
    ) {
        coroutineContext.ensureActive()
        if (depth > MAX_DEPTH) return

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

        context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
            ?.use { cursor ->
                val idIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex =
                    cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()

                    val documentId = cursor.getString(idIndex) ?: continue
                    val displayName = cursor.getString(nameIndex) ?: documentId
                    val mimeType = cursor.getString(mimeIndex)
                    val size = readLong(cursor, sizeIndex)
                    val lastModified = readLong(cursor, modifiedIndex)

                    when {
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                            scanDirectory(
                                treeUri = treeUri,
                                parentDocumentId = documentId,
                                cached = cached,
                                out = out,
                                sidecars = sidecars,
                                depth = depth + 1
                            )
                        }

                        isAudio(displayName, mimeType) -> {
                            val documentUri =
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            out.add(
                                resolveSong(documentUri, displayName, size, lastModified, cached)
                            )
                        }

                        else -> {
                            val formato = LyricsFormat.fromFileName(displayName)
                            if (formato != null) {
                                val documentUri =
                                    DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri,
                                        documentId
                                    )
                                sidecars += LyricsSidecar(
                                    uri = documentUri.toString(),
                                    displayName = displayName,
                                    parentDocumentId = parentDocumentId,
                                    format = formato
                                )
                            }
                        }
                    }
                }
            }
    }

    private fun resolveSong(
        documentUri: Uri,
        displayName: String,
        size: Long?,
        lastModified: Long?,
        cached: Map<String, CachedSong>
    ): CachedSong {
        val songId = documentUri.toString()
        val cachedSong = cached[songId]

        if (cachedSong != null &&
            cachedSong.metadataScanned == true &&
            ScanReusePolicy.canReuseSong(cachedSong, size, lastModified) {
                // Solo se consulta el disco si la entrada declara una portada.
                albumArtStore.exists(songId, size, lastModified)
            }
        ) {
            return cachedSong
        }

        return extractSong(documentUri, displayName, size, lastModified)
    }

    private fun readLong(cursor: android.database.Cursor, columnIndex: Int): Long? =
        if (columnIndex >= 0 && !cursor.isNull(columnIndex)) cursor.getLong(columnIndex) else null

    private fun isAudio(displayName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType.startsWith("audio/")) return true

        val extension = displayName.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private fun extractSong(
        documentUri: Uri,
        displayName: String,
        size: Long?,
        lastModified: Long?
    ): CachedSong {
        val songId = documentUri.toString()
        var title = displayName.substringBeforeLast('.')
        var artist = UNKNOWN
        var album = UNKNOWN
        var albumArtist = ""
        var genre = ""
        var year: Int? = null
        var duration = 0L
        var albumArtUri: String? = null
        var albumArtScanned = false
        var metadataScanned = false

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, documentUri)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?.let { album = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { albumArtist = it }
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }
                ?.let { genre = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull()
                ?.let { year = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { duration = it }

            metadataScanned = true
            albumArtScanned = true
            albumArtUri = resolveAlbumArt(songId, size, lastModified, retriever)
        } catch (_: Exception) {
            // Metadatos no disponibles: se conservan los valores por defecto y la
            // portada queda sin comprobar para reintentarla cuando el archivo cambie.
        } finally {
            runCatching { retriever.release() }
        }

        return CachedSong(
            id = songId,
            title = title.ifBlank { displayName },
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
            duration = duration,
            uri = songId,
            albumArtUri = albumArtUri,
            size = size,
            lastModified = lastModified,
            albumArtScanned = albumArtScanned,
            metadataScanned = metadataScanned
        )
    }

    private fun resolveAlbumArt(
        songId: String,
        size: Long?,
        lastModified: Long?,
        retriever: MediaMetadataRetriever
    ): String? {
        albumArtStore.fileFor(songId, size, lastModified)
            .takeIf { it.isFile }
            ?.let { return Uri.fromFile(it).toString() }

        val picture = runCatching { retriever.embeddedPicture }.getOrNull()
        if (picture == null || picture.isEmpty()) return null

        val stored = albumArtStore.store(songId, size, lastModified, picture) ?: return null
        return Uri.fromFile(stored).toString()
    }

    private companion object {
        const val MAX_DEPTH = 20
        const val UNKNOWN = "Unknown"

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "wav", "flac", "ogg", "oga",
            "opus", "wma", "amr", "3gp", "mka", "aiff", "aif", "ape"
        )
    }
}
