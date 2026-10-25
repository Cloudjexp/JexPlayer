package com.cloudjex.jexplayer.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon

@Composable
fun JexBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    compact: Boolean = false
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
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp)
            .clip(RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        // El inset se aplica al contenedor exterior para que la isla quede
        // completamente por encima de la navegación del sistema.
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = { onTabSelected(index) },
                icon = {
                    JexSymbolIcon(
                        symbol = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = if (showLabels) {
                    { Text(text = item.label) }
                } else {
                    null
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
