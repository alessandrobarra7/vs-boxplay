package com.boxplay.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioStorage(private val context: Context) {
    private val audioDirectory: File
        get() = File(context.filesDir, AUDIO_DIR_NAME).apply { mkdirs() }

    suspend fun copyFromUri(boxId: Int, uri: Uri): StoredAudio = withContext(Dispatchers.IO) {
        val originalFileName = readDisplayName(uri)
        val safeName = originalFileName.toSafeFileName()
        val target = File(audioDirectory, "box_${boxId}_${System.currentTimeMillis()}_$safeName")
        val temporary = File(audioDirectory, "${target.name}.tmp")

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("Não foi possível abrir o arquivo selecionado.")

            inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                }
            }

            if (temporary.length() == 0L) {
                error("O arquivo selecionado está vazio.")
            }

            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }

            StoredAudio(
                originalFileName = originalFileName,
                internalFilePath = target.absolutePath,
            )
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    /**
     * Igual a [copyFromUri], mas a origem já é um [File] local em vez de um
     * `content://` Uri — usado pelo editor multipista para entregar o
     * arquivo já mixado a um Box através do MESMO armazenamento interno que
     * o botão "Salvar" normal usa. Antes disso, o multipista apontava o
     * Box direto para o arquivo dentro de `multitrack-renders/<projectId>/`,
     * que pertence e é apagado pelo próprio módulo multipista a cada nova
     * exportação daquele projeto — fazendo o Box perder o áudio
     * silenciosamente na exportação seguinte. Copiando para cá, o Box passa
     * a ser dono do seu próprio arquivo, com o mesmo ciclo de vida de
     * qualquer áudio salvo manualmente.
     */
    suspend fun copyFromFile(boxId: Int, sourceFile: File, displayName: String): StoredAudio = withContext(Dispatchers.IO) {
        val safeName = displayName.toSafeFileName()
        val target = File(audioDirectory, "box_${boxId}_${UUID.randomUUID()}_$safeName")

        if (sourceFile.length() == 0L) {
            error("O arquivo mixado está vazio.")
        }

        try {
            sourceFile.copyTo(target, overwrite = false)
        } catch (error: Throwable) {
            target.delete()
            throw error
        }

        StoredAudio(
            originalFileName = displayName,
            internalFilePath = target.absolutePath,
        )
    }

    fun fileExists(path: String?): Boolean = path?.let { File(it).isFile } == true

    fun deleteIfInternal(path: String?) {
        if (path == null) return

        val directory = audioDirectory.canonicalFile
        val file = File(path).canonicalFile

        if (file.path.startsWith(directory.path) && file.isFile) {
            file.delete()
        }
    }

    fun readDisplayName(uri: Uri): String {
        val queriedName = queryDisplayName(uri)
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')
        return queriedName ?: fallbackName ?: "audio"
    }

    private fun queryDisplayName(uri: Uri): String? {
        // FIX: some content providers throw instead of returning null or an
        // empty cursor (SecurityException when the grant was revoked,
        // IllegalArgumentException for an unsupported/malformed uri, etc.).
        // This used to crash the app synchronously as soon as the user picked
        // such a file. Any failure here now just falls back to no queried
        // name, same as if the cursor had come back empty — readDisplayName()
        // already has a fallback chain for that case.
        return try {
            val cursor: Cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            ) ?: return null

            cursor.use {
                if (!it.moveToFirst()) return null
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex < 0) return null
                it.getString(nameIndex)?.takeIf { name -> name.isNotBlank() }
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun String.toSafeFileName(): String {
        val sanitized = replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(80)
        return sanitized.ifBlank { "audio" }
    }

    companion object {
        private const val AUDIO_DIR_NAME = "boxplay-audios"
    }
}

data class StoredAudio(
    val originalFileName: String,
    val internalFilePath: String,
)
