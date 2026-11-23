package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.LibrarySortDirection
import com.cloudjex.jexplayer.data.model.LibrarySortField
import com.cloudjex.jexplayer.data.model.LibrarySortSettings
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon

/**
 * Botón "Ordenar" con su menú flotante anclado. Reutiliza [LibrarySortSettings]
 * y el mismo callback de selección; la lógica y la persistencia no cambian.
 */
@Composable
internal fun LibrarySortButton(
    settings: LibrarySortSettings,
    onSelect: (LibrarySortField, LibrarySortDirection) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            JexSymbolIcon(
                symbol = JexSymbol.Filter,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text("Ordenar")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Un tono del acento sobre la superficie del tema: el borde es sutil
            // y el fondo cálido sale de los colores del tema activo.
            shape = RoundedCornerShape(20.dp),
            // Fondo oscuro con un matiz del acento del tema: cálido en los temas
            // ámbar/rubí y coherente en el resto, sin colores fijos.
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.primary,
                0.06f
            ),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            Text(
                text = "Ordenar por",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 14.dp,
                    bottom = 4.dp
                )
            )

            LibrarySortField.entries.forEach { field ->
                val selected = settings.field == field
                DropdownMenuItem(
                    text = {
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold
                            else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = { onSelect(field, settings.direction) },
                    leadingIcon = {
                        JexSymbolIcon(
                            symbol = field.sortIcon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (selected) {
                        {
                            JexSymbolIcon(
                                symbol = JexSymbol.Check,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibrarySortDirection.entries.forEach { direction ->
                    SortDirectionOption(
                        direction = direction,
                        selected = settings.direction == direction,
                        onClick = { onSelect(settings.field, direction) }
                    )
                }
            }
        }
    }
}

/** Dirección integrada en el menú: píldora con flecha y etiqueta. */
@Composable
private fun SortDirectionOption(
    direction: LibrarySortDirection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JexSymbolIcon(
                symbol = JexSymbol.ArrowBack,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(16.dp)
                    // La flecha base apunta a la izquierda; girarla da la
                    // dirección ascendente o descendente sin nuevos recursos.
                    .rotate(
                        if (direction == LibrarySortDirection.ASCENDING) 90f else -90f
                    )
            )
            Text(
                text = direction.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Icono de cada criterio, alineado con la iconografía del resto de la app. */
private val LibrarySortField.sortIcon: JexSymbol
    get() = when (this) {
        LibrarySortField.TITLE -> JexSymbol.MusicNote
        LibrarySortField.ALBUM -> JexSymbol.Album
        LibrarySortField.ARTIST -> JexSymbol.Person
        LibrarySortField.ALBUM_ARTIST -> JexSymbol.LibraryMusic
        LibrarySortField.GENRE -> JexSymbol.Filter
        LibrarySortField.YEAR -> JexSymbol.History
    }
