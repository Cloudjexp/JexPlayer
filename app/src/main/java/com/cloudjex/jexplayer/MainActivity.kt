package com.cloudjex.jexplayer

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cloudjex.jexplayer.data.local.LibraryScrollPosition
import com.cloudjex.jexplayer.data.local.LibraryScrollPositionStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudjex.jexplayer.ui.home.HomeScreen
import com.cloudjex.jexplayer.ui.home.WelcomeScreen
import com.cloudjex.jexplayer.ui.home.SettingsScreen
import com.cloudjex.jexplayer.ui.player.PlayerScreen
import com.cloudjex.jexplayer.ui.theme.JexPlayerTheme
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.viewmodel.MusicViewModel

@OptIn(FlowPreview::class)
class MainActivity : ComponentActivity() {

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        // `uri` es null si el usuario cancela; el ViewModel lo maneja sin errores.
        viewModel.onFolderSelected(uri)
    }

    private lateinit var viewModel: MusicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            val vm: MusicViewModel = viewModel()
            viewModel = vm
            val selectedTheme by vm.selectedAppearanceTheme.collectAsState()

            JexPlayerTheme(theme = selectedTheme ?: AppearanceTheme.default) {
                if (selectedTheme == null) {
                    ScreenLoading()
                } else {
                    val folders by vm.folders.collectAsState()
                     val restoredScreen by vm.showPlayer.collectAsState()
                     val currentSongId by vm.currentSongId.collectAsState()
                     val songs by vm.songs.collectAsState()
                     val libraryScrollStore = remember {
                         LibraryScrollPositionStore(applicationContext)
                     }
                     val savedLibraryPosition = remember { libraryScrollStore.load() }
                     val libraryListState = rememberLazyListState(
                         initialFirstVisibleItemIndex = savedLibraryPosition.firstVisibleItemIndex,
                         initialFirstVisibleItemScrollOffset =
                             savedLibraryPosition.firstVisibleItemScrollOffset
                     )
                     var pendingLibraryFollowId by remember { mutableStateOf<String?>(null) }
                     var lastObservedSongId by remember { mutableStateOf(currentSongId) }
                     var settingsOpen by rememberSaveable { mutableStateOf(false) }

                     // Se escribe solo cuando la posición se estabiliza. La lista puede
                     // estar desmontada al abrir el reproductor, pero su estado sigue
                     // viviendo en esta composición.
                     LaunchedEffect(libraryListState, songs.size) {
                         if (songs.isEmpty()) return@LaunchedEffect

                         snapshotFlow {
                             LibraryScrollPosition(
                                 firstVisibleItemIndex = libraryListState.firstVisibleItemIndex,
                                 firstVisibleItemScrollOffset =
                                     libraryListState.firstVisibleItemScrollOffset
                             )
                         }
                             .distinctUntilChanged()
                             .debounce(400)
                             .collect { position ->
                                 libraryScrollStore.save(
                                     position.copy(
                                         firstVisibleItemIndex = position.firstVisibleItemIndex
                                             .coerceIn(0, songs.lastIndex)
                                     )
                                 )
                             }
                     }

                     // Garantiza la última posición al pasar la Activity a segundo
                     // plano, incluso si aún no terminó el debounce.
                     DisposableEffect(libraryListState, songs.size) {
                         val observer = LifecycleEventObserver { _, event ->
                             if (event == Lifecycle.Event.ON_STOP && songs.isNotEmpty()) {
                                 libraryScrollStore.save(
                                     LibraryScrollPosition(
                                         firstVisibleItemIndex = libraryListState.firstVisibleItemIndex
                                             .coerceIn(0, songs.lastIndex),
                                         firstVisibleItemScrollOffset =
                                             libraryListState.firstVisibleItemScrollOffset
                                     )
                                 )
                             }
                         }
                         lifecycle.addObserver(observer)
                         onDispose { lifecycle.removeObserver(observer) }
                     }

                     // La biblioteca se desmonta mientras se muestra el reproductor.
                     // Conservamos aquí el último cambio de identidad ocurrido durante
                     // ese intervalo, sin confundirlo con la restauración inicial.
                     LaunchedEffect(currentSongId) {
                         if (currentSongId != lastObservedSongId) {
                             if (restoredScreen == true && currentSongId != null) {
                                 pendingLibraryFollowId = currentSongId
                             }
                             lastObservedSongId = currentSongId
                         }
                     }

                    BackHandler(enabled = settingsOpen || restoredScreen == true) {
                        if (settingsOpen) {
                            settingsOpen = false
                        } else {
                            vm.setShowPlayer(false)
                        }
                    }

                    val transitionOffsetPx = with(LocalDensity.current) { 20.dp.roundToPx() }

                     Scaffold(
                         modifier = Modifier.fillMaxSize(),
                         // El NavigationBar consume el inset inferior. Aplicarlo también aquí
                         // deja una zona vacía entre la barra de la app y la barra del sistema.
                         contentWindowInsets = WindowInsets.safeDrawing.only(
                             WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                         )
                     ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            val screen = restoredScreen
                            when {
                                folders.isEmpty() -> {
                                    WelcomeScreen(onSelectFolders = { openFolderPicker() })
                                }
                                // La preferencia de pantalla pendiente no debe mostrar la biblioteca.
                                screen == null -> ScreenLoading()

                                else -> if (settingsOpen) {
                                    SettingsScreen(
                                        viewModel = vm,
                                        onBack = { settingsOpen = false },
                                        onAddFolder = { openFolderPicker() }
                                    )
                                } else AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        if (targetState) {
                                            (fadeIn(tween(600, easing = FastOutSlowInEasing)) +
                                                slideInVertically(
                                                    tween(600, easing = FastOutSlowInEasing)
                                                ) { transitionOffsetPx })
                                                .togetherWith(
                                                    fadeOut(tween(600, easing = FastOutSlowInEasing))
                                                )
                                        } else {
                                            fadeIn(tween(600, easing = FastOutSlowInEasing))
                                                .togetherWith(
                                                    fadeOut(tween(600, easing = FastOutSlowInEasing)) +
                                                        slideOutVertically(
                                                            tween(600, easing = FastOutSlowInEasing)
                                                        ) { transitionOffsetPx }
                                                )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "screenTransition"
                                ) { playerOpen ->
                                    if (playerOpen) {
                                        PlayerScreen(
                                            viewModel = vm,
                                            onBack = { vm.setShowPlayer(false) }
                                        )
                                    } else {
                                        HomeScreen(
                                            viewModel = vm,
                                            listState = libraryListState,
                                            pendingFollowId = pendingLibraryFollowId,
                                            onPendingFollowHandled = { songId ->
                                                if (pendingLibraryFollowId == songId) {
                                                    pendingLibraryFollowId = null
                                                }
                                            },
                                            onOpenPlayer = { vm.setShowPlayer(true) },
                                            onOpenSettings = { settingsOpen = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }
}

/**
 * Pantalla neutra mientras se lee la última pantalla guardada. Evita mostrar la
 * biblioteca como contenido provisional antes del reproductor.
 */
@Composable
private fun ScreenLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
