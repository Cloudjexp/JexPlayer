package com.cloudjex.jexplayer.data.lyrics

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Parser de TTML (Apple Music, AMLL, etc.).
 *
 * Solo se usan tiempos por línea y por palabra/sílaba. Se elige una única pista
 * de idioma para no mezclar letra original y traducciones, y se descartan las
 * voces de fondo cuando vienen marcadas con `ttm:role`.
 */
object TtmlParser {

    private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
    private const val TTM_NS = "http://www.w3.org/ns/ttml#metadata"
    private val ELEMENTOS_IGNORADOS = setOf("metadata", "style", "styling", "layout")

    fun parse(xml: String): List<LineaCancion> {
        val documento = parsearXml(xml) ?: return emptyList()
        val candidatos = mutableListOf<ParagraphCandidate>()
        val elementos = documento.getElementsByTagName("*")

        for (indice in 0 until elementos.length) {
            val elemento = elementos.item(indice) as? Element ?: continue
            if (elemento.localNameSeguro() != "p") continue
            if (estaDentroDeMetadata(elemento)) continue

            val rol = rolDe(elemento)
            if (rol.contains("translation") ||
                rol.contains("background") ||
                rol == "x-bg"
            ) {
                continue
            }

            val inicio = parsearTiempo(elemento.atributo("begin"))
                ?: primerInicio(elemento)
                ?: continue

            candidatos += ParagraphCandidate(
                elemento = elemento,
                idioma = idiomaDe(elemento),
                inicio = inicio,
                orden = candidatos.size
            )
        }

        if (candidatos.isEmpty()) return emptyList()

        return elegirPista(candidatos)
            .mapNotNull { it.aLinea() }
            .sortedBy { it.tiempo }
    }

    private data class ParagraphCandidate(
        val elemento: Element,
        val idioma: String?,
        val inicio: Long,
        val orden: Int
    )

    private data class RawSegment(val tiempo: Long, val texto: StringBuilder)

    private data class TextoRecolectado(
        val segmentos: List<SegmentoLetra>,
        val tieneTiemposDePalabra: Boolean
    )

    private fun ParagraphCandidate.aLinea(): LineaCancion? {
        val recolectado = recolectarTexto(elemento, inicio)
        val segmentos = recolectado.segmentos
        if (segmentos.isEmpty()) return null

        val texto = segmentos.joinToString("") { it.texto }.trim()
        if (texto.isEmpty()) return null

        val fin = parsearTiempo(elemento.atributo("end"))
            ?.takeIf { it > inicio }

        return LineaCancion(
            tiempo = inicio,
            texto = texto,
            segmentos = if (recolectado.tieneTiemposDePalabra) segmentos else emptyList(),
            finMs = fin
        )
    }

    private fun recolectarTexto(parrafo: Element, inicioParrafo: Long): TextoRecolectado {
        val crudos = mutableListOf<RawSegment>()
        var tieneTiemposDePalabra = false

        fun agregar(texto: String, tiempo: Long) {
            if (texto.isEmpty()) return
            val ultimo = crudos.lastOrNull()
            if (ultimo != null && ultimo.tiempo == tiempo) {
                ultimo.texto.append(texto)
            } else {
                crudos += RawSegment(tiempo, StringBuilder(texto))
            }
        }

        /**
         * Recorre los hijos en orden y devuelve el último tiempo visto. Así un
         * texto que va después de un `<span begin="...">` hereda ese tiempo.
         */
        fun visitarHijos(nodo: Node, tiempoInicial: Long): Long {
            var tiempoActual = tiempoInicial
            val hijos = nodo.childNodes

            for (indice in 0 until hijos.length) {
                val hijo = hijos.item(indice)
                when (hijo.nodeType) {
                    Node.TEXT_NODE -> {
                        val valor = hijo.nodeValue ?: ""
                        if (valor.isNotEmpty()) {
                            if (valor.isBlank()) {
                                // Un nodo de espacios entre dos spans suele ser
                                // el separador de palabras, incluso si el XML
                                // viene indentado con saltos de línea.
                                agregar(" ", tiempoActual)
                            } else {
                                agregar(
                                    valor.replace(Regex("\\s+"), " "),
                                    tiempoActual
                                )
                            }
                        }
                    }

                    Node.ELEMENT_NODE -> {
                        val elemento = hijo as Element
                        val nombre = elemento.localNameSeguro()
                        when {
                            nombre in ELEMENTOS_IGNORADOS -> Unit
                            nombre == "br" -> agregar(" ", tiempoActual)
                            else -> {
                                val crudo = elemento.atributo("begin")
                                val inicioSpan = parsearTiempo(crudo)?.let { valor ->
                                    // Los offsets sin `:` son relativos al
                                    // inicio del elemento padre en TTML.
                                    if (crudo?.contains(':') == false &&
                                        valor < tiempoInicial
                                    ) {
                                        tiempoInicial + valor
                                    } else {
                                        valor
                                    }
                                }
                                if (inicioSpan != null) {
                                    tieneTiemposDePalabra = true
                                }
                                tiempoActual = visitarHijos(
                                    elemento,
                                    inicioSpan ?: tiempoActual
                                )
                            }
                        }
                    }
                }
            }

            return tiempoActual
        }

        visitarHijos(parrafo, inicioParrafo)
        return TextoRecolectado(
            segmentos = limpiarSegmentos(crudos),
            tieneTiemposDePalabra = tieneTiemposDePalabra
        )
    }

    private fun limpiarSegmentos(crudos: List<RawSegment>): List<SegmentoLetra> {
        val limpios = mutableListOf<SegmentoLetra>()
        var anteriorTerminaEnEspacio = false

        crudos.forEach { crudo ->
            var texto = crudo.texto.toString().replace(Regex("\\s+"), " ")
            if (anteriorTerminaEnEspacio) texto = texto.trimStart()
            if (limpios.isEmpty()) texto = texto.trimStart()
            if (texto.isEmpty()) return@forEach

            limpios += SegmentoLetra(crudo.tiempo, texto)
            anteriorTerminaEnEspacio = texto.endsWith(' ')
        }

        if (limpios.isNotEmpty()) {
            val ultimo = limpios.last()
            limpios[limpios.lastIndex] = ultimo.copy(texto = ultimo.texto.trimEnd())
        }

        return limpios.filter { it.texto.isNotEmpty() }
    }

    private fun elegirPista(candidatos: List<ParagraphCandidate>): List<ParagraphCandidate> {
        val grupos = candidatos.groupBy { it.idioma.orEmpty() }
        if (grupos.size <= 1) return candidatos

        val idiomaDispositivo = Locale.getDefault().language.lowercase(Locale.ROOT)
        val preferido = grupos.entries.firstOrNull { (idioma, _) ->
            idioma.isNotBlank() &&
                idioma.lowercase(Locale.ROOT).substringBefore('-') == idiomaDispositivo
        }?.value

        return preferido ?: grupos.maxByOrNull { it.value.size }?.value ?: candidatos
    }

    private fun idiomaDe(elemento: Element): String? {
        var actual: Node? = elemento
        while (actual is Element) {
            val idioma = actual.atributoNs(XML_NS, "lang")
                ?: actual.atributo("xml:lang")
                ?: actual.atributo("lang")
            if (!idioma.isNullOrBlank()) return idioma
            actual = actual.parentNode
        }
        return null
    }

    private fun rolDe(elemento: Element): String {
        var actual: Node? = elemento
        while (actual is Element) {
            val rol = actual.atributoNs(TTM_NS, "role")
                ?: actual.atributo("ttm:role")
            if (!rol.isNullOrBlank()) return rol.lowercase(Locale.ROOT)
            actual = actual.parentNode
        }
        return ""
    }

    private fun estaDentroDeMetadata(elemento: Element): Boolean {
        var actual: Node? = elemento.parentNode
        while (actual is Element) {
            if (actual.localNameSeguro() == "metadata") return true
            actual = actual.parentNode
        }
        return false
    }

    private fun primerInicio(elemento: Element): Long? {
        parsearTiempo(elemento.atributo("begin"))?.let { return it }

        val hijos = elemento.childNodes
        for (indice in 0 until hijos.length) {
            val hijo = hijos.item(indice) as? Element ?: continue
            primerInicio(hijo)?.let { return it }
        }
        return null
    }

    private fun parsearXml(xml: String): Document? = runCatching {
        val fabrica = DocumentBuilderFactory.newInstance()
        fabrica.isNamespaceAware = true
        fabrica.isExpandEntityReferences = false
        runCatching {
            fabrica.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
            )
        }
        runCatching {
            fabrica.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
            )
        }
        runCatching {
            fabrica.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
            )
        }
        fabrica.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }.getOrNull()

    /**
     * Convierte tiempos TTML (`hh:mm:ss.mmm`, `mm:ss.mmm`, `1.5s`, `500ms`…)
     * a milisegundos.
     */
    private fun parsearTiempo(valor: String?): Long? {
        val texto = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizado = texto.replace(',', '.')

        parsearOffset(normalizado)?.let { return it }

        val partes = normalizado.split(':')
        return when (partes.size) {
            3 -> {
                val horas = partes[0].toLongOrNull() ?: return null
                val minutos = partes[1].toLongOrNull() ?: return null
                val segundos = partes[2].toDoubleOrNull() ?: return null
                (horas * 3_600L + minutos * 60L) * 1_000L +
                    (segundos * 1_000.0).roundToLong()
            }

            2 -> {
                val minutos = partes[0].toLongOrNull() ?: return null
                val segundos = partes[1].toDoubleOrNull() ?: return null
                minutos * 60_000L + (segundos * 1_000.0).roundToLong()
            }

            1 -> (partes[0].toDoubleOrNull()?.times(1_000.0))?.roundToLong()

            else -> null
        }
    }

    private fun parsearOffset(valor: String): Long? {
        val minusculas = valor.lowercase(Locale.ROOT)
        val numero = minusculas.dropLastWhile { it.isLetter() }
            .toDoubleOrNull()
            ?: return null

        return when {
            minusculas.endsWith("ms") -> numero.roundToLong()
            minusculas.endsWith("s") -> (numero * 1_000.0).roundToLong()
            minusculas.endsWith("m") -> (numero * 60_000.0).roundToLong()
            minusculas.endsWith("h") -> (numero * 3_600_000.0).roundToLong()
            minusculas.endsWith("f") -> (numero / 30.0 * 1_000.0).roundToLong()
            else -> null
        }
    }

    private fun Element.atributo(nombre: String): String? =
        getAttribute(nombre).takeIf { it.isNotBlank() }

    private fun Element.atributoNs(espacio: String, nombre: String): String? =
        getAttributeNS(espacio, nombre).takeIf { it.isNotBlank() }

    private fun Node.localNameSeguro(): String =
        (localName ?: nodeName.substringAfter(':')).lowercase(Locale.ROOT)
}
