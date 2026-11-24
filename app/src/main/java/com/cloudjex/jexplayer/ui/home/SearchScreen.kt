package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.PlaybackContext

/**
 * Pantalla de búsqueda independiente. Reutiliza la biblioteca ya cargada en
 * [MusicViewModel] y los mismos criterios que el buscador anterior (título,
 * artista y álbum); no toca la cola ni el filtrado de la biblioteca.
 */
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    selectedFolderUri: String?,
    bottomContentPadding: Dp,
    isActive: Boolean = true,
    onExit: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val customLibraries by viewModel.customLibraries.collectAsState()
    val customLibrariesReady by viewModel.customLibrariesReady.collectAsState()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val focusManager = LocalFocusManager.current

    // El pager compone esta página durante el arrastre, antes de que se vea: el
    // foco solo se pide cuando Buscar está asentada, y se suelta al salir para
    // que el teclado no siga abierto sobre otra sección.
    LaunchedEffect(isActive) {
        if (!isActive) focusManager.clearFocus()
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var libraryDialogSongId by rememberSaveable { mutableStateOf<String?>(null) }

    // Al restaurar una búsqueda el cursor continúa al final del texto.
    LaunchedEffect(Unit) {
        if (query.text.isNotEmpty() &&
            query.selection != TextRange(query.text.length)
        ) {
            query = query.copy(selection = TextRange(query.text.length))
        }
    }

    val filteredSongs = remember(songs, query.text, selectedFolderUri) {
        songs.filter { song ->
            (selectedFolderUri == null || song.id.startsWith("${selectedFolderUri}/")) &&
                    (song.title.contains(query.text, ignoreCase = true) ||
                    song.artist.contains(query.text, ignoreCase = true) ||
                    song.album.contains(query.text, ignoreCase = true))
        }
    }

    // El estado de la lista vive fuera de las ramas para conservar el scroll
    // cuando se borra y se vuelve a escribir la búsqueda.
    val listState = rememberLazyListState()
    val scrollIdle = rememberScrollIdleSignal(listState)

    // Con el teclado abierto la lista ya termina por encima del mini-player y
    // de la barra inferior, así que no hace falta reservar su altura.
    val imeVisible = WindowInsets.ime
        .getBottom(LocalDensity.current) > 0
    val listBottomPadding = if (imeVisible) 8.dp else bottomContentPadding + 8.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                JexSymbolIcon(
                    symbol = JexSymbol.ArrowBack,
                    contentDescription = "Volver",
                    modifier = Modifier.size(22.dp)
                )
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                onClear = { query = TextFieldValue("") },
                isActive = isActive
            )
        }

        when {
            query.text.isBlank() -> SearchHint(
                "Busca por título, artista o álbum."
            )

            filteredSongs.isEmpty() -> SearchHint(
                "No se encontraron canciones."
            )

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = listBottomPadding
                    )
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
                            onClick = {
                                viewModel.playSong(
                                    song,
                                    PlaybackContext.Library,
                                    filteredSongs
                                )
                            },
                            onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                            onAddToLibraryClick = {
                                libraryDialogSongId = song.id
                            },
                            scrollIdle = scrollIdle
                        )
                    }
                }
            }
        }
    }

    libraryDialogSongId?.let { songId ->
        songs.firstOrNull { it.id == songId }?.let { song ->
            AddToLibraryDialog(
                viewModel = viewModel,
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

@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    isActive: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var didRequestFocus by rememberSaveable { mutableStateOf(false) }

    // Solo la primera apertura muestra el teclado: al volver a la pestaña o
    // cerrar el reproductor el campo se restaura sin robar el foco. Con el
    // pager hay que esperar además a que la página esté asentada.
    LaunchedEffect(isActive) {
        if (isActive && !didRequestFocus) {
            focusRequester.requestFocus()
            didRequestFocus = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JexSymbolIcon(
                symbol = JexSymbol.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(
                            text = "Buscar canciones...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            if (value.text.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    JexSymbolIcon(
                        symbol = JexSymbol.Close,
                        contentDescription = "Limpiar búsqueda",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHint(message: String) {
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
