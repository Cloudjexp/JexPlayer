package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudjex.jexplayer.data.model.AppearanceAccent
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.ui.theme.paletteFor
import com.cloudjex.jexplayer.viewmodel.MusicViewModel

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onAddFolder: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val revokedFolders by viewModel.revokedFolderUris.collectAsState()
    val message by viewModel.folderMessage.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedTheme by viewModel.selectedAppearanceTheme.collectAsState()
    val selectedAccent by viewModel.selectedAppearanceAccent.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()
    var folderToRemove by remember { mutableStateOf<MusicFolder?>(null) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text("Quitar carpeta") },
            text = {
                Text("Se dejará de indexar «${folder.name}». No se eliminarán sus archivos del dispositivo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFolder(folder)
                    folderToRemove = null
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                JexSymbolIcon(
                    symbol = JexSymbol.ArrowBack,
                    contentDescription = "Volver a la biblioteca",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Personaliza tu experiencia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel("Biblioteca", Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsCard {
            if (folders.isEmpty()) {
                Text(
                    "No hay carpetas configuradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                folders.forEach { folder ->
                    FolderSettingRow(
                        folder = folder,
                        isRevoked = folder.uri in revokedFolders,
                        onRemove = { folderToRemove = folder }
                    )
                }
            }
            SettingsActionRow(
                title = "Agregar carpeta musical",
                symbol = JexSymbol.LibraryMusic,
                onClick = onAddFolder
            )
            SettingsActionRow(
                title = if (isScanning) "Escaneando…" else "Actualizar biblioteca",
                symbol = JexSymbol.Refresh,
                onClick = { viewModel.refreshLibrary() },
                enabled = !isScanning
            )
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (revokedFolders.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel("Apariencia", Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsCard {
            Text(
                "Elige una paleta oscura. El cambio se aplica al instante.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppearanceTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { viewModel.setAppearanceTheme(theme) },
                        label = { Text(theme.label) }
                    )
                }
            }
            // El acento solo afecta a la paleta Midnight.
            if (selectedTheme == AppearanceTheme.DARK) {
                Spacer(Modifier.height(16.dp))
                SettingsLabel("Color de acento", Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))
                AccentRow(
                    selectedAccent = selectedAccent,
                    onAccentSelected = viewModel::setAppearanceAccent
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel("Reproducción", Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Controles de reproducción",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Aleatorio, repetición y progreso se controlan desde el reproductor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsLabel("Acerca de JexPlayer", Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "JexPlayer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!versionName.isNullOrBlank()) {
                    Text(
                        "Versión $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            SettingsActionRow(
                title = if (isCheckingUpdates) "Comprobando actualizaciones…" else "Buscar actualizaciones",
                symbol = JexSymbol.Download,
                onClick = { viewModel.checkForUpdates() },
                enabled = !isCheckingUpdates
            )
            updateMessage?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccentRow(
    selectedAccent: AppearanceAccent,
    onAccentSelected: (AppearanceAccent) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(AppearanceAccent.entries) { accent ->
            val isSelected = accent == selectedAccent
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onAccentSelected(accent) }
                    )
                    .semantics { contentDescription = accent.label }
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(paletteFor(AppearanceTheme.DARK, accent).primary, CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun SettingsLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    symbol: JexSymbol,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(title, color = contentColor) },
        leadingContent = {
            JexSymbolIcon(
                symbol = symbol,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun FolderSettingRow(folder: MusicFolder, isRevoked: Boolean, onRemove: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = if (isRevoked) {
            {
                Text(
                    "Permiso revocado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,
        leadingContent = {
            JexSymbolIcon(
                symbol = JexSymbol.Folder,
                contentDescription = null,
                tint = if (isRevoked) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                JexSymbolIcon(
                    symbol = JexSymbol.Close,
                    contentDescription = "Quitar ${folder.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
