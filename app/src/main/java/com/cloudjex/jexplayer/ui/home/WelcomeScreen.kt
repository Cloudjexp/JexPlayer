package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Primera pantalla cuando no hay ninguna carpeta configurada. Explica cómo
 * funciona la biblioteca y da acceso al selector oficial de carpetas.
 */
@Composable
fun WelcomeScreen(onSelectFolders: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bienvenido a JexPlayer",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Elige las carpetas donde guardas tu música. Solo se mostrarán " +
                    "los audios de esas ubicaciones y de sus subcarpetas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        Button(
            onClick = onSelectFolders,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Seleccionar carpetas")
        }
    }
}
