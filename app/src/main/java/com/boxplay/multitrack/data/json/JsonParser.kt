package com.boxplay.multitrack.data.json

class JsonParseException(message: String) : Exception(message)

/** Faz o parse de um texto JSON completo, lançando [JsonParseException] em caso de conteúdo inválido. */
fun parseJson(text: String): JsonValue {
    val parser = JsonParserImpl(text)
    val value = parser.parseValue()
    parser.skipWhitespace()
    if (!parser.isAtEnd()) {
        throw JsonParseException("Conteúdo inesperado após o JSON.")
    }
    return value
}

private class JsonParserImpl(private val text: String) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= text.length

    fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    fun parseValue(): JsonValue {
        skipWhitespace()
        if (isAtEnd()) throw JsonParseException("JSON vazio ou incompleto.")
        return when (text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun expect(char: Char) {
        if (isAtEnd() || text[pos] != char) {
            throw JsonParseException("Esperado '$char' na posição $pos.")
        }
        pos++
    }

    private fun parseObject(): JsonValue.Obj {
        expect('{')
        val obj = JsonValue.Obj()
        skipWhitespace()
        if (!isAtEnd() && text[pos] == '}') {
            pos++
            return obj
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            obj.entries[key] = value
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Objeto JSON incompleto.")
            when (text[pos]) {
                ',' -> {
                    pos++
                }
                '}' -> {
                    pos++
                    break
                }
                else -> throw JsonParseException("Esperado ',' ou '}' na posição $pos.")
            }
        }
        return obj
    }

    private fun parseArray(): JsonValue.Arr {
        expect('[')
        val arr = JsonValue.Arr()
        skipWhitespace()
        if (!isAtEnd() && text[pos] == ']') {
            pos++
            return arr
        }
        while (true) {
            val value = parseValue()
            arr.items.add(value)
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Array JSON incompleto.")
            when (text[pos]) {
                ',' -> {
                    pos++
                }
                ']' -> {
                    pos++
                    break
                }
                else -> throw JsonParseException("Esperado ',' ou ']' na posição $pos.")
            }
        }
        return arr
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (isAtEnd()) throw JsonParseException("String JSON incompleta.")
            val c = text[pos]
            pos++
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (isAtEnd()) throw JsonParseException("Escape incompleto em string JSON.")
                    val escaped = text[pos]
                    pos++
                    when (escaped) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) throw JsonParseException("Escape unicode incompleto.")
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16)
                                ?: throw JsonParseException("Escape unicode inválido: \\u$hex")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw JsonParseException("Escape inválido: \\$escaped")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseBoolean(): JsonValue.Bool {
        return when {
            text.startsWith("true", pos) -> {
                pos += 4
                JsonValue.Bool(true)
            }
            text.startsWith("false", pos) -> {
                pos += 5
                JsonValue.Bool(false)
            }
            else -> throw JsonParseException("Valor booleano inválido na posição $pos.")
        }
    }

    private fun parseNull(): JsonValue {
        if (text.startsWith("null", pos)) {
            pos += 4
            return JsonValue.Null
        }
        throw JsonParseException("Valor inválido na posição $pos.")
    }

    private fun parseNumber(): JsonValue.Num {
        val start = pos
        if (!isAtEnd() && text[pos] == '-') pos++
        while (!isAtEnd() && text[pos].isDigit()) pos++
        if (!isAtEnd() && text[pos] == '.') {
            pos++
            while (!isAtEnd() && text[pos].isDigit()) pos++
        }
        if (!isAtEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
            pos++
            if (!isAtEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
            while (!isAtEnd() && text[pos].isDigit()) pos++
        }
        if (pos == start) throw JsonParseException("Número inválido na posição $pos.")
        return JsonValue.Num(text.substring(start, pos))
    }
}
