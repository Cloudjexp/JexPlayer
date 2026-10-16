package com.cloudjex.jexplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.AudioTags
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexArrowBackIcon

@Composable
fun LyricsScreen(
    song: Song,
    tags: AudioTags?,
    onClose: () -> Unit
) {
    val letras = tags?.letras?: "Sin letras"
    Box(
        Modifier.fillMaxSize()
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = "Fondo de la pantalla de letras.",
            modifier = Modifier.fillMaxHeight().blur(50.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = JexArrowBackIcon,
                    contentDescription = "Regresar a la biblioteca"
                )
            }
            Box(
                Modifier
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp)
                    .verticalScroll(rememberScrollState())
                    .align(Alignment.Center)
            ) {
                Text(
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(0.7f),
                    text = letras,
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}