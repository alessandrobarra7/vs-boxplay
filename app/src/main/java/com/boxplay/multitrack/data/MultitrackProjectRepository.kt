package com.boxplay.multitrack.data

import android.content.Context
import com.boxplay.multitrack.model.MultitrackProject
import java.io.File

/**
 * Persistência dos projetos do editor multipista.
 *
 * Cada projeto vira um arquivo `<projectId>.json` dentro de
 * `context.filesDir/multitrack-projects/` (ver especificação, seção 17) —
 * NÃO estende o DataStore Preferences do BoxPlay com chaves de tracks.
 *
 * O construtor que recebe um [File] diretamente (em vez de [Context]) existe
 * para permitir testes unitários JVM puros com um diretório temporário, sem
 * precisar de Robolectric.
 */
class MultitrackProjectRepository(private val projectsDir: File) {

    constructor(context: Context) : this(File(context.filesDir, PROJECTS_DIR_NAME))

    init {
        projectsDir.mkdirs()
    }

    fun listProjectIds(): List<String> =
        projectsDir.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            ?.map { it.name.removeSuffix(FILE_SUFFIX) }
            ?.sorted()
            ?: emptyList()

    fun loadProject(projectId: String): MultitrackProjectSerializer.ParseResult {
        val file = projectFile(projectId)
        if (!file.isFile) {
            return MultitrackProjectSerializer.ParseResult.Corrupted("Projeto '$projectId' não encontrado.")
        }
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return MultitrackProjectSerializer.ParseResult.Corrupted(
                "Falha ao ler o arquivo do projeto: ${error.message}",
            )
        }
        return MultitrackProjectSerializer.fromJson(text)
    }

    /**
     * Grava o projeto de forma atômica: escreve em um arquivo temporário e só
     * substitui o arquivo final depois que a escrita foi concluída com
     * sucesso. Nunca deixa um projeto parcialmente sobrescrito em caso de
     * falha (seção 18 da especificação).
     */
    fun saveProject(project: MultitrackProject): Boolean {
        val target = projectFile(project.id)
        val temp = File(projectsDir, "${project.id}$FILE_SUFFIX.tmp")
        return try {
            val json = MultitrackProjectSerializer.toJson(project)
            temp.writeText(json, Charsets.UTF_8)

            val replaced = temp.renameTo(target)
            if (!replaced) {
                // Alguns sistemas de arquivo recusam renomear por cima de um
                // arquivo já existente; nesse caso copiamos e só então
                // removemos o temporário, sem nunca deixar o alvo apagado
                // sem um substituto pronto.
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.isFile
        } catch (error: Exception) {
            temp.delete()
            false
        }
    }

    fun deleteProject(projectId: String): Boolean {
        val file = projectFile(projectId)
        return !file.exists() || file.delete()
    }

    /** Carrega todos os projetos válidos, ignorando arquivos corrompidos, ordenados por atualização mais recente. */
    fun loadAllValidProjects(): List<MultitrackProject> =
        listProjectIds()
            .mapNotNull { id -> (loadProject(id) as? MultitrackProjectSerializer.ParseResult.Success)?.project }
            .sortedByDescending { it.updatedAt }

    private fun projectFile(projectId: String): File = File(projectsDir, "$projectId$FILE_SUFFIX")

    private companion object {
        const val PROJECTS_DIR_NAME = "multitrack-projects"
        const val FILE_SUFFIX = ".json"
    }
}
