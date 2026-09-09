package com.boxplay.multitrack.model

/**
 * Roteamento de saída de uma pista do editor multipista (ver especificação,
 * seção 7). LEFT e RIGHT enviam a pista somente ao canal correspondente;
 * CENTER envia aos dois canais com o mesmo ganho.
 */
enum class TrackRouting {
    LEFT,
    CENTER,
    RIGHT,
    ;

    companion object {
        fun fromNameOrNull(value: String?): TrackRouting? {
            if (value == null) return null
            return values().firstOrNull { it.name == value }
        }
    }
}
