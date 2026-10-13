package com.cloudjex.jexplayer.data.model

/**
 * Carpeta de música seleccionada manualmente por el usuario mediante el
 * selector oficial de Android (ACTION_OPEN_DOCUMENT_TREE).
 *
 * Solo almacena la información mínima necesaria en esta fase: la URI del árbol
 * (con su permiso persistente de lectura) y un nombre para mostrar. Todavía no
 * se indexa su contenido.
 */
data class MusicFolder(
    val uri: String,
    val name: String
)
