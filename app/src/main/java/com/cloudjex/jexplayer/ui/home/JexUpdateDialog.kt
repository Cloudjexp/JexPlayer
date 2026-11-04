package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cloudjex.jexplayer.data.update.InAppUpdateState
import com.cloudjex.jexplayer.data.update.formatMegabytes
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import kotlin.math.roundToInt

/** Fondo pedido por diseño: el diálogo es oscuro en todos los temas. */
private val DialogBackground = Color(0xFF1C1C1E)
private val DialogCard = Color(0xFF2C2C2E)
private val DialogSecondary = Color(0xFFBDBDBD)

@Composable
fun JexUpdateDialog(viewModel: MusicViewModel) {
    val state by viewModel.inAppUpdate.collectAsState()
    if (state is InAppUpdateState.Idle) return

    Dialog(onDismissRequest = { viewModel.hideUpdate() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val current = state) {
                    is InAppUpdateState.Available -> AvailableContent(
                        update = current,
                        onDownload = { viewModel.startInAppDownload(current.downloadUrl) },
                        onDismissForever = viewModel::dismissUpdate,
                        onLater = viewModel::hideUpdate
                    )

                    is InAppUpdateState.Downloading -> DownloadingContent(
                        downloading = current,
                        onCancel = viewModel::cancelInAppDownload
                    )

                    is InAppUpdateState.ReadyToInstall -> ReadyToInstallContent(
                        onInstall = { viewModel.installApk(current.apkFile) },
                        onLater = viewModel::hideUpdate
                    )

                    is InAppUpdateState.Error -> ErrorContent(
                        message = current.message,
                        onClose = viewModel::hideUpdate
                    )

                    InAppUpdateState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun AvailableContent(
    update: InAppUpdateState.Available,
    onDownload: () -> Unit,
    onDismissForever: () -> Unit,
    onLater: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        JexSymbolIcon(
            symbol = JexSymbol.Download,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = accent
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(
        "Actualización Disponible",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Una nueva versión está lista",
        color = DialogSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DialogCard)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersionColumn("Actual", update.currentVersion, DialogSecondary, Modifier.weight(1f))
        Text("→", color = DialogSecondary)
        VersionColumn("Nuevo", update.newVersion, accent, Modifier.weight(1f))
    }

    if (update.changelog.isNotBlank()) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Novedades",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            update.changelog,
            color = DialogSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DialogCard)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            if (update.isApk) "↓ Descargar & Instalar" else "Abrir en GitHub",
            fontWeight = FontWeight.SemiBold
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismissForever, modifier = Modifier.weight(1f)) {
            Text("No recordar", color = DialogSecondary)
        }
        TextButton(onClick = onLater, modifier = Modifier.weight(1f)) {
            Text("Más tarde", color = DialogSecondary)
        }
    }
}

@Composable
private fun DownloadingContent(
    downloading: InAppUpdateState.Downloading,
    onCancel: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val progress = downloading.progress.coerceIn(0f, 1f)
    val barModifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))

    Text(
        "Descargando...",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(20.dp))
    if (downloading.totalBytes > 0) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = barModifier,
            color = accent,
            trackColor = DialogCard
        )
    } else {
        LinearProgressIndicator(
            modifier = barModifier,
            color = accent,
            trackColor = DialogCard
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${formatMegabytes(downloading.downloadedBytes)} / " +
                formatMegabytes(downloading.totalBytes),
            color = DialogSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(progress * 100).roundToInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Cancelar", color = DialogSecondary)
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onClose: () -> Unit
) {
    Text(
        "Algo salió mal",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        message,
        color = DialogSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text("Cerrar", color = DialogSecondary)
    }
}

@Composable
private fun ReadyToInstallContent(
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        "Descarga completada",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "La actualización está lista para instalar.",
        color = DialogSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onInstall,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("Instalar", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
        Text("Más tarde", color = DialogSecondary)
    }
}

@Composable
private fun VersionColumn(
    label: String,
    version: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = DialogSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            version,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
