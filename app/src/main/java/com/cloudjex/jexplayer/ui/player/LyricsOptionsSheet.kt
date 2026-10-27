package com.cloudjex.jexplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.lyrics.LyricsSource
import com.cloudjex.jexplayer.data.lyrics.LyricsTranslationSupport
import com.cloudjex.jexplayer.data.model.LyricsColorPreset
import com.cloudjex.jexplayer.data.model.LyricsFontPreset
import com.cloudjex.jexplayer.data.model.LyricsLineMotion
import com.cloudjex.jexplayer.data.model.LyricsSettings
import com.cloudjex.jexplayer.data.model.LyricsStylePreset

/**
 * Panel inferior con las fuentes disponibles y los ajustes visuales de la
 * letra. Se abre desde el icono de ajustes de la esquina superior derecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOptionsSheet(
    sources: List<LyricsSource>,
    selectedSourceId: String?,
    settings: LyricsSettings,
    translationSupport: LyricsTranslationSupport,
    onSourceSelected: (String) -> Unit,
    onSettingsChange: (LyricsSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mostrarAyudaTraduccion by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Opciones de letra",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Elige la fuente y cómo quieres ver la letra.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            Text(
                text = "Fuente",
                style = MaterialTheme.typography.titleMedium
            )

            if (sources.isEmpty()) {
                Text(
                    text = "No hay fuentes de letra disponibles para esta canción.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val etiquetasRepetidas = sources
                    .groupBy { it.label }
                    .filterValues { it.size > 1 }
                    .keys

                sources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(source.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = source.id == selectedSourceId,
                            onClick = { onSourceSelected(source.id) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = detalleFuente(
                                    source = source,
                                    mostrarCarpeta = source.label in etiquetasRepetidas
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            val traduccionDisponible =
                translationSupport == LyricsTranslationSupport.AVAILABLE

            Text(
                text = "Traducción",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (traduccionDisponible) 1f else 0.55f)
                    .clickable {
                        if (traduccionDisponible) {
                            onSettingsChange(
                                settings.copy(
                                    showTranslation = !settings.showTranslation
                                )
                            )
                        } else {
                            mostrarAyudaTraduccion = true
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mostrar traducción",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (traduccionDisponible) {
                            "Añade la traducción debajo de la línea original."
                        } else {
                            "No disponible para esta letra. Toca para saber cómo debe ser."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = traduccionDisponible && settings.showTranslation,
                    onCheckedChange = {
                        if (traduccionDisponible) {
                            onSettingsChange(settings.copy(showTranslation = it))
                        } else {
                            mostrarAyudaTraduccion = true
                        }
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            Text(
                text = "Estilo",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsStylePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = mismoEstilo(settings, preset),
                        onClick = {
                            onSettingsChange(aplicarEstilo(settings, preset))
                        },
                        label = { Text(preset.label) }
                    )
                }
            }

            Text(
                text = "Color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsColorPreset.entries.forEach { color ->
                    FilterChip(
                        selected = settings.colorPreset == color,
                        onClick = {
                            onSettingsChange(settings.copy(colorPreset = color))
                        },
                        label = { Text(color.label) }
                    )
                }
            }

            Text(
                text = "Tipografía",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsFontPreset.entries.forEach { font ->
                    FilterChip(
                        selected = settings.fontPreset == font,
                        onClick = {
                            onSettingsChange(settings.copy(fontPreset = font))
                        },
                        label = { Text(font.label) }
                    )
                }
            }

            Text(
                text = "Transición",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyricsLineMotion.entries.forEach { motion ->
                    FilterChip(
                        selected = settings.lineMotion == motion,
                        onClick = {
                            onSettingsChange(settings.copy(lineMotion = motion))
                        },
                        label = { Text(motion.label) }
                    )
                }
            }
            Text(
                text = if (settings.lineMotion == LyricsLineMotion.ENFOQUE) {
                    "La línea activa queda nítida y las demás se alejan y difuminan según su distancia."
                } else {
                    "La línea activa crece y el resto se atenúa, sin desenfoque."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Cursiva",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Aplica al texto de la letra",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.italic,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(italic = it))
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Tamaño",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${settings.fontSizeSp} sp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        onSettingsChange(
                            settings.copy(fontSizeSp = settings.fontSizeSp - 1)
                        )
                    },
                    enabled = settings.fontSizeSp > LyricsSettings.MIN_FONT_SIZE_SP
                ) {
                    Text("−")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        onSettingsChange(
                            settings.copy(fontSizeSp = settings.fontSizeSp + 1)
                        )
                    },
                    enabled = settings.fontSizeSp < LyricsSettings.MAX_FONT_SIZE_SP
                ) {
                    Text("+")
                }
            }

            TextButton(
                onClick = {
                    onSettingsChange(
                        aplicarEstilo(settings, LyricsStylePreset.CLASICO)
                    )
                },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Restablecer estilo")
            }
        }
    }

    if (mostrarAyudaTraduccion) {
        AlertDialog(
            onDismissRequest = { mostrarAyudaTraduccion = false },
            title = { Text("Traducción no disponible") },
            text = { Text(mensajeTraduccion(translationSupport)) },
            confirmButton = {
                TextButton(onClick = { mostrarAyudaTraduccion = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

private fun mismoEstilo(
    settings: LyricsSettings,
    preset: LyricsStylePreset
): Boolean = settings.colorPreset == preset.settings.colorPreset &&
    settings.fontPreset == preset.settings.fontPreset &&
    settings.italic == preset.settings.italic &&
    settings.fontSizeSp == preset.settings.fontSizeSp

private fun aplicarEstilo(
    settings: LyricsSettings,
    preset: LyricsStylePreset
): LyricsSettings = settings.copy(
    colorPreset = preset.settings.colorPreset,
    fontPreset = preset.settings.fontPreset,
    italic = preset.settings.italic,
    fontSizeSp = preset.settings.fontSizeSp
)

private fun mensajeTraduccion(
    support: LyricsTranslationSupport
): String = when (support) {
    LyricsTranslationSupport.UNAVAILABLE_KARAOKE ->
        "Esta letra tiene tiempos por palabra (karaoke), así que la traducción no se puede agrupar. La opción solo funciona con letras LRC simples."
    else ->
        "Para activarla, la letra debe ser un LRC simple con al menos dos parejas de original y traducción en líneas separadas, con el mismo tiempo y la traducción entre paréntesis. Ejemplo:\n\n" +
            "[00:12.44]'Cause I'm beggin'\n" +
            "[00:12.44](Porque estoy suplicando)\n\n" +
            "No funciona con karaoke por palabra."
}

private fun detalleFuente(
    source: LyricsSource,
    mostrarCarpeta: Boolean
): String = buildList {
    add(source.format.etiqueta)
    if (source.isEmbedded) add("incluida en el archivo")
    source.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    if (mostrarCarpeta) source.folderHint?.let(::add)
}.joinToString(" · ")
