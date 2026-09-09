package com.boxplay.multitrack.data

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import java.io.File
import java.util.UUID

/**
 * Extrai arquivos de áudio de dentro de um pacote .rar para importação como
 * pistas separadas — mesma ideia de [MultitrackZipImporter], só que para o
 * formato RAR.
 *
 * RAR é um formato proprietário (ao contrário do .zip, que o próprio
 * Android/Kotlin já sabe ler via `java.util.zip`), então esta classe depende
 * da biblioteca `junrar` para decodificar o arquivo. É a única dependência
 * nova do módulo multipista — todo o resto do módulo (JSON, storage, .zip)
 * segue sem depender de bibliotecas externas.
 */
object MultitrackRarImporter {

    /** Um arquivo de áudio já extraído do .rar para um arquivo temporário local. */
    data class ExtractedAudio(val uri: Uri, val displayName: String)

    sealed class RarImportResult {
        data class Success(
            val entries: List<ExtractedAudio>,
            val skippedNonAudioCount: Int,
        ) : RarImportResult()

        data class Error(val message: String) : RarImportResult()
    }

    private val AUDIO_EXTENSIONS = setOf(
        "wav", "mp3", "m4a", "aac", "ogg", "flac", "wma", "aiff", "aif", "opus",
    )

    /**
     * Lê [rarUri] e extrai cada entrada de áudio reconhecida para um arquivo
     * temporário em cache (ver [cleanup]). Igual ao importador de .zip:
     * entradas sem extensão de áudio são ignoradas e contadas em
     * [RarImportResult.Success.skippedNonAudioCount].
     *
     * [onProgress] recebe a porcentagem (0-100) já extraída. Ao contrário do
     * .zip, o junrar já lê a lista completa de entradas ([Archive.fileHeaders])
     * ao abrir o arquivo, então o total é conhecido de cara — sem precisar de
     * uma primeira passada separada.
     *
     * Roda em [Dispatchers.IO] — chame a partir de uma coroutine, não na
     * thread principal.
     */
    suspend fun extract(context: Context, rarUri: Uri, onProgress: (Int) -> Unit = {}): RarImportResult {
        val stagingDir = File(context.cacheDir, "multitrack_rar_import/${UUID.randomUUID()}")
        if (!stagingDir.mkdirs()) {
            return RarImportResult.Error("Não foi possível preparar a importação do pacote .rar.")
        }

        val extracted = mutableListOf<ExtractedAudio>()
        var skipped = 0

        try {
            val input = context.contentResolver.openInputStream(rarUri)
                ?: return RarImportResult.Error("Não foi possível abrir o arquivo .rar selecionado.")

            input.use { stream ->
                Archive(stream).use { archive ->
                    val headers = archive.fileHeaders.filter { !it.isDirectory }
                    val totalAudio = headers.count { header ->
                        val name = header.fileNameString.substringAfterLast('/').substringAfterLast('\\')
                        val extension = name.substringAfterLast('.', "").lowercase()
                        name.isNotBlank() && extension in AUDIO_EXTENSIONS
                    }
                    var processedAudio = 0

                    headers.forEach { header ->
                        val entryFileName = header.fileNameString
                            .substringAfterLast('/')
                            .substringAfterLast('\\')
                        val extension = entryFileName.substringAfterLast('.', "").lowercase()

                        if (entryFileName.isBlank() || extension !in AUDIO_EXTENSIONS) {
                            skipped++
                            return@forEach
                        }

                        val outFile = File(stagingDir, "${UUID.randomUUID()}_${sanitizeEntryName(entryFileName)}")
                        outFile.outputStream().use { output -> archive.extractFile(header, output) }

                        if (outFile.length() > 0L) {
                            extracted.add(ExtractedAudio(Uri.fromFile(outFile), entryFileName))
                        } else {
                            outFile.delete()
                            skipped++
                        }

                        processedAudio++
                        if (totalAudio > 0) {
                            onProgress(((processedAudio.toFloat() / totalAudio) * 100).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } catch (error: Exception) {
            cleanup(extracted)
            stagingDir.deleteRecursively()
            return RarImportResult.Error(error.message ?: "Falha ao ler o arquivo .rar selecionado.")
        }

        if (extracted.isEmpty()) {
            stagingDir.deleteRecursively()
            return RarImportResult.Error("Nenhum arquivo de áudio reconhecido dentro do .rar.")
        }

        onProgress(100)
        return RarImportResult.Success(extracted, skipped)
    }

    /**
     * Remove os arquivos temporários extraídos por [extract], igual a
     * [MultitrackZipImporter.cleanup].
     */
    fun cleanup(entries: List<ExtractedAudio>) {
        val parentDirs = mutableSetOf<File>()
        entries.forEach { entry ->
            val path = entry.uri.path ?: return@forEach
            val file = File(path)
            file.parentFile?.let { parentDirs.add(it) }
            file.delete()
        }
        parentDirs.forEach { it.deleteRecursively() }
    }

    private fun sanitizeEntryName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "audio" }
    }
}
