package com.cloudjex.jexplayer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun jexVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

val JexArrowBackIcon = jexVector(
    "JexArrowBack",
    "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
)

val JexMoreIcon = jexVector(
    "JexMore",
    "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 " +
            "2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
)

val JexPlayIcon = jexVector(
    "JexPlay",
    "M8 5v14l11-7z"
)

val JexPauseIcon = jexVector(
    "JexPause",
    "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
)

val JexSkipPreviousIcon = jexVector(
    "JexSkipPrevious",
    "M6 6h2v12H6zm3.5 6l8.5 6V6z"
)

val JexSkipNextIcon = jexVector(
    "JexSkipNext",
    "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"
)

val JexShuffleIcon = jexVector(
    "JexShuffle",
    "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
            "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 " +
            "2.04-3.13-3.13z"
)

val JexRepeatIcon = jexVector(
    "JexRepeat",
    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"
)

val JexRepeatOneIcon = jexVector(
    "JexRepeatOne",
    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"
)

val JexMusicIcon = jexVector(
    "JexMusic",
    "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 " +
            "4-4V7h4V3h-6z"
)

val JexLyricsIcon = jexVector(
    "JexLyrics",
    "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm1 7V3.5L18.5 9H15z " +
            "M16 17H8v-2h8v2zm0-4H8v-2h8v2zm-3-4H8V7h5v2z"
)
