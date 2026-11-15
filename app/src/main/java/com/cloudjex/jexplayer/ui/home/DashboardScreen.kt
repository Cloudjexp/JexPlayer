package com.cloudjex.jexplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.DashboardMediaItem
import com.cloudjex.jexplayer.viewmodel.LibraryStats
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.PlaybackContext
import com.cloudjex.jexplayer.viewmodel.greetingFor
import java.util.Calendar

private enum class DashboardRoute {
    HOME,
    ALBUMS,
    ALBUM_DETAIL,
    ARTISTS,
    ARTIST_DETAIL,
    HISTORY
}

/**
 * Panel de inicio. Consume los flujos ya derivados por [MusicViewModel] y las
 * agrupaciones en memoria de la biblioteca: no escanea, no consulta preferencias
 * y no reordena nada durante la recomposición.
 */
@Composable
fun DashboardScreen(
    viewModel: MusicViewModel,
    bottomContentPadding: Dp,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albumGroups.collectAsState()
    val artists by viewModel.artistGroups.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val stats by viewModel.libraryStats.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }

    var routeName by rememberSaveable { mutableStateOf(DashboardRoute.HOME.name) }
    val route = DashboardRoute.entries.firstOrNull { it.name == routeName } ?: DashboardRoute.HOME
    var selectedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArtistKey by rememberSaveable { mutableStateOf<String?>(null) }

    val currentAlbum = remember(albums, selectedAlbumKey) {
        albums.firstOrNull { it.key == selectedAlbumKey }
    }
    val currentArtist = remember(artists, selectedArtistKey) {
        artists.firstOrNull { it.key == selectedArtistKey }
    }

    val playSong: (Song, List<Song>) -> Unit = { song, queue ->
        viewModel.playSong(song, PlaybackContext.Library, queue)
    }

    BackHandler(enabled = route != DashboardRoute.HOME) {
        routeName = when (route) {
            DashboardRoute.ALBUM_DETAIL -> DashboardRoute.ALBUMS.name
            DashboardRoute.ARTIST_DETAIL -> DashboardRoute.ARTISTS.name
            else -> DashboardRoute.HOME.name
        }
    }

    when {
        route == DashboardRoute.ALBUM_DETAIL && currentAlbum != null -> AlbumDetailScreen(
            album = currentAlbum,
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onPlaySong = playSong,
            onBack = { routeName = DashboardRoute.ALBUMS.name }
        )

        route == DashboardRoute.ALBUM_DETAIL || route == DashboardRoute.ALBUMS -> AlbumsBrowser(
            albums = albums,
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onOpenAlbum = { album ->
                selectedAlbumKey = album.key
                routeName = DashboardRoute.ALBUM_DETAIL.name
            },
            onBack = { routeName = DashboardRoute.HOME.name }
        )

        route == DashboardRoute.ARTIST_DETAIL && currentArtist != null ->
            ArtistDiscographyScreen(
                artist = currentArtist,
                songsById = songsById,
                bottomContentPadding = bottomContentPadding,
                onPlaySong = playSong,
                onBack = { routeName = DashboardRoute.ARTISTS.name }
            )

        route == DashboardRoute.ARTISTS || route == DashboardRoute.ARTIST_DETAIL -> ArtistsBrowser(
            artists = artists,
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onOpenArtist = { artist ->
                selectedArtistKey = artist.key
                routeName = DashboardRoute.ARTIST_DETAIL.name
            },
            onBack = { routeName = DashboardRoute.HOME.name }
        )

        route == DashboardRoute.HISTORY -> HistoryBrowser(
            items = recentlyPlayed.filterIsInstance<DashboardMediaItem.SongItem>(),
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onPlaySong = { song -> viewModel.playSongFromDashboard(song) },
            onBack = { routeName = DashboardRoute.HOME.name }
        )

        else -> DashboardHome(
            recentlyPlayed = recentlyPlayed,
            recentlyAdded = recentlyAdded,
            stats = stats,
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenFavorites = onOpenFavorites,
            onPlaySong = { song -> viewModel.playSongFromDashboard(song) },
            onOpenAlbum = { key ->
                selectedAlbumKey = key
                routeName = DashboardRoute.ALBUM_DETAIL.name
            },
            onOpenArtist = { key ->
                selectedArtistKey = key
                routeName = DashboardRoute.ARTIST_DETAIL.name
            },
            onOpenAlbums = { routeName = DashboardRoute.ALBUMS.name },
            onOpenArtists = { routeName = DashboardRoute.ARTISTS.name },
            onOpenHistory = { routeName = DashboardRoute.HISTORY.name }
        )
    }
}

@Composable
private fun DashboardHome(
    recentlyPlayed: List<DashboardMediaItem>,
    recentlyAdded: List<CachedSong>,
    stats: LibraryStats?,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val greeting = remember {
        greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    val addedSongs = remember(recentlyAdded, songsById) {
        recentlyAdded.mapNotNull { cached -> songsById[cached.id] }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomContentPadding + 20.dp)
    ) {
        item(key = "header") {
            DashboardHeader(
                greeting = greeting,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings
            )
        }

        item(key = "recently-played-title") {
            DashboardSectionTitle("Escuchado recientemente")
        }
        item(key = "recently-played") {
            if (recentlyPlayed.isEmpty()) {
                RecentlyPlayedEmpty()
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyPlayed, key = { it.key }) { item ->
                        when (item) {
                            is DashboardMediaItem.SongItem -> {
                                val song = songsById[item.entry.id]
                                if (song != null) {
                                    RecentlyPlayedSongCard(
                                        song = song,
                                        onClick = { onPlaySong(song) }
                                    )
                                }
                            }

                            is DashboardMediaItem.AlbumItem -> RecentlyPlayedLabelCard(
                                label = item.albumKey,
                                onClick = { onOpenAlbum(item.albumKey) }
                            )

                            is DashboardMediaItem.ArtistItem -> RecentlyPlayedLabelCard(
                                label = item.artistKey,
                                onClick = { onOpenArtist(item.artistKey) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "explore-title") { DashboardSectionTitle("Explora tu música") }
        item(key = "explore") {
            QuickAccessGrid(
                onOpenAlbums = onOpenAlbums,
                onOpenArtists = onOpenArtists,
                onOpenFavorites = onOpenFavorites,
                onOpenHistory = onOpenHistory
            )
        }

        item(key = "stats-title") { DashboardSectionTitle("Estadísticas") }
        item(key = "stats") { StatsCards(stats) }

        item(key = "added-title") {
            Column {
                DashboardSectionTitle("Añadido recientemente")
                Text(
                    text = "Según la fecha de modificación del archivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
                )
            }
        }
        if (addedSongs.isEmpty()) {
            item(key = "added-empty") {
                BrowseEmptyHint("Todavía no hay canciones con fecha de archivo conocida.")
            }
        } else {
            items(addedSongs, key = { "added:${it.id}" }) { song ->
                DashboardSongRow(song = song, onClick = { onPlaySong(song) })
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    greeting: String,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tu música",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "¿Qué quieres escuchar hoy?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSearch) {
            JexSymbolIcon(
                symbol = JexSymbol.Search,
                contentDescription = "Buscar",
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onOpenSettings) {
            JexSymbolIcon(
                symbol = JexSymbol.Settings,
                contentDescription = "Ajustes",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DashboardSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp)
    )
}

@Composable
private fun RecentlyPlayedSongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        SongArtwork(song = song, size = 132.dp, cornerRadius = 16.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist.ifBlank { "Artista desconocido" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentlyPlayedLabelCard(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(132.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentlyPlayedEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Aún no hay reproducciones",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Reproduce una canción y aparecerá aquí.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickAccessGrid(
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAccessCard(
                icon = JexSymbol.Album,
                label = "Álbumes",
                onClick = onOpenAlbums,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                icon = JexSymbol.Person,
                label = "Artistas",
                onClick = onOpenArtists,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAccessCard(
                icon = JexSymbol.Favorite,
                label = "Favoritos",
                onClick = onOpenFavorites,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                icon = JexSymbol.History,
                label = "Historial",
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatsCards(stats: LibraryStats?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            value = stats?.songCount?.toString() ?: "—",
            label = "Canciones",
            icon = JexSymbol.MusicNote,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = stats?.artistCount?.toString() ?: "—",
            label = "Artistas",
            icon = JexSymbol.Person,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: JexSymbol,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            JexSymbolIcon(
                symbol = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
