package com.boxplay.multitrack.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Extrai arquivos de áudio de dentro de um pacote .zip para importação
 * como pistas separadas.
 *
 * Pacotes de multipista (stems) costumam ser distribuídos como um único
 * .zip contendo um arquivo de áudio por pista — é assim que a maioria dos
 * programas de música já recebe e organiza esse tipo de material. Esta
 * classe reproduz esse comportamento dentro do editor: cada entrada de
 * áudio reconhecida dentro do .zip vira uma pista, usando exatamente o
 * mesmo caminho de importação de um arquivo avulso ([MultitrackAudioStorage.copyFromUri]).
 *
 * Não usa nenhuma biblioteca nova — `java.util.zip` já faz parte do
 * Android/Kotlin, seguindo o mesmo princípio de dependência zero já usado
 * no resto do módulo multipista (ver [com.boxplay.multitrack.data.json]).
 */
object MultitrackZipImporter {

    /** Um arquivo de áudio já extraído do .zip para um arquivo temporário local. */
    data class ExtractedAudio(val uri: Uri, val displayName: String)

    sealed class ZipImportResult {
        data class Success(
            val entries: List<ExtractedAudio>,
            val skippedNonAudioCount: Int,
        ) : ZipImportResult()

        data class Error(val message: String) : ZipImportResult()
    }

    private val AUDIO_EXTENSIONS = setOf(
        "wav", "mp3", "m4a", "aac", "ogg", "flac", "wma", "aiff", "aif", "opus",
    )

    /**
     * Lê [zipUri] e extrai cada entrada de áudio reconhecida para um arquivo
     * temporário em cache (ver [cleanup]). Entradas que não têm extensão de
     * áudio (pastas, notas de texto, arquivos de projeto de outro programa,
     * arte de capa etc.) são ignoradas e contadas em [ZipImportResult.Success.skippedNonAudioCount].
     *
     * [onProgress] recebe a porcentagem (0-100) já extraída, calculada a
     * partir de uma primeira passada rápida que só conta quantas entradas de
     * áudio existem (sem extrair nada ainda) — pedido do usuário para a
     * barra de carregamento mostrar o quanto falta, não só que está rodando.
     *
     * Roda em [Dispatchers.IO] — chame a partir de uma coroutine, não na
     * thread principal.
     */
    suspend fun extract(context: Context, zipUri: Uri, onProgress: (Int) -> Unit = {}): ZipImportResult {
        val stagingDir = File(context.cacheDir, "multitrack_zip_import/${UUID.randomUUID()}")
        if (!stagingDir.mkdirs()) {
            return ZipImportResult.Error("Não foi possível preparar a importação do pacote .zip.")
        }

        // Primeira passada: só conta quantas entradas de áudio existem, para
        // podermos calcular uma porcentagem real na segunda passada (a
        // extração de verdade). ZipInputStream não expõe um total de
        // entradas antecipadamente, então a única forma de saber é lendo os
        // cabeçalhos uma vez sem extrair o conteúdo.
        val totalAudio = runCatching {
            var count = 0
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            val extension = name.substringAfterLast('.', "").lowercase()
                            if (name.isNotBlank() && extension in AUDIO_EXTENSIONS) count++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            count
        }.getOrDefault(0)

        val extracted = mutableListOf<ExtractedAudio>()
        var skipped = 0
        var processedAudio = 0

        try {
            val input = context.contentResolver.openInputStream(zipUri)
                ?: return ZipImportResult.Error("Não foi possível abrir o arquivo .zip selecionado.")

            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        entry = zip.nextEntry
                        continue
                    }

                    val entryFileName = entry.name.substringAfterLast('/')
                    val extension = entryFileName.substringAfterLast('.', "").lowercase()

                    if (entryFileName.isBlank() || extension !in AUDIO_EXTENSIONS) {
                        skipped++
                        entry = zip.nextEntry
                        continue
                    }

                    val outFile = File(stagingDir, "${UUID.randomUUID()}_${sanitizeEntryName(entryFileName)}")
                    outFile.outputStream().use { output -> zip.copyTo(output) }

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

                    entry = zip.nextEntry
                }
            }
        } catch (error: Exception) {
            cleanup(extracted)
            stagingDir.deleteRecursively()
            return ZipImportResult.Error(error.message ?: "Falha ao ler o arquivo .zip selecionado.")
        }

        if (extracted.isEmpty()) {
            stagingDir.deleteRecursively()
            return ZipImportResult.Error("Nenhum arquivo de áudio reconhecido dentro do .zip.")
        }

        onProgress(100)
        return ZipImportResult.Success(extracted, skipped)
    }

    /**
     * Remove os arquivos temporários extraídos por [extract]. Chame depois
     * que cada [ExtractedAudio] já tiver sido copiado para dentro do
     * projeto via [MultitrackAudioStorage.copyFromUri] — o arquivo dentro
     * do projeto é uma cópia independente, então apagar o temporário aqui
     * não afeta a pista já importada.
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
