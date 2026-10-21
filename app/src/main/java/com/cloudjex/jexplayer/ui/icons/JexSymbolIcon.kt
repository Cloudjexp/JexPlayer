package com.cloudjex.jexplayer.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.cloudjex.jexplayer.R

/** Símbolos Rounded oficiales empaquetados como vectores Android locales. */
enum class JexSymbol(@param:DrawableRes val resourceId: Int) {
    ArrowBack(R.drawable.ms_arrow_back),
    Close(R.drawable.ms_close),
    Favorite(R.drawable.ms_favorite),
    Filter(R.drawable.ms_filter_list),
    Folder(R.drawable.ms_folder),
    LibraryMusic(R.drawable.ms_library_music),
    Lyrics(R.drawable.ms_lyrics),
    MusicNote(R.drawable.ms_music_note),
    Pause(R.drawable.ms_pause),
    PlayArrow(R.drawable.ms_play_arrow),
    QueueMusic(R.drawable.ms_queue_music),
    Repeat(R.drawable.ms_repeat),
    RepeatOne(R.drawable.ms_repeat_one),
    Search(R.drawable.ms_search),
    Settings(R.drawable.ms_settings),
    Shuffle(R.drawable.ms_shuffle),
    SkipNext(R.drawable.ms_skip_next),
    SkipPrevious(R.drawable.ms_skip_previous)
}

@Composable
fun JexSymbolIcon(
    symbol: JexSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Icon(
        painter = painterResource(symbol.resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
