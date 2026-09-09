package com.boxplay.multitrack.data

import java.io.File

/**
 * Regras de nomeação e segurança de caminhos para os arquivos de áudio do
 * editor multipista. Mantido separado de [MultitrackAudioStorage] para que
 * essas regras (sanitização de nome, verificação de canonicalPath) possam
 * ser testadas em JVM puro, sem depender de um Android Context (ver
 * especificação, seção 76 — "Segurança de path").
 */
object MultitrackAudioPaths {
    private const val MAX_SANITIZED_LENGTH = 80

    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(MAX_SANITIZED_LENGTH)
        return sanitized.ifBlank { "audio" }
    }

    fun trackFileName(trackId: String, originalFileName: String): String =
        "${trackId}_${sanitizeFileName(originalFileName)}"

    /**
     * Retorna true somente se [file] estiver de fato dentro de [root], usando
     * caminhos canônicos (resolve ".." e symlinks). Deve ser chamado antes de
     * qualquer exclusão, para impedir que um path arbitrário salvo em um
     * projeto JSON apague algo fora da pasta esperada.
     */
    fun isWithin(root: File, file: File): Boolean {
        return try {
            val canonicalRoot = root.canonicalFile
            val canonicalFile = file.canonicalFile
            canonicalFile.path == canonicalRoot.path ||
                canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
        } catch (error: Exception) {
            false
        }
    }
}
