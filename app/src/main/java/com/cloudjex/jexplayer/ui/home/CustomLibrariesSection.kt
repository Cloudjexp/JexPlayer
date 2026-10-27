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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.cloudjex.jexplayer.data.model.CustomLibrary
import com.cloudjex.jexplayer.data.model.CustomLibraryNameError
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.PlaybackContext

/** Vista general de Favoritos y de las bibliotecas personalizadas. */
@Composable
fun CustomLibrariesOverview(
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel,
    songs: List<Song>,
    favoriteCount: Int,
    customLibraries: List<CustomLibrary>,
    customLibrariesReady: Boolean,
    libraryMessage: String?,
    bottomContentPadding: Dp = 0.dp,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: (String) -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var libraryToRename by remember { mutableStateOf<CustomLibrary?>(null) }
    var libraryToDelete by remember { mutableStateOf<CustomLibrary?>(null) }
    val songsById = remember(songs) { songs.associateBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 16.dp + bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Tus listas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showCreateDialog = true },
                    enabled = customLibrariesReady
                ) {
                    Text("Crear lista")
                }
            }
        }

        item {
            FavoritesCard(count = favoriteCount, onClick = onOpenFavorites)
        }

        item {
            Text(
                text = "Bibliotecas personalizadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        libraryMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        when {
            !customLibrariesReady -> item {
                Text(
                    text = "Cargando listas…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            customLibraries.isEmpty() -> item {
                Text(
                    text = "Todavía no tienes listas personalizadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> items(customLibraries, key = { it.id }) { library ->
                val availableCount = library.songIds.count { it in songsById }
                val unavailableCount = library.songIds.size - availableCount

                CustomLibraryCard(
                    library = library,
                    availableCount = availableCount,
                    unavailableCount = unavailableCount,
                    canPlay = availableCount > 0,
                    onOpen = { onOpenLibrary(library.id) },
                    onPlay = {
                        library.songIds
                            .firstNotNullOfOrNull(songsById::get)
                            ?.let { song ->
                                viewModel.playSong(
                                    song,
                                    PlaybackContext.Custom(library.id),
                                    library.songIds.mapNotNull(songsById::get)
                                )
                            }
                    },
                    onRename = { libraryToRename = library },
                    onDelete = { libraryToDelete = library }
                )
            }
        }
    }

    if (showCreateDialog) {
        CustomLibraryNameDialog(
            viewModel = viewModel,
            library = null,
            onDismiss = { showCreateDialog = false }
        )
    }

    libraryToRename?.let { library ->
        CustomLibraryNameDialog(
            viewModel = viewModel,
            library = library,
            onDismiss = { libraryToRename = null }
        )
    }

    libraryToDelete?.let { library ->
        DeleteCustomLibraryDialog(
            library = library,
            onConfirm = {
                if (viewModel.deleteCustomLibrary(library.id)) {
                    libraryToDelete = null
                }
            },
            onDismiss = { libraryToDelete = null }
        )
    }
}

/** Detalle de una biblioteca personalizada, sin salir de la pestaña Listas. */
@Composable
fun CustomLibraryDetail(
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel,
    library: CustomLibrary,
    songs: List<Song>,
    favoriteIds: Set<String>,
    currentSongId: String?,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onAddToLibrary: (Song) -> Unit
) {
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val librarySongs = remember(library.songIds, songsById) {
        library.songIds.mapNotNull(songsById::get)
    }
    val unavailableCount = library.songIds.size - librarySongs.size
    val libraryMessage by viewModel.libraryMessage.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                JexSymbolIcon(
                    symbol = JexSymbol.ArrowBack,
                    contentDescription = "Volver a las listas",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(songCountText(librarySongs.size))
                        if (unavailableCount > 0) {
                            append(" · $unavailableCount no disponibles")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    librarySongs.firstOrNull()?.let { song ->
                        viewModel.playSong(
                            song,
                            PlaybackContext.Custom(library.id),
                            librarySongs
                        )
                    }
                },
                enabled = librarySongs.isNotEmpty()
            ) {
                JexSymbolIcon(
                    symbol = JexSymbol.PlayArrow,
                    contentDescription = "Reproducir lista",
                    tint = if (librarySongs.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }

        libraryMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        if (librarySongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Esta lista está vacía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = bottomContentPadding + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(librarySongs, key = { it.id }, contentType = { "song" }) { song ->
                    SongItem(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        isFavorite = song.id in favoriteIds,
                        onClick = {
                            viewModel.playSong(
                                song,
                                PlaybackContext.Custom(library.id),
                                librarySongs
                            )
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                        onAddToLibraryClick = { onAddToLibrary(song) },
                        onRemoveFromLibraryClick = {
                            viewModel.removeSongFromCustomLibrary(library.id, song.id)
                        },
                        scrollState = listState
                    )
                }
            }
        }
    }
}

/** Diálogo global para marcar la pertenencia de una canción a varias listas. */
@Composable
fun AddToLibraryDialog(
    viewModel: MusicViewModel,
    song: Song,
    libraries: List<CustomLibrary>,
    librariesReady: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateLibraryForSongDialog(
            viewModel = viewModel,
            song = song,
            onDismiss = { showCreateDialog = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a lista") },
        text = {
            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                when {
                    !librariesReady -> Text(
                        text = "Cargando listas…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    libraries.isEmpty() -> Text(
                        text = "Todavía no tienes listas personalizadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(libraries, key = { it.id }) { library ->
                            val checked = song.id in library.songIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggle(library.id) }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null
                                )
                                Text(
                                    text = library.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { showCreateDialog = true },
                    enabled = librariesReady
                ) {
                    Text("Crear nueva lista")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun CreateLibraryForSongDialog(
    viewModel: MusicViewModel,
    song: Song,
    onDismiss: () -> Unit
) {
    val ready by viewModel.customLibrariesReady.collectAsState()
    val libraries by viewModel.customLibraries.collectAsState()
    var name by rememberSaveable(song.id) { mutableStateOf("") }
    val error = remember(name, libraries, ready) {
        viewModel.customLibraryNameError(name)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear nueva lista") },
        text = {
            Column {
                Text(
                    text = "Se creará la lista y se añadirá «${song.title}».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(customLibraryNameErrorText(error)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (viewModel.createCustomLibraryWithSong(name, song.id)) {
                        onDismiss()
                    }
                },
                enabled = ready && name.isNotBlank() && error == null
            ) {
                Text("Crear y añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun FavoritesCard(
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JexSymbolIcon(
                symbol = JexSymbol.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = "Favoritos",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = songCountText(count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomLibraryCard(
    library: CustomLibrary,
    availableCount: Int,
    unavailableCount: Int,
    canPlay: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JexSymbolIcon(
                symbol = JexSymbol.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(songCountText(availableCount))
                        if (unavailableCount > 0) {
                            append(" · $unavailableCount no disponibles")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onPlay,
                enabled = canPlay
            ) {
                JexSymbolIcon(
                    symbol = JexSymbol.PlayArrow,
                    contentDescription = "Reproducir ${library.name}",
                    tint = if (canPlay) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    JexSymbolIcon(
                        symbol = JexSymbol.Settings,
                        contentDescription = "Opciones de ${library.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomLibraryNameDialog(
    viewModel: MusicViewModel,
    library: CustomLibrary?,
    onDismiss: () -> Unit
) {
    val ready by viewModel.customLibrariesReady.collectAsState()
    var name by rememberSaveable(library?.id) {
        mutableStateOf(library?.name.orEmpty())
    }
    val error = viewModel.customLibraryNameError(name, library?.id)
    val editing = library != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editing) "Renombrar lista" else "Crear lista")
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nombre") },
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(customLibraryNameErrorText(error)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val accepted = if (library == null) {
                        viewModel.createCustomLibrary(name)
                    } else {
                        viewModel.renameCustomLibrary(library.id, name)
                    }
                    if (accepted) onDismiss()
                },
                enabled = ready && name.isNotBlank() && error == null
            ) {
                Text(if (editing) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeleteCustomLibraryDialog(
    library: CustomLibrary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar lista") },
        text = {
            Text(
                "Se eliminará «${library.name}». " +
                    "Las canciones no se borran del dispositivo."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun customLibraryNameErrorText(error: CustomLibraryNameError): String =
    when (error) {
        CustomLibraryNameError.Blank -> "El nombre no puede estar vacío."
        CustomLibraryNameError.TooLong ->
            "El nombre no puede superar los 80 caracteres."
        CustomLibraryNameError.Reserved -> "«Favoritos» es una lista reservada."
        CustomLibraryNameError.Duplicate ->
            "Ya existe una lista con ese nombre."
    }

private fun songCountText(count: Int): String =
    if (count == 1) "1 canción" else "$count canciones"
