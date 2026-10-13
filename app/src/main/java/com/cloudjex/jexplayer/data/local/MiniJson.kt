package com.cloudjex.jexplayer.data.local

internal object MiniJson {

    fun writeString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (character in value) {
            when {
                character == '"' -> builder.append("\\\"")
                character == '\\' -> builder.append("\\\\")
                character == '\n' -> builder.append("\\n")
                character == '\r' -> builder.append("\\r")
                character == '\t' -> builder.append("\\t")
                character == '\b' -> builder.append("\\b")
                character == '\u000C' -> builder.append("\\f")
                character < ' ' ->
                    builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else -> builder.append(character)
            }
        }
        builder.append('"')
        return builder.toString()
    }
    fun parse(text: String): Any? = JsonParser(text).parseAll()
}

private class JsonParser(private val text: String) {

    private var index = 0

    fun parseAll(): Any? {
        val value = parseValue()
        skipWhitespace()
        require(index == text.length) { "Contenido extra tras el JSON en posición $index" }
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        require(index < text.length) { "JSON vacío o truncado" }

        return when (val character = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> {
                require(character == '-' || character.isDigit()) {
                    "Carácter inesperado '$character' en posición $index"
                }
                parseNumber()
            }
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()

        skipWhitespace()
        if (peek() == '}') {
            index++
            return result
        }

        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            result[key] = parseValue()
            skipWhitespace()

            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return result
                }

                else -> throw IllegalArgumentException("Se esperaba ',' o '}' en posición $index")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val result = ArrayList<Any?>()

        skipWhitespace()
        if (peek() == ']') {
            index++
            return result
        }

        while (true) {
            result.add(parseValue())
            skipWhitespace()

            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return result
                }

                else -> throw IllegalArgumentException("Se esperaba ',' o ']' en posición $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()

        while (index < text.length) {
            val character = text[index++]
            when {
                character == '"' -> return builder.toString()
                character == '\\' -> {
                    require(index < text.length) { "Escape incompleto" }
                    when (val escaped = text[index++]) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            require(index + 4 <= text.length) { "Secuencia \\u incompleta" }
                            builder.append(text.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }

                        else -> throw IllegalArgumentException("Escape inválido '\\$escaped'")
                    }
                }

                else -> builder.append(character)
            }
        }

        throw IllegalArgumentException("String sin cerrar")
    }

    private fun parseNumber(): Any {
        val start = index

        if (peek() == '-') index++
        while (index < text.length && text[index].isDigit()) index++

        var isFloatingPoint = false
        if (index < text.length && text[index] == '.') {
            isFloatingPoint = true
            index++
            while (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            isFloatingPoint = true
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            while (index < text.length && text[index].isDigit()) index++
        }

        val token = text.substring(start, index)
        require(token.isNotEmpty() && token != "-") { "Número inválido en posición $start" }

        return if (isFloatingPoint) {
            token.toDouble()
        } else {
            token.toLongOrNull() ?: throw IllegalArgumentException("Número inválido '$token'")
        }
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        require(text.startsWith(literal, index)) { "Literal inválido en posición $index" }
        index += literal.length
        return value
    }

    private fun peek(): Char =
        if (index < text.length) text[index]
        else throw IllegalArgumentException("Fin inesperado del JSON")

    private fun expect(expected: Char) {
        require(index < text.length && text[index] == expected) {
            "Se esperaba '$expected' en posición $index"
        }
        index++
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}
