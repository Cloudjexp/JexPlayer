package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.LibrarySortDirection
import com.cloudjex.jexplayer.data.model.LibrarySortField
import com.cloudjex.jexplayer.data.model.LibrarySortSettings
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon

@Composable
internal fun LibrarySortButton(
    settings: LibrarySortSettings,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick) {
        JexSymbolIcon(
            symbol = JexSymbol.Filter,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text("Ordenar")
    }
}

@Composable
internal fun LibrarySortDialog(
    settings: LibrarySortSettings,
    onSelect: (LibrarySortField, LibrarySortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordenar biblioteca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Campo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibrarySortField.entries.forEach { field ->
                        FilterChip(
                            selected = settings.field == field,
                            onClick = {
                                onSelect(field, settings.direction)
                            },
                            label = { Text(field.label) }
                        )
                    }
                }

                Text(
                    text = "Dirección",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibrarySortDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = settings.direction == direction,
                            onClick = {
                                onSelect(settings.field, direction)
                            },
                            label = { Text(direction.label) }
                        )
                    }
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
