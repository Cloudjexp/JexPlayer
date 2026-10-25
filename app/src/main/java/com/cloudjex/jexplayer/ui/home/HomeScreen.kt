package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalDensity
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.RepeatMode
import com.cloudjex.jexplayer.viewmodel.PlaybackContext
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    listState: LazyListState = rememberLazyListState(),
    pendingFollowId: String? = null,
    onPendingFollowHandled: (String) -> Unit = {},
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    androidx.compose.runtime.LaunchedEffect(favoriteIds) {
        android.util.Log.d(
            "JexFavorites",
            "Stored favorites: ${favoriteIds.size} - $favoriteIds"
        )
    }
    val folders by viewModel.folders.collectAsState()
    val revokedFolderUris by viewModel.revokedFolderUris.collectAsState()
    val folderMessage by viewModel.folderMessage.collectAsState()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val customLibraries by viewModel.customLibraries.collectAsState()
    val customLibrariesReady by viewModel.customLibrariesReady.collectAsState()
    val libraryMessage by viewModel.libraryMessage.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showFavoritesPlaylist by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryDialogSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable {
        mutableStateOf(0)
    }

    LaunchedEffect(customLibraries, customLibrariesReady, selectedLibraryId) {
        if (customLibrariesReady &&
            selectedLibraryId != null &&
            customLibraries.none { it.id == selectedLibraryId }
        ) {
            selectedLibraryId = null
        }
    }

    LaunchedEffect(songs, libraryDialogSongId) {
        if (libraryDialogSongId != null &&
            songs.none { it.id == libraryDialogSongId }
        ) {
            libraryDialogSongId = null
        }
    }

    BackHandler(
        enabled = (selectedLibraryId != null || showFavoritesPlaylist) &&
            libraryDialogSongId == null
    ) {
        if (selectedLibraryId != null) {
            selectedLibraryId = null
        } else {
            showFavoritesPlaylist = false
        }
    }

    val filteredSongs = remember(songs, searchQuery, selectedFolderUri) {
        songs.filter { song ->
            (selectedFolderUri == null || song.id.startsWith("${selectedFolderUri}/")) &&
                    (song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true) ||
                    song.album.contains(searchQuery, ignoreCase = true))
        }
    }
    val favoriteSongs = songs.filter { song ->
        song.id in favoriteIds
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // Cambia el contenido según la pestaña seleccionada
        when (selectedTab) {

            // PESTAÑA 0: MÚSICA
            0 -> {
                LibraryHeader(
                    isScanning = isScanning,
                    isSearchVisible = isSearchVisible,
                    onSearch = { isSearchVisible = true },
                    onSettings = onOpenSettings
                )

                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = fadeIn() + expandHorizontally(
                        expandFrom = Alignment.End
                    ),
                    exit = fadeOut() + shrinkHorizontally(
                        shrinkTowards = Alignment.End
                    )
                ) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            searchQuery = ""
                            isSearchVisible = false
                        }
                    )
                }

                LibrarySummary(
                    songCount = songs.size,
                    filtersVisible = showFilters,
                    onToggleFilters = {
                        showFilters = !showFilters
                    }
                )
                if (showFilters) {
                    FoldersFilterRow(
                        folders = folders,
                        revokedFolderUris = revokedFolderUris,
                        selectedFolderUri = selectedFolderUri,
                        onFolderSelected = {
                            selectedFolderUri = it
                        }
                    )
                }

                folderMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 2.dp
                        )
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (filteredSongs.isEmpty()) {
                        EmptyLibrary(
                            message = when {
                                searchQuery.isNotBlank() ||
                                        selectedFolderUri != null ->
                                    "No se encontraron canciones con estos filtros."

                                isScanning ->
                                    "Escaneando carpetas…"

                                else ->
                                    "No se encontraron archivos de audio en las carpetas seleccionadas."
                            }
                        )
                    } else {
                        LibrarySongList(
                            filteredSongs = filteredSongs,
                            favoriteIds = favoriteIds,
                            viewModel = viewModel,
                            listState = listState,
                            pendingFollowId = pendingFollowId,
                            onPendingFollowHandled = onPendingFollowHandled,
                            onAddToLibrary = { song ->
                                libraryDialogSongId = song.id
                            }
                        )
                    }
                }
            }

            // PESTAÑA 1: LISTAS
            1 -> {
                val selectedLibrary = selectedLibraryId?.let { id ->
                    customLibraries.firstOrNull { it.id == id }
                }

                when {
                    selectedLibrary != null -> {
                        CustomLibraryDetail(
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            library = selectedLibrary,
                            songs = songs,
                            favoriteIds = favoriteIds,
                            currentSongId = currentSongId,
                            onBack = { selectedLibraryId = null },
                            onAddToLibrary = { song ->
                                libraryDialogSongId = song.id
                            }
                        )
                    }

                    showFavoritesPlaylist -> {
                        FavoritesPlaylist(
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            favoriteSongs = favoriteSongs,
                            currentSongId = currentSongId,
                            onBack = { showFavoritesPlaylist = false },
                            onAddToLibrary = { song ->
                                libraryDialogSongId = song.id
                            }
                        )
                    }

                    else -> {
                        CustomLibrariesOverview(
                            modifier = Modifier.weight(1f),
                            viewModel = viewModel,
                            songs = songs,
                            favoriteCount = favoriteSongs.size,
                            customLibraries = customLibraries,
                            customLibrariesReady = customLibrariesReady,
                            libraryMessage = libraryMessage,
                            onOpenFavorites = {
                                selectedLibraryId = null
                                showFavoritesPlaylist = true
                            },
                            onOpenLibrary = { libraryId ->
                                showFavoritesPlaylist = false
                                selectedLibraryId = libraryId
                            }
                        )
                    }
                }
            }

            // PESTAÑA 2: CARPETAS
            2 -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tus carpetas de música",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        MiniPlayerHost(songs = songs, viewModel = viewModel, onOpenPlayer = onOpenPlayer)

        // BARRA DE NAVEGACIÓN INFERIOR
        JexBottomBar(
            selectedTab = selectedTab,
            onTabSelected = {
                selectedTab = it
            }
        )

        libraryDialogSongId?.let { songId ->
            songs.firstOrNull { it.id == songId }?.let { song ->
                AddToLibraryDialog(
                    song = song,
                    libraries = customLibraries,
                    librariesReady = customLibrariesReady,
                    onToggle = { libraryId ->
                        viewModel.toggleSongInCustomLibrary(libraryId, song.id)
                    },
                    onDismiss = { libraryDialogSongId = null }
                )
            }
        }
    }

}

@Composable
private fun FavoritesPlaylist(
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel,
    favoriteSongs: List<Song>,
    currentSongId: String?,
    onBack: () -> Unit,
    onAddToLibrary: (Song) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "← Favoritos",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(bottom = 16.dp)
        )

        if (favoriteSongs.isEmpty()) {
            Text(
                text = "Todavía no tienes canciones favoritas.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn {
                items(
                    favoriteSongs,
                    key = { it.id }
                ) { song ->
                    SongItem(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        isFavorite = true,
                        onClick = {
                            viewModel.playSong(song, PlaybackContext.Favorites)
                        },
                        onFavoriteClick = {
                            viewModel.toggleFavorite(song.id)
                        },
                        onAddToLibraryClick = {
                            onAddToLibrary(song)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySongList(
    filteredSongs: List<Song>,
    favoriteIds: Set<String>,
    viewModel: MusicViewModel,
    listState: LazyListState,
    pendingFollowId: String?,
    onPendingFollowHandled: (String) -> Unit,
    onAddToLibrary: (Song) -> Unit
) {
    val currentSongId by viewModel.currentSongId.collectAsState()
    var observedSongId by remember { mutableStateOf(currentSongId) }
    var hasObservedSong by remember { mutableStateOf(false) }
    var localFollowId by remember { mutableStateOf<String?>(null) }

    // Solo una nueva identidad de canción genera una solicitud. Los cambios de
    // progreso, reproducción y visualizador no pasan por este efecto.
    LaunchedEffect(currentSongId) {
        if (!hasObservedSong) {
            hasObservedSong = true
            observedSongId = currentSongId
        } else if (
            currentSongId != null &&
            currentSongId != observedSongId
        ) {
            observedSongId = currentSongId
            localFollowId = currentSongId
        }
    }

    val followId = pendingFollowId ?: localFollowId
    LaunchedEffect(followId, filteredSongs) {
        val songId = followId ?: return@LaunchedEffect
        val index = filteredSongs.indexOfFirst { it.id == songId }
        if (index == -1) return@LaunchedEffect

        // Mantener una canción ya visible evita reposicionamientos innecesarios.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }

        if (pendingFollowId == songId) onPendingFollowHandled(songId)
        if (localFollowId == songId) localFollowId = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = filteredSongs,
            key = { it.id },
            contentType = { "song" }
        ) { song ->
            SongItem(
                song = song,
                isCurrent = song.id == currentSongId,
                isFavorite = song.id in favoriteIds,
                onClick = { viewModel.playSong(song, PlaybackContext.Library) },
                onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                onAddToLibraryClick = { onAddToLibrary(song) }
            )
        }
    }
}

@Composable
private fun MiniPlayerHost(
    songs: List<Song>,
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit
) {
    val currentSongId by viewModel.currentSongId.collectAsState()
    val restoredSong by viewModel.restoredSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentSong = songs.find { it.id == currentSongId }
        ?: restoredSong?.takeIf { it.id == currentSongId }

    if (currentSong != null) {
        MiniPlayer(
            song = currentSong,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onPrevious = { viewModel.previousSong() },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextSong() },
            onShuffle = { viewModel.toggleShuffle() },
            onRepeat = { viewModel.toggleRepeat() },
            onOpenPlayer = onOpenPlayer
        )
    }
}

@Composable
private fun LibraryHeader(
    isScanning: Boolean,
    isSearchVisible: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    JexSymbolIcon(
                        symbol = JexSymbol.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Biblioteca",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isScanning) "Actualizando tu música…" else "Tu música local",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = !isSearchVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(onClick = onSearch) {
                    JexSymbolIcon(
                        symbol = JexSymbol.Search,
                        contentDescription = "Buscar canciones",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(onClick = onSettings) {
                JexSymbolIcon(
                    symbol = JexSymbol.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (isScanning) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Buscar canciones…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                JexSymbolIcon(
                    symbol = JexSymbol.Close,
                    contentDescription = "Cerrar búsqueda",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LibrarySummary(
    songCount: Int,
    filtersVisible: Boolean,
    onToggleFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (songCount == 1) "1 canción" else "$songCount canciones",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleFilters, modifier = Modifier.size(32.dp)) {
            JexSymbolIcon(
                symbol = JexSymbol.Filter,
                contentDescription = if (filtersVisible) "Ocultar filtros" else "Mostrar filtros",
                tint = if (filtersVisible) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FoldersFilterRow(
    folders: List<MusicFolder>,
    revokedFolderUris: Set<String>,
    selectedFolderUri: String?,
    onFolderSelected: (String?) -> Unit
) {
    if (folders.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedFolderUri == null,
                onClick = { onFolderSelected(null) },
                label = { Text("Todas") },
                leadingIcon = {
                    JexSymbolIcon(
                        symbol = JexSymbol.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            folders.forEach { folder ->
                val isRevoked = folder.uri in revokedFolderUris
                FilterChip(
                    selected = selectedFolderUri == folder.uri,
                    onClick = { onFolderSelected(folder.uri) },
                    label = {
                        Text(
                            text = folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    },
                    leadingIcon = {
                        JexSymbolIcon(
                            symbol = JexSymbol.Folder,
                            contentDescription = null,
                            tint = if (isRevoked) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    border = if (isRevoked) androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFolderUri == folder.uri,
                        borderColor = MaterialTheme.colorScheme.error
                    ) else null
                )
            }
        }

        if (revokedFolderUris.isNotEmpty()) {
            Text(
                text = "Permiso revocado en ${revokedFolderUris.size} carpeta(s): vuelve a seleccionarla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

    }
}

@Composable
private fun SongArtwork(
    song: Song,
    size: Dp,
    cornerRadius: Dp
) {
    val placeholderColor = MaterialTheme.colorScheme.secondaryContainer
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val density = LocalDensity.current
    val context = LocalPlatformContext.current
    val sizePx = with(density) { size.roundToPx() }
    val imageRequest = remember(song.albumArtUri, sizePx) {
        ImageRequest.Builder(context)
            .data(song.albumArtUri)
            // Las portadas originales pueden ser grandes; una fila solo necesita
            // los píxeles de su miniatura. La clave incluye el tamaño para no
            // mezclar la portada de 48 dp con la de 82 dp.
            .size(sizePx)
            .memoryCacheKey("${song.albumArtUri}|$sizePx")
            .diskCacheKey("${song.albumArtUri}|$sizePx")
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = song.album,
        modifier = Modifier
            .size(size)
            .clip(shape),
        contentScale = ContentScale.Crop,
        placeholder = placeholderPainter,
        error = placeholderPainter,
        fallback = placeholderPainter
    )
}

@Composable
internal fun SongItem(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToLibraryClick: () -> Unit,
    onRemoveFromLibraryClick: (() -> Unit)? = null
) {
    var listActionsExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) {
            // El estado activo se reconoce sin convertir la fila en un bloque rojo.
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(song = song, size = 82.dp, cornerRadius = 10.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.ifBlank { "Artista desconocido" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatDuration(song.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = {
                                if (onRemoveFromLibraryClick == null) {
                                    onAddToLibraryClick()
                                } else {
                                    listActionsExpanded = true
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            JexSymbolIcon(
                                symbol = JexSymbol.QueueMusic,
                                contentDescription = if (onRemoveFromLibraryClick == null) {
                                    "Añadir a lista"
                                } else {
                                    "Acciones de lista"
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (onRemoveFromLibraryClick != null) {
                            DropdownMenu(
                                expanded = listActionsExpanded,
                                onDismissRequest = { listActionsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Añadir a lista") },
                                    leadingIcon = {
                                        JexSymbolIcon(
                                            symbol = JexSymbol.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        listActionsExpanded = false
                                        onAddToLibraryClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quitar de esta lista") },
                                    leadingIcon = {
                                        JexSymbolIcon(
                                            symbol = JexSymbol.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        listActionsExpanded = false
                                        onRemoveFromLibraryClick()
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        JexSymbolIcon(
                            symbol = JexSymbol.Favorite,
                            contentDescription = if (isFavorite) {
                                "Quitar de favoritos"
                            } else {
                                "Agregar a favoritos"
                            },
                            tint = if (isFavorite) {
                                androidx.compose.ui.graphics.Color(0xFFE57373)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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

@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenPlayer),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(song = song, size = 48.dp, cornerRadius = 12.dp)

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
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

            IconButton(onClick = onShuffle, modifier = Modifier.size(36.dp)) {
                JexSymbolIcon(
                    symbol = JexSymbol.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                JexSymbolIcon(
                    symbol = JexSymbol.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause, size = 44.dp)

            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                JexSymbolIcon(
                    symbol = JexSymbol.SkipNext,
                    contentDescription = "Siguiente",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onRepeat, modifier = Modifier.size(36.dp)) {
                JexSymbolIcon(
                    symbol = if (repeatMode == RepeatMode.ONE) JexSymbol.RepeatOne
                    else JexSymbol.Repeat,
                    contentDescription = "Repetición",
                    tint = if (repeatMode == RepeatMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp = 48.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        JexSymbolIcon(
            symbol = if (isPlaying) JexSymbol.Pause else JexSymbol.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(if (size == 48.dp) 22.dp else 20.dp)
        )
    }
}

@Composable
private fun JexBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        BottomBarItem(
            JexSymbol.LibraryMusic,
            "Música",
            selected = selectedTab == 0
        ),
        BottomBarItem(
            JexSymbol.QueueMusic,
            "Listas",
            selected = selectedTab == 1
        ),
        BottomBarItem(
            JexSymbol.Folder,
            "Carpetas",
            selected = selectedTab == 2
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = {
                    onTabSelected(index)
                },
                icon = {
                    JexSymbolIcon(
                        symbol = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}

private data class BottomBarItem(
    val icon: JexSymbol,
    val label: String,
    val selected: Boolean
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}
