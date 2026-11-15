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

/** Un destino de la barra inferior de la aplicación. */
data class JexBottomBarItem(
    val icon: JexSymbol,
    val label: String
)

@Composable
fun JexBottomBar(
    items: List<JexBottomBarItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    compact: Boolean = false
) {
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
                selected = index == selectedTab,
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
