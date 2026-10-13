package com.cloudjex.jexplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudjex.jexplayer.ui.home.HomeScreen
import com.cloudjex.jexplayer.ui.home.WelcomeScreen
import com.cloudjex.jexplayer.ui.player.PlayerScreen
import com.cloudjex.jexplayer.ui.theme.JexPlayerTheme
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
        enableEdgeToEdge()

        setContent {
            JexPlayerTheme {
                val vm: MusicViewModel = viewModel()
                viewModel = vm
                val folders by vm.folders.collectAsState()

                var showPlayer by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = showPlayer) {
                    showPlayer = false
                }

                val transitionOffsetPx = with(LocalDensity.current) { 20.dp.roundToPx() }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (folders.isEmpty()) {
                            WelcomeScreen(onSelectFolders = { openFolderPicker() })
                        } else {
                            AnimatedContent(
                                targetState = showPlayer,
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
                                        onBack = { showPlayer = false }
                                    )
                                } else {
                                    HomeScreen(
                                        viewModel = vm,
                                        onAddFolder = { openFolderPicker() },
                                        onOpenPlayer = { showPlayer = true }
                                    )
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
