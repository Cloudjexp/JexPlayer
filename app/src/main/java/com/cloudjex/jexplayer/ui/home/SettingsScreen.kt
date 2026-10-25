package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.AppearanceAccent
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

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Biblioteca")
        SettingsCard {
            Text(
                "Carpetas seleccionadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (folders.isEmpty()) {
                Text("No hay carpetas configuradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                folders.forEach { folder ->
                    FolderSettingRow(
                        folder = folder,
                        isRevoked = folder.uri in revokedFolders,
                        onRemove = { folderToRemove = folder }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar carpeta musical")
            }
            OutlinedButton(
                onClick = { viewModel.refreshLibrary() },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Escaneando…" else "Actualizar biblioteca")
            }
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (revokedFolders.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Apariencia")
        SettingsCard {
            Text(
                "Elige una paleta oscura. El cambio se aplica al instante.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            AppearanceTheme.entries.forEach { theme ->
                AppearanceThemeOption(
                    theme = theme,
                    selected = selectedTheme == theme,
                    onSelect = { viewModel.setAppearanceTheme(theme) }
                )
                if (theme == AppearanceTheme.DARK && selectedTheme == AppearanceTheme.DARK) {
                    DarkAccentSelector(
                        selectedAccent = selectedAccent,
                        onAccentSelected = viewModel::setAppearanceAccent
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Reproducción")
        SettingsCard {
            Text("Controles de reproducción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Aleatorio, repetición y progreso se controlan desde el reproductor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle("Acerca de JexPlayer")
        SettingsCard {
            Text("JexPlayer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!versionName.isNullOrBlank()) {
                Text("Versión $versionName", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = { viewModel.checkForUpdates() },
                enabled = !isCheckingUpdates,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(if (isCheckingUpdates) "Comprobando…" else "Buscar actualizaciones")
            }
            updateMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DarkAccentSelector(
    selectedAccent: AppearanceAccent,
    onAccentSelected: (AppearanceAccent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Color de acento: ${selectedAccent.label}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppearanceAccent.entries.forEach { accent ->
                DropdownMenuItem(
                    text = { Text(accent.label) },
                    onClick = {
                        onAccentSelected(accent)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun AppearanceThemeOption(
    theme: AppearanceTheme,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val palette = paletteFor(theme)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = palette.primary,
                content = {}
            )
            Text(
                text = theme.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun FolderSettingRow(folder: MusicFolder, isRevoked: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.small,
            color = if (isRevoked) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                JexSymbolIcon(
                    symbol = JexSymbol.Folder,
                    contentDescription = null,
                    tint = if (isRevoked) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
                fontWeight = FontWeight.Medium
            )
            if (isRevoked) {
                Text(
                    "Permiso revocado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        TextButton(onClick = onRemove) { Text("Quitar") }
    }
}
