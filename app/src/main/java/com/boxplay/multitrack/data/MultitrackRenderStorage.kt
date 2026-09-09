package com.boxplay.multitrack.data

import android.content.Context
import java.io.File

/**
 * Onde ficam os áudios finais (mixados) exportados pelo editor multipista —
 * pedido do usuário: "exportar para uma pasta" em vez de espalhar arquivos
 * soltos. Cada projeto tem sua própria subpasta dentro do armazenamento
 * interno do app, e só o render mais recente é mantido — um render novo
 * substitui o anterior, em vez de acumular arquivos velhos.
 */
class MultitrackRenderStorage(private val context: Context) {

    private val rootDir: File
        get() = File(context.filesDir, ROOT_DIR_NAME).apply { mkdirs() }

    private fun projectDir(projectId: String): File =
        File(rootDir, projectId).apply { mkdirs() }

    /** Prepara o arquivo de destino do próximo render, apagando qualquer render anterior desse projeto. */
    fun prepareRenderFile(projectId: String, projectName: String): File {
        val dir = projectDir(projectId)
        dir.listFiles()?.forEach { it.delete() }

        val safeName = projectName
            .ifBlank { "projeto" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .trim()
            .take(60)
            .ifBlank { "projeto" }

        return File(dir, "$safeName.mp3")
    }

    fun deleteProjectRenders(projectId: String): Boolean = projectDir(projectId).deleteRecursively()

    private companion object {
        const val ROOT_DIR_NAME = "multitrack-renders"
    }
}
