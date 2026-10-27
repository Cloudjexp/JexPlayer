package com.cloudjex.jexplayer.data.local

import android.content.Context
import android.content.SharedPreferences
import com.cloudjex.jexplayer.data.model.LyricsColorPreset
import com.cloudjex.jexplayer.data.model.LyricsFontPreset
import com.cloudjex.jexplayer.data.model.LyricsLineMotion
import com.cloudjex.jexplayer.data.model.LyricsSettings
import org.json.JSONObject

/**
 * Preferencias locales de la pantalla de letras.
 *
 * Se guarda la apariencia y, por canción, la fuente que el usuario eligió
 * manualmente. Si no hay una elección guardada, el ViewModel usa la letra
 * incrustada y luego la mejor coincidencia externa.
 */
internal class LyricsSettingsStore private constructor(
    private val preferences: SharedPreferences
) {

    fun loadSettings(): LyricsSettings = LyricsSettings(
        colorPreset = LyricsColorPreset.fromStorageKey(
            preferences.getString(KEY_COLOR, null)
        ),
        fontPreset = LyricsFontPreset.fromStorageKey(
            preferences.getString(KEY_FONT, null)
        ),
        italic = preferences.getBoolean(KEY_ITALIC, true),
        fontSizeSp = preferences.getInt(
            KEY_FONT_SIZE,
            LyricsSettings.DEFAULT_FONT_SIZE_SP
        ),
        showTranslation = preferences.getBoolean(KEY_SHOW_TRANSLATION, false),
        lineMotion = LyricsLineMotion.fromStorageKey(
            preferences.getString(KEY_LINE_MOTION, null)
        )
    ).normalizada()

    fun saveSettings(settings: LyricsSettings) {
        val normalizada = settings.normalizada()
        preferences.edit()
            .putString(KEY_COLOR, normalizada.colorPreset.storageKey)
            .putString(KEY_FONT, normalizada.fontPreset.storageKey)
            .putBoolean(KEY_ITALIC, normalizada.italic)
            .putInt(KEY_FONT_SIZE, normalizada.fontSizeSp)
            .putBoolean(KEY_SHOW_TRANSLATION, normalizada.showTranslation)
            .putString(KEY_LINE_MOTION, normalizada.lineMotion.storageKey)
            .apply()
    }

    fun selectedSource(songId: String): String? = synchronized(lock) {
        cargarFuentes()[songId]
    }

    fun saveSelectedSource(songId: String, sourceId: String) {
        synchronized(lock) {
            val fuentes = cargarFuentes()
            fuentes[songId] = sourceId
            guardarFuentes(fuentes)
        }
    }

    fun clearSelectedSource(songId: String) {
        synchronized(lock) {
            val fuentes = cargarFuentes()
            if (fuentes.remove(songId) != null) {
                guardarFuentes(fuentes)
            }
        }
    }

    private fun cargarFuentes(): MutableMap<String, String> {
        val crudo = preferences.getString(KEY_SOURCES, null)
            ?.takeIf { it.isNotBlank() }
            ?: return mutableMapOf()

        return runCatching {
            val json = JSONObject(crudo)
            buildMap {
                val claves = json.keys()
                while (claves.hasNext()) {
                    val clave = claves.next()
                    val valor = json.optString(clave)
                    if (clave.isNotBlank() && valor.isNotBlank()) {
                        put(clave, valor)
                    }
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun guardarFuentes(fuentes: Map<String, String>) {
        val json = JSONObject()
        fuentes.forEach { (cancion, fuente) -> json.put(cancion, fuente) }
        preferences.edit()
            .putString(KEY_SOURCES, json.toString())
            .apply()
    }

    companion object {
        private val lock = Any()
        private const val KEY_COLOR = "lyrics_color"
        private const val KEY_FONT = "lyrics_font"
        private const val KEY_ITALIC = "lyrics_italic"
        private const val KEY_FONT_SIZE = "lyrics_font_size"
        private const val KEY_SHOW_TRANSLATION = "lyrics_show_translation"
        private const val KEY_LINE_MOTION = "lyrics_line_motion"
        private const val KEY_SOURCES = "lyrics_sources"
        private const val PREFERENCES_NAME = "jexplayer_lyrics"

        fun from(context: Context): LyricsSettingsStore =
            LyricsSettingsStore(
                context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
            )
    }
}
