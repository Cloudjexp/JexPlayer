package com.cloudjex.jexplayer.data.local

import android.content.Context

/** Posición de la biblioteca, independiente de la pantalla actualmente abierta. */
data class LibraryScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

class LibraryScrollPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LibraryScrollPosition = LibraryScrollPosition(
        firstVisibleItemIndex = preferences.getInt(KEY_INDEX, 0).coerceAtLeast(0),
        firstVisibleItemScrollOffset = preferences.getInt(KEY_OFFSET, 0).coerceAtLeast(0)
    )

    fun save(position: LibraryScrollPosition) {
        preferences.edit()
            .putInt(KEY_INDEX, position.firstVisibleItemIndex.coerceAtLeast(0))
            .putInt(KEY_OFFSET, position.firstVisibleItemScrollOffset.coerceAtLeast(0))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "jexplayer_library_scroll"
        const val KEY_INDEX = "first_visible_item_index"
        const val KEY_OFFSET = "first_visible_item_scroll_offset"
    }
}
