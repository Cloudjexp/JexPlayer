package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.AlbumGroup
import com.cloudjex.jexplayer.viewmodel.ArtistGroup
import com.cloudjex.jexplayer.viewmodel.DashboardMediaItem
import com.cloudjex.jexplayer.viewmodel.SongEntry
import com.cloudjex.jexplayer.viewmodel.albumsForArtist

/*
 * Pantallas de exploración del panel: agrupaciones reales de la biblioteca en
 * memoria (sin escaneos ni consultas extra) y navegación a detalle/discografía.
 * Los grupos viajan como [SongEntry]; aquí se resuelven a [Song] con el mapa que
 * la pantalla ya tiene para reproducir y cargar portadas.
 */

private fun List<SongEntry>.resolve(songsById: Map<String, Song>): List<Song> =
    mapNotNull { songsById[it.id] }

private fun List<Song>.artwork(): Song? =
    firstOrNull { it.albumArtUri != null } ?: firstOrNull()

@Composable
internal fun BrowseHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            JexSymbolIcon(
                symbol = JexSymbol.ArrowBack,
                contentDescription = "Volver",
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Fila compacta reutilizada por el panel y por las pantallas de exploración. */
@Composable
internal fun DashboardSongRow(
    song: Song,
    onClick: () -> Unit,
    subtitle: String? = null,
    showArtwork: Boolean = true
) {
    val secondary = subtitle ?: song.artist.ifBlank { "Artista desconocido" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArtwork) {
            SongArtwork(song = song, size = 48.dp, cornerRadius = 10.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AlbumsBrowser(
    albums: List<AlbumGroup>,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onOpenAlbum: (AlbumGroup) -> Unit,
    onBack: () -> Unit
) {
    val rows = remember(albums, songsById) {
        albums.map { album -> album to album.songs.resolve(songsById).artwork() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding + 16.dp)
    ) {
        item(key = "header") {
            BrowseHeader(title = "Álbumes", subtitle = "${albums.size} álbumes", onBack = onBack)
        }
        if (rows.isEmpty()) {
            item(key = "empty") { BrowseEmptyHint("Todavía no hay álbumes en tu biblioteca.") }
        }
        items(rows, key = { it.first.key }) { (album, artwork) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAlbum(album) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artwork != null) {
                    SongArtwork(song = artwork, size = 56.dp, cornerRadius = 12.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} · ${album.songCount} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlbumDetailScreen(
    album: AlbumGroup,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onPlaySong: (Song, List<Song>) -> Unit,
    onBack: () -> Unit
) {
    val songs = remember(album, songsById) { album.songs.resolve(songsById) }
    val artwork = remember(songs) { songs.artwork() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding + 16.dp)
    ) {
        item(key = "header") {
            Column {
                BrowseHeader(
                    title = album.title,
                    subtitle = "${album.artist} · ${songs.size} canciones",
                    onBack = onBack
                )
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artwork != null) {
                        SongArtwork(song = artwork, size = 96.dp, cornerRadius = 16.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        items(songs, key = { it.id }) { song ->
            DashboardSongRow(
                song = song,
                onClick = { onPlaySong(song, songs) },
                showArtwork = false
            )
        }
    }
}

@Composable
internal fun ArtistsBrowser(
    artists: List<ArtistGroup>,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onOpenArtist: (ArtistGroup) -> Unit,
    onBack: () -> Unit
) {
    val rows = remember(artists, songsById) {
        artists.map { artist -> artist to artist.songs.resolve(songsById).artwork() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding + 16.dp)
    ) {
        item(key = "header") {
            BrowseHeader(
                title = "Artistas",
                subtitle = "${artists.size} artistas",
                onBack = onBack
            )
        }
        if (rows.isEmpty()) {
            item(key = "empty") { BrowseEmptyHint("Todavía no hay artistas en tu biblioteca.") }
        }
        items(rows, key = { it.first.key }) { (artist, artwork) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(artist) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artwork != null) {
                    SongArtwork(song = artwork, size = 52.dp, cornerRadius = 26.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${artist.albumCount} álbumes · ${artist.songCount} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArtistDiscographyScreen(
    artist: ArtistGroup,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onPlaySong: (Song, List<Song>) -> Unit,
    onBack: () -> Unit
) {
    val albums = remember(artist) { albumsForArtist(artist.songs) }
    val artistSongs = remember(artist, songsById) { artist.songs.resolve(songsById) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding + 16.dp)
    ) {
        item(key = "header") {
            Column {
                BrowseHeader(
                    title = artist.name,
                    subtitle = "${artist.albumCount} álbumes · ${artist.songCount} canciones",
                    onBack = onBack
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        albums.forEach { album ->
            item(key = "album:${album.key}") {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 4.dp
                    )
                )
            }
            items(album.songs, key = { "song:${it.id}" }) { entry ->
                val song = songsById[entry.id] ?: return@items
                DashboardSongRow(
                    song = song,
                    onClick = { onPlaySong(song, artistSongs) },
                    subtitle = album.title,
                    showArtwork = false
                )
            }
        }
    }
}

@Composable
internal fun HistoryBrowser(
    items: List<DashboardMediaItem.SongItem>,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onPlaySong: (Song) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding + 16.dp)
    ) {
        item(key = "header") {
            BrowseHeader(
                title = "Historial",
                subtitle = "${items.size} canciones recientes",
                onBack = onBack
            )
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                BrowseEmptyHint(
                    "Todavía no hay reproducciones.\nReproduce una canción y aparecerá aquí."
                )
            }
        }
        items(items, key = { it.key }) { item ->
            val song = songsById[item.entry.id] ?: return@items
            DashboardSongRow(song = song, onClick = { onPlaySong(song) })
        }
    }
}

@Composable
internal fun BrowseEmptyHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Tarjeta de acceso rápido del panel. */
@Composable
internal fun QuickAccessCard(
    icon: JexSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            JexSymbolIcon(
                symbol = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
