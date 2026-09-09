package com.boxplay.multitrack.data.json

/** Serializa um [JsonValue] para texto JSON compacto, com escaping seguro de strings. */
fun writeJson(value: JsonValue): String {
    val sb = StringBuilder()
    writeValue(value, sb)
    return sb.toString()
}

private fun writeValue(value: JsonValue, sb: StringBuilder) {
    when (value) {
        is JsonValue.Obj -> {
            sb.append('{')
            var first = true
            for ((key, v) in value.entries) {
                if (!first) sb.append(',')
                first = false
                writeString(key, sb)
                sb.append(':')
                writeValue(v, sb)
            }
            sb.append('}')
        }
        is JsonValue.Arr -> {
            sb.append('[')
            var first = true
            for (item in value.items) {
                if (!first) sb.append(',')
                first = false
                writeValue(item, sb)
            }
            sb.append(']')
        }
        is JsonValue.Str -> writeString(value.value, sb)
        is JsonValue.Num -> sb.append(value.value)
        is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
        JsonValue.Null -> sb.append("null")
    }
}

private fun writeString(text: String, sb: StringBuilder) {
    sb.append('"')
    for (c in text) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c.code < 0x20) {
                sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                sb.append(c)
            }
        }
    }
    sb.append('"')
}
