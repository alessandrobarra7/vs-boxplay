package com.boxplay.multitrack.data.json

/**
 * Modelo mínimo de valores JSON usado pelo editor multipista.
 *
 * Escrito manualmente (sem depender de org.json, Gson, Moshi ou
 * kotlinx.serialization) para que a serialização dos projetos multipista
 * continue 100% testável em testes JVM puros (`app/src/test`), do mesmo jeito
 * que o restante dos testes do projeto (ex.: PurchaseEntitlementTest), sem
 * exigir Robolectric ou um Android Context. org.json, em particular, é
 * stubado no ambiente de teste unitário puro do Android e lançaria exceção
 * em tempo de teste.
 */
sealed class JsonValue {
    data class Obj(val entries: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue() {
        fun put(key: String, value: JsonValue): Obj {
            entries[key] = value
            return this
        }

        fun put(key: String, value: String?): Obj {
            entries[key] = if (value == null) Null else Str(value)
            return this
        }

        fun put(key: String, value: Long): Obj {
            entries[key] = Num(value.toString())
            return this
        }

        fun put(key: String, value: Long?): Obj {
            entries[key] = if (value == null) Null else Num(value.toString())
            return this
        }

        fun put(key: String, value: Int?): Obj {
            entries[key] = if (value == null) Null else Num(value.toString())
            return this
        }

        fun put(key: String, value: Float): Obj {
            entries[key] = Num(value.toString())
            return this
        }

        fun put(key: String, value: Boolean): Obj {
            entries[key] = Bool(value)
            return this
        }

        fun str(key: String): String? = (entries[key] as? Str)?.value
        fun long(key: String): Long? = (entries[key] as? Num)?.value?.toLongOrNull()
        fun int(key: String): Int? = (entries[key] as? Num)?.value?.toIntOrNull()
        fun float(key: String): Float? = (entries[key] as? Num)?.value?.toFloatOrNull()
        fun bool(key: String): Boolean? = (entries[key] as? Bool)?.value
        fun arr(key: String): Arr? = entries[key] as? Arr
        fun obj(key: String): Obj? = entries[key] as? Obj
    }

    data class Arr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue() {
        companion object {
            /**
             * Constrói um Arr a partir de uma List<JsonValue> comum.
             *
             * Necessário porque MutableList é invariante em Kotlin: uma
             * List<JsonValue.Obj>, por exemplo, não pode ser passada
             * diretamente ao construtor primário (que espera
             * MutableList<JsonValue>). Como o parâmetro [items] aqui é
             * List<JsonValue> (covariante), o upcast acontece na chamada, e
             * só então convertemos para uma nova lista mutável.
             */
            fun of(items: List<JsonValue>): Arr = Arr(ArrayList(items))
        }
    }
    data class Str(val value: String) : JsonValue()
    data class Num(val value: String) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    object Null : JsonValue()
}
