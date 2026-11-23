package com.cloudjex.jexplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.AllListeningInsights
import com.cloudjex.jexplayer.viewmodel.DashboardMediaItem
import com.cloudjex.jexplayer.viewmodel.DashboardRankings
import com.cloudjex.jexplayer.viewmodel.ListeningInsights
import com.cloudjex.jexplayer.viewmodel.FullDashboardRankings
import com.cloudjex.jexplayer.viewmodel.FullRankingSet
import com.cloudjex.jexplayer.viewmodel.LibraryStats
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.PlaybackContext
import com.cloudjex.jexplayer.viewmodel.RankedAlbum
import com.cloudjex.jexplayer.viewmodel.RankedArtist
import com.cloudjex.jexplayer.viewmodel.RankedSong
import com.cloudjex.jexplayer.viewmodel.formatListenedTime
import com.cloudjex.jexplayer.viewmodel.greetingFor
import java.util.Calendar
import kotlinx.coroutines.delay

private enum class DashboardRoute {
    HOME,
    ALBUMS,
    ALBUM_DETAIL,
    ARTISTS,
    ARTIST_DETAIL,
    HISTORY,
    RANKING_DETAIL
}

private enum class RankingPeriod {
    WEEK,
    MONTH,
    YEAR,
    ALL_TIME
}

private fun RankingPeriod.label(): String = when (this) {
    RankingPeriod.WEEK -> "últimos 7 días"
    RankingPeriod.MONTH -> "últimos 30 días"
    RankingPeriod.YEAR -> "último año"
    RankingPeriod.ALL_TIME -> "todo el tiempo"
}

private fun insightsForPeriod(
    insights: AllListeningInsights?,
    period: RankingPeriod
): ListeningInsights? = when (period) {
    RankingPeriod.WEEK -> insights?.weekly
    RankingPeriod.MONTH -> insights?.monthly
    RankingPeriod.YEAR -> insights?.yearly
    RankingPeriod.ALL_TIME -> insights?.allTime
}

private enum class RankingTab {
    SONGS,
    ARTISTS,
    ALBUMS
}

private const val FEATURED_PAGES = 3
private const val FEATURED_VIRTUAL_PAGES = 300
private const val FEATURED_START_PAGE = 100
private const val FEATURED_AUTO_SCROLL_DELAY_MS = 3_500L


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
    val rankings by viewModel.dashboardRankings.collectAsState()
    val fullRankings by viewModel.fullDashboardRankings.collectAsState()
    val insights by viewModel.listeningInsights.collectAsState()

    val songsById = remember(songs) { songs.associateBy { it.id } }

    var routeName by rememberSaveable { mutableStateOf(DashboardRoute.HOME.name) }
    val route = DashboardRoute.entries.firstOrNull { it.name == routeName } ?: DashboardRoute.HOME
    var selectedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArtistKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Compartido entre la tarjeta y su detalle para que el periodo elegido no
    // se pierda al entrar o salir.
    var rankingPeriodName by rememberSaveable { mutableStateOf(RankingPeriod.WEEK.name) }
    val rankingPeriod = RankingPeriod.entries
        .firstOrNull { it.name == rankingPeriodName } ?: RankingPeriod.WEEK
    var rankingDetailTab by rememberSaveable { mutableStateOf(RankingTab.ARTISTS.name) }

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

        route == DashboardRoute.RANKING_DETAIL -> RankingDetailScreen(
            rankings = fullRankings,
            period = rankingPeriod,
            onPeriodChange = { rankingPeriodName = it.name },
            initialTab = RankingTab.entries
                .firstOrNull { it.name == rankingDetailTab } ?: RankingTab.ARTISTS,
            songsById = songsById,
            bottomContentPadding = bottomContentPadding,
            onBack = { routeName = DashboardRoute.HOME.name },
            onPlaySong = { song -> viewModel.playSongFromDashboard(song) },
            onOpenArtist = { key ->
                selectedArtistKey = key
                routeName = DashboardRoute.ARTIST_DETAIL.name
            },
            onOpenAlbum = { key ->
                selectedAlbumKey = key
                routeName = DashboardRoute.ALBUM_DETAIL.name
            }
        )

        else -> DashboardHome(
            recentlyPlayed = recentlyPlayed,
            recentlyAdded = recentlyAdded,
            stats = stats,
            rankings = rankings,
            insights = insights,
            rankingPeriod = rankingPeriod,
            onRankingPeriodChange = { rankingPeriodName = it.name },
            onOpenRankingDetail = { tab ->
                rankingDetailTab = tab.name
                routeName = DashboardRoute.RANKING_DETAIL.name
            },
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
    rankings: DashboardRankings?,
    insights: AllListeningInsights?,
    rankingPeriod: RankingPeriod,
    onRankingPeriodChange: (RankingPeriod) -> Unit,
    onOpenRankingDetail: (RankingTab) -> Unit,
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

        item(key = "rankings-featured") {
            FeaturedStatsCard(
                rankings = rankings,
                period = rankingPeriod,
                onPeriodChange = onRankingPeriodChange,
                songsById = songsById,
                onOpenDetail = onOpenRankingDetail
            )
        }

        item(key = "listening-insights") {
            ListeningInsightsCard(
                insights = insightsForPeriod(insights, rankingPeriod),
                periodLabel = rankingPeriod.label()
            )
        }

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

// ---------------------------------------------------------------------------
// Rankings de escucha
// ---------------------------------------------------------------------------

@Composable
private fun RankingPeriodToggle(
    selected: RankingPeriod,
    onSelect: (RankingPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            RankingPeriod.WEEK to "7 días",
            RankingPeriod.MONTH to "30 días",
            RankingPeriod.YEAR to "1 año",
            RankingPeriod.ALL_TIME to "Todo"
        ).forEach { (period, label) ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RankingEmptyHint() {
    Text(
        text = "Reproduce música para ver tus rankings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
    )
}

@Composable
private fun RankedSongsRow(
    ranked: List<RankedSong>,
    songsById: Map<String, Song>,
    onPlaySong: (Song) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(ranked, key = { _, item -> "ranked-song:${item.entry.id}" }) { index, item ->
            val song = songsById[item.entry.id]
            RankedMediaCard(
                rank = index + 1,
                artworkSong = song,
                title = item.entry.title,
                subtitle = item.entry.artist.ifBlank { "Artista desconocido" },
                footer = rankingFooter(item.playCount, item.listenedMs),
                cornerRadius = 16.dp,
                onClick = { song?.let(onPlaySong) }
            )
        }
    }
}

@Composable
private fun RankedArtistsRow(
    ranked: List<RankedArtist>,
    songsById: Map<String, Song>,
    onOpenArtist: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(ranked, key = { _, item -> "ranked-artist:${item.key}" }) { index, item ->
            // No hay proveedor de fotos de artistas: usamos la portada del álbum
            // de su canción más escuchada como imagen representativa.
            val artworkSong = songsById[item.representativeSongId]
            RankedMediaCard(
                rank = index + 1,
                artworkSong = artworkSong,
                title = item.name,
                subtitle = "Artista",
                footer = rankingFooter(item.playCount, item.listenedMs),
                cornerRadius = 66.dp,
                onClick = { onOpenArtist(item.key) }
            )
        }
    }
}

@Composable
private fun RankedAlbumsRow(
    ranked: List<RankedAlbum>,
    songsById: Map<String, Song>,
    onOpenAlbum: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(ranked, key = { _, item -> "ranked-album:${item.key}" }) { index, item ->
            val artworkSong = songsById[item.representativeSongId]
            RankedMediaCard(
                rank = index + 1,
                artworkSong = artworkSong,
                title = item.title,
                subtitle = item.artist,
                footer = rankingFooter(item.playCount, item.listenedMs),
                cornerRadius = 16.dp,
                onClick = { onOpenAlbum(item.key) }
            )
        }
    }
}

private fun rankingFooter(playCount: Int, listenedMs: Long): String {
    val plays = if (playCount == 1) "1 reproducción" else "$playCount reproducciones"
    return "$plays · ${formatListenedTime(listenedMs)}"
}

@Composable
private fun RankedMediaCard(
    rank: Int,
    artworkSong: Song?,
    title: String,
    subtitle: String,
    footer: String,
    cornerRadius: Dp,
    onClick: () -> Unit
) {
    val initial = remember(title) {
        title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
    }
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            if (artworkSong != null) {
                SongArtwork(
                    song = artworkSong,
                    size = 132.dp,
                    cornerRadius = cornerRadius
                )
            } else {
                Surface(
                    modifier = Modifier.size(132.dp),
                    shape = RoundedCornerShape(cornerRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = rank.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = footer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Tarjeta destacada: artista -> canción obsesión -> álbum, deslizando a la derecha
// ---------------------------------------------------------------------------

private fun featuredTabForPage(page: Int): RankingTab = when (page) {
    0 -> RankingTab.ARTISTS
    1 -> RankingTab.SONGS
    else -> RankingTab.ALBUMS
}

@Composable
private fun FeaturedStatsCard(
    rankings: DashboardRankings?,
    period: RankingPeriod,
    onPeriodChange: (RankingPeriod) -> Unit,
    songsById: Map<String, Song>,
    onOpenDetail: (RankingTab) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = FEATURED_START_PAGE,
        pageCount = { FEATURED_VIRTUAL_PAGES }
    )

    val topArtist = when (period) {
        RankingPeriod.WEEK -> rankings?.weeklyArtists?.firstOrNull()
        RankingPeriod.MONTH -> rankings?.monthlyArtists?.firstOrNull()
        RankingPeriod.YEAR -> rankings?.yearlyArtists?.firstOrNull()
        RankingPeriod.ALL_TIME -> rankings?.allTimeArtists?.firstOrNull()
    }
    val topSong = when (period) {
        RankingPeriod.WEEK -> rankings?.weeklySongs?.firstOrNull()
        RankingPeriod.MONTH -> rankings?.monthlySongs?.firstOrNull()
        RankingPeriod.YEAR -> rankings?.yearlySongs?.firstOrNull()
        RankingPeriod.ALL_TIME -> rankings?.allTimeSongs?.firstOrNull()
    }
    val topAlbum = when (period) {
        RankingPeriod.WEEK -> rankings?.weeklyAlbums?.firstOrNull()
        RankingPeriod.MONTH -> rankings?.monthlyAlbums?.firstOrNull()
        RankingPeriod.YEAR -> rankings?.yearlyAlbums?.firstOrNull()
        RankingPeriod.ALL_TIME -> rankings?.allTimeAlbums?.firstOrNull()
    }

    // Avance automático hacia la derecha. Las páginas virtuales hacen que el
    // salto de la última categoría a la primera también se deslice a la derecha.
    // Si cambia el periodo a mitad de un deslizamiento, se asienta primero la
    // página más cercana para no dejar la tarjeta entre dos categorías.
    LaunchedEffect(pagerState, period) {
        if (pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(
                page = pagerState.currentPage,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = FastOutSlowInEasing
                )
            )
        }
        while (true) {
            while (pagerState.isScrollInProgress) delay(100L)
            delay(FEATURED_AUTO_SCROLL_DELAY_MS)
            val next = pagerState.currentPage + 1
            if (next >= FEATURED_VIRTUAL_PAGES) {
                pagerState.scrollToPage(FEATURED_START_PAGE)
            } else {
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(
                        durationMillis = 650,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Lo más escuchado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))
            RankingPeriodToggle(
                selected = period,
                onSelect = onPeriodChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val frameHeight = maxWidth * 0.55f
                val coverImageSize = maxWidth
                val artistImageSize = frameHeight - 12.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(frameHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onOpenDetail(featuredTabForPage(pagerState.currentPage % FEATURED_PAGES))
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page % FEATURED_PAGES) {
                            0 -> FeaturedArtistPage(
                                eyebrow = "Artista más escuchado",
                                title = topArtist?.name ?: "Sin datos aún",
                                footer = if (topArtist != null) {
                                    rankingFooter(topArtist.playCount, topArtist.listenedMs)
                                } else {
                                    "Reproduce música para ver tu top"
                                },
                                artworkSong = songsById[topArtist?.representativeSongId],
                                imageSize = artistImageSize
                            )

                            1 -> FeaturedCoverPage(
                                eyebrow = "Canción obsesión",
                                title = topSong?.entry?.title ?: "Sin datos aún",
                                subtitle = topSong?.entry?.artist?.ifBlank { "Artista desconocido" }
                                    ?: "",
                                footer = if (topSong != null) {
                                    rankingFooter(topSong.playCount, topSong.listenedMs)
                                } else {
                                    "Reproduce música para ver tu top"
                                },
                                artworkSong = topSong?.let { songsById[it.entry.id] },
                                imageSize = coverImageSize
                            )

                            else -> FeaturedCoverPage(
                                eyebrow = "Álbum más escuchado",
                                title = topAlbum?.title ?: "Sin datos aún",
                                subtitle = topAlbum?.artist ?: "",
                                footer = if (topAlbum != null) {
                                    rankingFooter(topAlbum.playCount, topAlbum.listenedMs)
                                } else {
                                    "Reproduce música para ver tu top"
                                },
                                artworkSong = songsById[topAlbum?.representativeSongId],
                                imageSize = coverImageSize
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(FEATURED_PAGES) { index ->
                    val active = pagerState.currentPage % FEATURED_PAGES == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 8.dp else 6.dp)
                            .background(
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
            }

        }
    }
}

@Composable
private fun FeaturedCoverPage(
    eyebrow: String,
    title: String,
    subtitle: String,
    footer: String,
    artworkSong: Song?,
    imageSize: Dp
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        if (artworkSong != null) {
            // La portada cubre todo el recuadro; el marco recorta el exceso vertical.
            SongArtwork(song = artworkSong, size = imageSize, cornerRadius = 0.dp)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun FeaturedArtistPage(
    eyebrow: String,
    title: String,
    footer: String,
    artworkSong: Song?,
    imageSize: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                // Reserva el hueco de la foto de la derecha más un pequeño margen.
                .padding(start = 14.dp, end = imageSize + 18.dp)
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            if (artworkSong != null) {
                SongArtwork(
                    song = artworkSong,
                    size = imageSize,
                    cornerRadius = 14.dp
                )
            } else {
                Surface(
                    modifier = Modifier.size(imageSize),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        JexSymbolIcon(
                            symbol = JexSymbol.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningInsightsCard(
    insights: ListeningInsights?,
    periodLabel: String
) {
    val days = insights?.days.orEmpty()
    val genres = insights?.genres.orEmpty()
    val moods = insights?.moods.orEmpty().filter { it.playCount > 0 }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Tus hábitos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = insights?.chartLabel.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (days.all { it.listenedMs <= 0L }) {
                Text(
                    text = "Reproduce música para ver tus días más activos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxMs = days.maxOf { it.listenedMs }.coerceAtLeast(1L)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            val barHeight = if (day.listenedMs <= 0L) {
                                2.dp
                            } else {
                                (64f * day.listenedMs.toFloat() / maxMs.toFloat()).dp
                                    .coerceAtLeast(4.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 3.dp,
                                            topEnd = 3.dp
                                        )
                                    )
                                    .background(
                                        if (day.listenedMs > 0L) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                            Text(
                                text = day.label.take(2),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                val topDay = days.maxByOrNull { it.listenedMs }
                if (topDay != null && topDay.listenedMs > 0L) {
                    Text(
                        text = "Día más activo: ${topDay.label} ${topDay.dateLabel} · " +
                            formatListenedTime(topDay.listenedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Géneros · $periodLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (genres.isEmpty()) {
                Text(
                    text = "Sin géneros en los tags de tus canciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(genres, key = { it.label }) { genre ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${genre.label} · ${genre.playCount}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ánimo estimado según tus géneros",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (moods.isEmpty()) {
                Text(
                    text = "Escucha música para ver tu ánimo estimado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val total = moods.sumOf { it.playCount }.coerceAtLeast(1)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(moods, key = { it.label }) { mood ->
                        val percent = mood.playCount * 100 / total
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${mood.label} · $percent%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detalle: todas las canciones, artistas y álbumes, primero los más escuchados
// ---------------------------------------------------------------------------

@Composable
private fun RankingDetailScreen(
    rankings: FullDashboardRankings?,
    period: RankingPeriod,
    onPeriodChange: (RankingPeriod) -> Unit,
    initialTab: RankingTab,
    songsById: Map<String, Song>,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    val set: FullRankingSet = when (period) {
        RankingPeriod.WEEK -> rankings?.weekly ?: FullRankingSet()
        RankingPeriod.MONTH -> rankings?.monthly ?: FullRankingSet()
        RankingPeriod.YEAR -> rankings?.yearly ?: FullRankingSet()
        RankingPeriod.ALL_TIME -> rankings?.allTime ?: FullRankingSet()
    }

    var selectedTabName by rememberSaveable { mutableStateOf(initialTab.name) }
    LaunchedEffect(initialTab) { selectedTabName = initialTab.name }
    val tabs = RankingTab.entries
    val selectedTab = tabs.firstOrNull { it.name == selectedTabName } ?: initialTab

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                JexSymbolIcon(
                    symbol = JexSymbol.ArrowBack,
                    contentDescription = "Volver"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tus estadísticas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Primero lo más escuchado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RankingPeriodToggle(
            selected = period,
            onSelect = onPeriodChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )

        SecondaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTabName = tab.name },
                    text = {
                        Text(
                            when (tab) {
                                RankingTab.SONGS -> "Canciones"
                                RankingTab.ARTISTS -> "Artistas"
                                RankingTab.ALBUMS -> "Álbumes"
                            }
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomContentPadding + 20.dp)
        ) {
            when (selectedTab) {
                RankingTab.SONGS -> {
                    item(key = "detail-songs-title") { DashboardSectionTitle("Canciones") }
                    itemsIndexed(set.songs, key = { _, item -> "detail-song:${item.entry.id}" }) { index, item ->
                        val song = songsById[item.entry.id]
                        RankingDetailRow(
                            rank = index + 1,
                            artworkSong = song,
                            title = item.entry.title,
                            subtitle = item.entry.artist.ifBlank { "Artista desconocido" },
                            footer = rankingFooter(item.playCount, item.listenedMs),
                            onClick = { song?.let(onPlaySong) }
                        )
                    }
                }

                RankingTab.ARTISTS -> {
                    item(key = "detail-artists-title") { DashboardSectionTitle("Artistas") }
                    itemsIndexed(set.artists, key = { _, item -> "detail-artist:${item.key}" }) { index, item ->
                        RankingDetailRow(
                            rank = index + 1,
                            artworkSong = songsById[item.representativeSongId],
                            title = item.name,
                            subtitle = "Artista",
                            footer = rankingFooter(item.playCount, item.listenedMs),
                            onClick = { onOpenArtist(item.key) }
                        )
                    }
                }

                RankingTab.ALBUMS -> {
                    item(key = "detail-albums-title") { DashboardSectionTitle("Álbumes") }
                    itemsIndexed(set.albums, key = { _, item -> "detail-album:${item.key}" }) { index, item ->
                        RankingDetailRow(
                            rank = index + 1,
                            artworkSong = songsById[item.representativeSongId],
                            title = item.title,
                            subtitle = item.artist,
                            footer = rankingFooter(item.playCount, item.listenedMs),
                            onClick = { onOpenAlbum(item.key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingDetailRow(
    rank: Int,
    artworkSong: Song?,
    title: String,
    subtitle: String,
    footer: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        if (artworkSong != null) {
            SongArtwork(song = artworkSong, size = 48.dp, cornerRadius = 10.dp)
        } else {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
