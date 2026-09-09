package com.boxplay.multitrack.util

import java.util.Locale

/**
 * Formatação de tempo para a UI do editor multipista (especificação, seção
 * 90): MM:SS.mmm na maioria dos projetos, HH:MM:SS.mmm quando passa de uma
 * hora. Internamente o tempo é sempre Long em microssegundos — nunca Float.
 */
object MultitrackTimeFormat {
    fun format(durationUs: Long): String {
        val safeUs = durationUs.coerceAtLeast(0L)
        val totalMs = safeUs / 1000L
        val ms = totalMs % 1000L
        val totalSeconds = totalMs / 1000L
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        val minutes = totalMinutes % 60L
        val hours = totalMinutes / 60L

        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
        } else {
            String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, ms)
        }
    }
}
