package com.cloudjex.jexplayer.viewmodel

/** Returns the explicit timeline index for a media id, or -1 when it is absent. */
internal fun findMediaIdIndex(mediaIds: List<String>, mediaId: String): Int =
    mediaIds.indexOfFirst { it == mediaId }
