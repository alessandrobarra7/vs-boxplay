package com.boxplay.multitrack.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Armazenamento interno dos áudios do editor multipista.
 *
 * Independente do LocalAudioStorage do soundboard (ver especificação, seção
 * 20): cada projeto tem sua própria subpasta em
 * `context.filesDir/multitrack-audios/<projectId>/`, e nada nesta classe
 * jamais lê ou apaga arquivos de `boxplay-audios/`.
 */
class MultitrackAudioStorage(private val context: Context) {

    private val rootDir: File
        get() = File(context.filesDir, ROOT_DIR_NAME).apply { mkdirs() }

    private fun projectDir(projectId: String): File = File(rootDir, projectId).apply { mkdirs() }

    sealed class ImportResult {
        data class Success(val internalFilePath: String, val originalFileName: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    suspend fun copyFromUri(projectId: String, trackId: String, uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val originalFileName = readDisplayName(uri)
            val target = File(projectDir(projectId), MultitrackAudioPaths.trackFileName(trackId, originalFileName))
            val temporary = File(target.parentFile, "${target.name}.tmp")

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult.Error("Não foi possível abrir o arquivo selecionado.")

                inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output)
                    }
                }

                if (temporary.length() == 0L) {
                    temporary.delete()
                    return@withContext ImportResult.Error("O arquivo selecionado está vazio.")
                }

                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }

                ImportResult.Success(
                    internalFilePath = target.absolutePath,
                    originalFileName = originalFileName,
                )
            } catch (error: Exception) {
                temporary.delete()
                ImportResult.Error(error.message ?: "Falha ao importar o arquivo de áudio.")
            }
        }

    fun fileExists(path: String?): Boolean = path?.let { File(it).isFile } == true

    /** Exclui um arquivo de pista somente se ele estiver dentro de multitrack-audios/. */
    fun deleteTrackFile(path: String?): Boolean {
        if (path == null) return true
        val file = File(path)
        if (!MultitrackAudioPaths.isWithin(rootDir, file)) return false
        return !file.exists() || file.delete()
    }

    /** Exclui toda a pasta de áudios de um projeto (usado ao apagar o projeto inteiro). */
    fun deleteProjectAudioDir(projectId: String): Boolean {
        val dir = File(rootDir, projectId)
        if (!MultitrackAudioPaths.isWithin(rootDir, dir)) return false
        return !dir.exists() || dir.deleteRecursively()
    }

    private fun readDisplayName(uri: Uri): String {
        val queried = queryDisplayName(uri)
        val fallback = uri.lastPathSegment?.substringAfterLast('/')
        return queried ?: fallback ?: "audio"
    }

    private fun queryDisplayName(uri: Uri): String? {
        // Alguns content providers lançam exceção em vez de retornar null ou
        // um cursor vazio (SecurityException quando o grant foi revogado,
        // IllegalArgumentException para uma uri malformada, etc.) — mesmo
        // cuidado já aplicado no LocalAudioStorage do soundboard.
        return try {
            val cursor = context.contentResolver.query(
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

    companion object {
        private const val ROOT_DIR_NAME = "multitrack-audios"
    }
}
