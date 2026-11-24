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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cloudjex.jexplayer.data.local.LibraryScrollPosition
import com.cloudjex.jexplayer.data.local.LibraryScrollPositionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudjex.jexplayer.ui.home.HomeScreen
import com.cloudjex.jexplayer.ui.home.SearchScreen
import com.cloudjex.jexplayer.ui.home.WelcomeScreen
import com.cloudjex.jexplayer.ui.home.JexUpdateDialog
import com.cloudjex.jexplayer.ui.home.SettingsScreen
import com.cloudjex.jexplayer.ui.home.DashboardScreen
import com.cloudjex.jexplayer.ui.home.MiniPlayerHost
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.navigation.JexBottomBar
import com.cloudjex.jexplayer.ui.navigation.JexBottomBarItem
import com.cloudjex.jexplayer.ui.player.PlayerScreen
import com.cloudjex.jexplayer.ui.theme.JexPlayerTheme
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.viewmodel.MusicViewModel

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
            val selectedAccent by vm.selectedAppearanceAccent.collectAsState()

            JexPlayerTheme(
                theme = selectedTheme ?: AppearanceTheme.default,
                accent = selectedAccent
            ) {
                if (selectedTheme == null) {
                    ScreenLoading()
                } else {
                    val folders by vm.folders.collectAsState()
                     val restoredScreen by vm.showPlayer.collectAsState()
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
                     val currentScreen = rememberUpdatedState(restoredScreen)
                      var settingsOpen by rememberSaveable { mutableStateOf(false) }
                      var selectedTab by rememberSaveable { mutableStateOf(0) }
                      var appTab by rememberSaveable { mutableStateOf(0) }
                      var previousAppTab by rememberSaveable { mutableStateOf(0) }
                      var selectedFolderUri by rememberSaveable {
                          mutableStateOf<String?>(null)
                      }
                      var pendingOpenFavorites by rememberSaveable { mutableStateOf(false) }
                      // El gesto horizontal y `appTab` son la misma intención en dos
                      // sitios: la barra, el back y el estado guardado escriben aquí,
                      // el pager solo lo refleja al asentarse.
                      val appPagerState = rememberPagerState(initialPage = appTab) {
                          APP_PAGE_KEYS.size
                      }
                      var floatingContentHeightPx by remember { mutableStateOf(0) }
                      val bottomContentPadding = with(LocalDensity.current) {
                          floatingContentHeightPx.toDp()
                      }
                      // Inicio y Biblioteca se desmontan al alternar; el holder
                      // conserva el estado guardable de cada destino (búsqueda,
                      // filtros, sub-pestaña) para no perderlo al volver.
                      val appStateHolder = rememberSaveableStateHolder()

                     // Se escribe solo cuando el gesto termina. Observar el offset
                     // directamente creaba un objeto y reactivaba el debounce en cada
                     // frame de scroll, sin aportar nada mientras el dedo se movía.
                     LaunchedEffect(libraryListState, songs.size, libraryScrollStore) {
                         if (songs.isEmpty()) return@LaunchedEffect

                         snapshotFlow { libraryListState.isScrollInProgress }
                             .distinctUntilChanged()
                             .collectLatest { scrolling ->
                                 if (scrolling) return@collectLatest
                                 delay(400)
                                 libraryScrollStore.save(
                                     LibraryScrollPosition(
                                         firstVisibleItemIndex =
                                             libraryListState.firstVisibleItemIndex
                                                 .coerceIn(0, songs.lastIndex),
                                         firstVisibleItemScrollOffset =
                                             libraryListState.firstVisibleItemScrollOffset
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
                     // ese intervalo. Se recoge el Flow dentro de la corrutina para que
                     // un cambio de pista no recomponga toda la jerarquía de la Activity.
                     LaunchedEffect(vm) {
                         var lastObservedSongId = vm.currentSongId.value
                         vm.currentSongId.collect { songId ->
                             if (songId != lastObservedSongId) {
                                 if (currentScreen.value == true && songId != null) {
                                     pendingLibraryFollowId = songId
                                 }
                                 lastObservedSongId = songId
                             }
                         }
                     }

                    BackHandler(enabled = settingsOpen || restoredScreen == true) {
                        if (settingsOpen) {
                            settingsOpen = false
                        } else {
                            vm.setShowPlayer(false)
                        }
                    }

                    // La búsqueda es un destino propio. Recordar de dónde se
                    // abrió permite que atrás devuelva al usuario a su pestaña.
                    val openSearch = {
                        if (appTab != 1) previousAppTab = appTab
                        appTab = 1
                    }

                    // `settledPage` no cambia mientras el pager se mueve (ni con el
                    // dedo ni animando): así un salto de dos páginas no escribe la
                    // intermedia, y no se cancela la animación que lo lleva a la
                    // destino. Solo se actualiza al asentarse.
                    LaunchedEffect(appPagerState) {
                        snapshotFlow { appPagerState.settledPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                if (page == appTab) return@collect
                                // Mismo criterio que openSearch: atrás devuelve al
                                // usuario a la sección desde la que llegó.
                                if (page == 1) previousAppTab = appTab
                                appTab = page
                            }
                    }

                    // Botones, "Abrir favoritos" y el estado guardado mueven el
                    // pager; el gesto ya lo movió por su cuenta.
                    LaunchedEffect(appTab) {
                        if (appPagerState.currentPage != appTab) {
                            appPagerState.animateScrollToPage(appTab)
                        }
                    }

                    // Buscar se compone durante el arrastre, antes de ser visible:
                    // no debe pedir foco (ni abrir el teclado) hasta estar asentada.
                    val searchActive = !appPagerState.isScrollInProgress &&
                        appPagerState.settledPage == 1

                    // Con destinos de aplicación, atrás vuelve a Inicio antes de
                    // salir. Nunca compite con el reproductor ni con ajustes.
                    BackHandler(
                        enabled = !settingsOpen &&
                            restoredScreen != true &&
                            appTab != 0
                    ) {
                        appTab = if (appTab == 1) previousAppTab else 0
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
                         Column(modifier = Modifier.padding(innerPadding)) {
                             Box(modifier = Modifier.weight(1f)) {
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
                                            selectedTab = selectedTab,
                                            onBack = { vm.setShowPlayer(false) },
                                            onTabSelected = {
                                                selectedTab = it
                                                vm.setShowPlayer(false)
                                            }
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Un pager, no un `when`: el gesto sigue al
                                            // dedo y los extremos (Inicio/Biblioteca) se
                                            // detienen solos. appTab sigue mandando en la
                                            // barra, el back y el estado guardado.
                                            HorizontalPager(
                                                state = appPagerState,
                                                modifier = Modifier.fillMaxSize(),
                                                key = { page -> APP_PAGE_KEYS[page] }
                                            ) { page ->
                                                appStateHolder.SaveableStateProvider(
                                                    APP_PAGE_KEYS[page]
                                                ) {
                                                    when (page) {
                                                        0 -> DashboardScreen(
                                                            viewModel = vm,
                                                            bottomContentPadding = bottomContentPadding,
                                                            onOpenSearch = openSearch,
                                                            onOpenSettings = { settingsOpen = true },
                                                            onOpenFavorites = {
                                                                selectedTab = 1
                                                                appTab = 2
                                                                pendingOpenFavorites = true
                                                            }
                                                        )

                                                        1 -> SearchScreen(
                                                            viewModel = vm,
                                                            selectedFolderUri = selectedFolderUri,
                                                            bottomContentPadding = bottomContentPadding,
                                                            isActive = searchActive,
                                                            onExit = {
                                                                appTab = previousAppTab
                                                            }
                                                        )

                                                        else -> HomeScreen(
                                                            viewModel = vm,
                                                            selectedTab = selectedTab,
                                                            onTabSelected = { selectedTab = it },
                                                            listState = libraryListState,
                                                            pendingFollowId = pendingLibraryFollowId,
                                                            onPendingFollowHandled = { songId ->
                                                                if (pendingLibraryFollowId == songId) {
                                                                    pendingLibraryFollowId = null
                                                                }
                                                            },
                                                            onOpenSettings = { settingsOpen = true },
                                                            bottomContentPadding = bottomContentPadding,
                                                            onOpenSearch = openSearch,
                                                            selectedFolderUri = selectedFolderUri,
                                                            onFolderSelected = {
                                                                selectedFolderUri = it
                                                            },
                                                            pendingOpenFavorites = pendingOpenFavorites,
                                                            onOpenFavoritesHandled = {
                                                                pendingOpenFavorites = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .onSizeChanged {
                                                        floatingContentHeightPx = it.height
                                                    }
                                            ) {
                                                MiniPlayerHost(
                                                    songs = songs,
                                                    viewModel = vm,
                                                    onOpenPlayer = { vm.setShowPlayer(true) }
                                                )
                                                JexBottomBar(
                                                    items = APP_BOTTOM_BAR_ITEMS,
                                                    selectedTab = appTab,
                                                    onTabSelected = { tab ->
                                                        if (tab == 1) openSearch()
                                                        else appTab = tab
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                JexUpdateDialog(viewModel = vm)
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
 */@Composable
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

/** Destinos principales de la aplicación, en el orden de la barra inferior. */
private val APP_BOTTOM_BAR_ITEMS = listOf(
    JexBottomBarItem(icon = JexSymbol.Home, label = "Inicio"),
    JexBottomBarItem(icon = JexSymbol.Search, label = "Buscar"),
    JexBottomBarItem(icon = JexSymbol.LibraryMusic, label = "Biblioteca")
)

/** Claves del SaveableStateHolder, una por página del pager y en su mismo orden. */
private val APP_PAGE_KEYS = listOf("inicio", "buscar", "biblioteca")
