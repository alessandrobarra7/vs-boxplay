package com.boxplay.multitrack.viewmodel

// NOTA DESTE PACOTE DE HANDOFF: o cache local deste arquivo usado para
// montar este pacote era de uma versão anterior à reformulação final do
// fluxo de exportação (seletor de pasta SAF + progresso em % + diálogo de
// Cena/Box só depois de escolher a pasta + streaming/memória no mixdown).
// As seções afetadas (imports, campos de estado de exportação, e as
// funções openExportDialog/beginExport/copyRenderToTree/
// dismissExportDestination/confirmExportDestination, além das chamadas a
// addTracksFromZipUri/addTracksFromRarUri) foram atualizadas abaixo para
// refletir o comportamento REAL do app tal como testado e entregue no APK
// `vsplay-barra7-editor-multipista-mixagem-otimizada-debug.apk` — com base
// no código efetivamente escrito/commitado nessa sessão. O restante do
// arquivo (gerência de projeto, pistas, pré-escuta, import de zip/rar) foi
// conferido como já correspondente à versão atual.

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boxplay.data.AudioBoxConfig
import com.boxplay.data.AudioBoxRepository
import com.boxplay.data.AudioSceneState
import com.boxplay.data.LocalAudioStorage
import com.boxplay.multitrack.audio.MultitrackMixdownEngine
import com.boxplay.multitrack.audio.MultitrackPreviewEngine
import com.boxplay.multitrack.data.MultitrackAudioStorage
import com.boxplay.multitrack.data.MultitrackProjectRepository
import com.boxplay.multitrack.data.MultitrackProjectSerializer
import com.boxplay.multitrack.data.MultitrackRarImporter
import com.boxplay.multitrack.data.MultitrackRenderStorage
import com.boxplay.multitrack.data.MultitrackZipImporter
import com.boxplay.multitrack.model.MultitrackProject
import com.boxplay.multitrack.model.MultitrackTrack
import com.boxplay.multitrack.model.TrackRouting
import com.boxplay.multitrack.ui.model.MultitrackProjectSummaryUiState
import com.boxplay.multitrack.ui.model.MultitrackProjectUiState
import com.boxplay.multitrack.ui.model.MultitrackTrackUiState
import com.boxplay.multitrack.ui.model.ProjectSaveStatus
import com.boxplay.multitrack.ui.model.TrackFileStatus
import com.boxplay.multitrack.ui.model.ZipImportUiState
import com.boxplay.multitrack.util.MultitrackTimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel do editor multipista. Orquestra estado e persistência, o motor
 * de pré-escuta ([MultitrackPreviewEngine], botão "Tocar") e a exportação
 * para um Box do BoxPlay (botão "Exportar para Box"): mixa as pistas num
 * único áudio via [MultitrackMixdownEngine] e salva o resultado no Box
 * escolhido, para que ele toque como um áudio avulso normal.
 */
class MultitrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MultitrackProjectRepository(application)
    private val audioStorage = MultitrackAudioStorage(application)
    private val boxRepository = AudioBoxRepository(application)

    // FIX (investigação "botão Salvar desacoplado do multipista"): usa o
    // MESMO armazenamento interno que o botão "Salvar" de um Box normal usa
    // (com.boxplay.viewmodel.BoxPlayViewModel.onSaveClicked), em vez de
    // apontar o Box direto para o arquivo dentro de multitrack-renders/,
    // que pertence ao módulo multipista e é apagado a cada nova exportação
    // do mesmo projeto. Ver confirmExportDestination() abaixo.
    private val boxAudioStorage = LocalAudioStorage(application)
    private val previewEngine = MultitrackPreviewEngine(application)
    private val renderStorage = MultitrackRenderStorage(application)

    private data class EditorState(
        val project: MultitrackProject,
        val expandedTrackId: String? = null,
        val saveStatus: ProjectSaveStatus = ProjectSaveStatus.SALVO,
    )

    private val _projectSummaries = MutableStateFlow<List<MultitrackProjectSummaryUiState>>(emptyList())
    val projectSummaries: StateFlow<List<MultitrackProjectSummaryUiState>> = _projectSummaries

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editorUiState: StateFlow<MultitrackProjectUiState?> = _editor
        .map { it?.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var autosaveJob: Job? = null

    private val _zipImportState = MutableStateFlow<ZipImportUiState>(ZipImportUiState.Idle)
    val zipImportState: StateFlow<ZipImportUiState> = _zipImportState

    private val _isPreviewing = MutableStateFlow(false)
    val isPreviewing: StateFlow<Boolean> = _isPreviewing

    /** Cenas/Boxes existentes no BoxPlay, para o seletor de "Exportar para Box". */
    val sceneState: StateFlow<AudioSceneState> = boxRepository.sceneState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioSceneState.default())

    private val _exportDialogVisible = MutableStateFlow(false)
    val exportDialogVisible: StateFlow<Boolean> = _exportDialogVisible

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    /** Progresso (0-100) da exportação em andamento, para a barra de carregamento. */
    private val _exportProgressPercent = MutableStateFlow<Int?>(null)
    val exportProgressPercent: StateFlow<Int?> = _exportProgressPercent

    /** Arquivo já mixado, aguardando o usuário escolher a Cena/Box de destino. */
    private var pendingRenderedFile: File? = null

    init {
        refreshProjectList()
    }

    override fun onCleared() {
        super.onCleared()
        previewEngine.release()
    }

    fun refreshProjectList() {
        viewModelScope.launch {
            val projects = withContext(Dispatchers.IO) { repository.loadAllValidProjects() }
            _projectSummaries.value = projects.map { it.toSummaryUiState() }
        }
    }

    fun createProject(name: String): String {
        val now = System.currentTimeMillis()
        val project = MultitrackProject(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Novo projeto" },
            createdAt = now,
            updatedAt = now,
        )
        _editor.value = EditorState(project = project)
        persistNow(project)
        return project.id
    }

    fun openProject(projectId: String) {
        if (_editor.value?.project?.id == projectId) return
        previewEngine.stop()
        _isPreviewing.value = false
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.loadProject(projectId) }) {
                is MultitrackProjectSerializer.ParseResult.Success -> {
                    _editor.value = EditorState(project = result.project)
                }
                is MultitrackProjectSerializer.ParseResult.Corrupted,
                is MultitrackProjectSerializer.ParseResult.UnsupportedSchema,
                -> {
                    // V1: um projeto corrompido ou de um schema futuro
                    // simplesmente não abre (seção 19) — fases seguintes
                    // podem expor um erro dedicado na tela de lista.
                }
            }
        }
    }

    fun closeCurrentProject() {
        autosaveJob?.cancel()
        previewEngine.stop()
        _isPreviewing.value = false
        _editor.value = null
        refreshProjectList()
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteProject(projectId)
                audioStorage.deleteProjectAudioDir(projectId)
            }
            if (_editor.value?.project?.id == projectId) {
                autosaveJob?.cancel()
                _editor.value = null
            }
            refreshProjectList()
        }
    }

    fun renameProject(name: String) {
        if (name.isBlank()) return
        updateProject { project -> project.withName(name) }
    }

    fun renameTrack(trackId: String, name: String) {
        if (name.isBlank()) return
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.withName(name))
        }
    }

    fun toggleTrackExpanded(trackId: String) {
        _editor.update { state ->
            state?.copy(expandedTrackId = if (state.expandedTrackId == trackId) null else trackId)
        }
    }

    fun setTrackVolume(trackId: String, volume: Float) {
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.withVolume(volume))
        }
        syncPreviewVolume(trackId)
    }

    fun setTrackMuted(trackId: String, muted: Boolean) {
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.withMuted(muted))
        }
        syncPreviewVolume(trackId)
    }

    /**
     * Aplica o volume/mudo atual da pista no player de pré-escuta em tempo
     * real, se ele estiver tocando — é o que deixa o usuário "regular o
     * volume de cada item" enquanto o "Tocar" está em andamento, em vez de
     * precisar parar e tocar de novo para ouvir o ajuste.
     */
    private fun syncPreviewVolume(trackId: String) {
        if (!_isPreviewing.value) return
        val track = _editor.value?.project?.tracks?.find { it.id == trackId } ?: return
        previewEngine.setVolume(trackId, track.volume, track.muted)
    }

    /**
     * Alterna a pré-escuta (botão "Tocar"/"Pausar"): toca todas as pistas
     * do projeto ao mesmo tempo via [MultitrackPreviewEngine], respeitando
     * volume/mudo/offset de cada uma.
     */
    fun togglePreview() {
        if (_isPreviewing.value) {
            previewEngine.stop()
            _isPreviewing.value = false
            return
        }

        val project = _editor.value?.project ?: return
        val tracks = project.tracks.mapNotNull { track ->
            val path = track.internalFilePath
            if (path.isNullOrBlank() || !audioStorage.fileExists(path)) return@mapNotNull null
            MultitrackPreviewEngine.PreviewTrack(
                trackId = track.id,
                filePath = path,
                volume = track.volume,
                muted = track.muted,
                offsetUs = track.offsetUs,
            )
        }
        if (tracks.isEmpty()) return

        previewEngine.play(tracks)
        _isPreviewing.value = true
    }

    /**
     * Ponto de entrada do botão "Exportar": NÃO abre mais direto o seletor
     * de Cena/Box. O fluxo pedido pelo usuário é: (1) escolher/criar uma
     * pasta no aparelho ou no Drive via o seletor nativo do Android (SAF,
     * `ActivityResultContracts.OpenDocumentTree` — disparado pela UI, ver
     * [com.boxplay.multitrack.ui.screens.MultitrackEditorScreen]); (2) só
     * depois da pasta escolhida, mixar e perguntar em qual Box sincronizar.
     * Esta função é chamada pela tela quando o usuário já escolheu a pasta
     * (`destinationTreeUri`), e é ela quem dispara a mixagem.
     */
    fun beginExport(destinationTreeUri: Uri) {
        val project = _editor.value?.project ?: return
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                destinationTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _isExporting.value = true
            _exportProgressPercent.value = 0
            _exportMessage.value = "Mixando as pistas... 0%"
            try {
                val mixdownTracks = project.tracks.mapNotNull { track ->
                    val path = track.internalFilePath
                    if (path.isNullOrBlank() || !audioStorage.fileExists(path)) return@mapNotNull null
                    MultitrackMixdownEngine.MixdownTrack(
                        filePath = path,
                        volume = track.volume,
                        muted = track.muted,
                        offsetUs = track.offsetUs,
                        panLeft = track.routing == TrackRouting.LEFT,
                        panRight = track.routing == TrackRouting.RIGHT,
                    )
                }
                val outputFile = renderStorage.prepareRenderFile(project.id, project.name)

                // Mixagem feita "em streaming", uma pista de cada vez (decodifica,
                // soma no buffer mestre e descarta) para não manter todas as
                // pistas decodificadas em memória ao mesmo tempo — correção de um
                // bug real de desempenho/crash relatado pelo usuário (mixagem
                // demorando >10min e o app fechando sozinho por OutOfMemoryError
                // em projetos com várias pistas). Ver MultitrackMixdownEngine.kt.
                val result = withContext(Dispatchers.IO) {
                    MultitrackMixdownEngine.render(mixdownTracks, outputFile) { percent ->
                        _exportProgressPercent.value = percent
                        _exportMessage.value = "Mixando as pistas... $percent%"
                    }
                }

                when (result) {
                    is MultitrackMixdownEngine.MixdownResult.Success -> {
                        _exportMessage.value = "Salvando na pasta escolhida..."
                        val savedToFolder = withContext(Dispatchers.IO) {
                            copyRenderToTree(destinationTreeUri, result.file)
                        }
                        pendingRenderedFile = result.file
                        _exportMessage.value = if (savedToFolder) {
                            "Áudio salvo na pasta escolhida. Selecione o Box de destino."
                        } else {
                            "O áudio foi mixado, mas não foi possível salvar na pasta escolhida. " +
                                "Selecione o Box de destino."
                        }
                        // Só agora (depois de mixado e salvo na pasta) é que o
                        // diálogo de escolha de Cena/Box aparece.
                        _exportDialogVisible.value = true
                    }
                    is MultitrackMixdownEngine.MixdownResult.Error -> {
                        _exportMessage.value = "Não foi possível mixar as pistas: ${result.message}"
                    }
                }
            } finally {
                _isExporting.value = false
                _exportProgressPercent.value = null
            }
        }
    }

    /** Copia o arquivo já mixado para a pasta (SAF) escolhida pelo usuário. */
    private fun copyRenderToTree(treeUri: Uri, sourceFile: File): Boolean = runCatching {
        val context = getApplication<Application>()
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        treeDoc.findFile(sourceFile.name)?.delete()
        val target = treeDoc.createFile("audio/mpeg", sourceFile.name) ?: return false
        context.contentResolver.openOutputStream(target.uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        true
    }.getOrDefault(false)

    fun dismissExportDialog() {
        _exportDialogVisible.value = false
        pendingRenderedFile = null
    }

    /**
     * Vincula o projeto à Cena/Box escolhida e aponta o Box para o áudio já
     * mixado e já salvo na pasta do usuário (etapa final do fluxo — a
     * mixagem em si já aconteceu em [beginExport]). O Box passa a tocar
     * este único arquivo como um áudio avulso "normal", sem precisar
     * sincronizar pistas.
     *
     * FIX (investigação "botão Salvar desacoplado do multipista"): antes,
     * esta função gravava `internalFilePath` apontando DIRETO para o
     * arquivo em `multitrack-renders/<projectId>/`, um arquivo que pertence
     * ao módulo multipista e é apagado por [MultitrackRenderStorage] a cada
     * nova exportação do mesmo projeto — na próxima vez que o usuário
     * exportasse, o Box perdia o áudio silenciosamente. Agora o arquivo
     * mixado é copiado para o armazenamento interno do PRÓPRIO Box (o mesmo
     * usado por `BoxPlayViewModel.onSaveClicked`), com o mesmo ciclo de
     * vida de um áudio salvo manualmente: o Box passa a ser dono do seu
     * arquivo, e o anterior (se houver) é apagado.
     */
    fun confirmExportDestination(sceneId: Int, boxId: Int, sceneName: String) {
        val renderedFile = pendingRenderedFile ?: return
        _exportDialogVisible.value = false
        updateProject { it.copy(linkedSceneId = sceneId, linkedBoxId = boxId) }

        viewModelScope.launch {
            val existingBox = sceneState.value.scenes.find { it.id == sceneId }
                ?.boxes?.find { it.id == boxId }
                ?: AudioBoxConfig.emptySlot(boxId)
            val previousInternalPath = existingBox.internalFilePath

            val storedAudio = withContext(Dispatchers.IO) {
                boxAudioStorage.copyFromFile(boxId, renderedFile, renderedFile.name)
            }

            val updatedBox = existingBox.copy(
                displayName = storedAudio.originalFileName,
                originalFileName = storedAudio.originalFileName,
                internalFilePath = storedAudio.internalFilePath,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) {
                boxRepository.saveConfig(sceneId, updatedBox)
                boxAudioStorage.deleteIfInternal(previousInternalPath)
            }
            _exportMessage.value = "Enviado para \"$sceneName\" > Box $boxId — " +
                "toca como um áudio normal, sem precisar sincronizar pistas."
            pendingRenderedFile = null
        }
    }

    fun dismissExportMessage() {
        _exportMessage.value = null
    }

    fun setTrackRouting(trackId: String, routing: TrackRouting) {
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.withRouting(routing))
        }
    }

    fun shiftTrackOffset(trackId: String, deltaUs: Long) {
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.shiftOffsetUs(deltaUs))
        }
    }

    fun zeroTrackOffset(trackId: String) {
        updateProject { project ->
            val track = project.tracks.find { it.id == trackId } ?: return@updateProject project
            project.replacingTrack(track.withOffsetUs(0L))
        }
    }

    fun removeTrack(trackId: String) {
        val track = _editor.value?.project?.tracks?.find { it.id == trackId } ?: return
        updateProject { project -> project.withoutTrack(trackId) }
        _editor.update { state ->
            if (state?.expandedTrackId == trackId) state.copy(expandedTrackId = null) else state
        }
        audioStorage.deleteTrackFile(track.internalFilePath)
    }

    /**
     * Importa uma pista a partir de uma Uri escolhida via SAF.
     *
     * A extração real de duração/metadados é trabalho da Fase 3 da
     * especificação (seção 21/22) e ainda não existe — por isso a pista
     * entra com durationUs = 0L (a UI mostra "--:--") em vez de inferir a
     * duração a partir do tamanho do arquivo, o que a seção 5 proíbe
     * explicitamente.
     */
    fun addTrackFromUri(uri: Uri, displayNameHint: String? = null) {
        viewModelScope.launch { importSingleTrack(uri, displayNameHint) }
    }

    /**
     * Ponto de entrada único do botão "Adicionar pista": o usuário escolhe
     * qualquer arquivo (áudio avulso, um pacote .zip ou um pacote .rar de
     * stems) num só seletor, sem precisar dizer antes qual dos três é — o
     * app decide sozinho a partir do próprio arquivo escolhido (seção do
     * pedido do usuário: nada de passo extra, tudo automático a partir da
     * seleção).
     *
     * A extensão/tipo MIME reportado por provedores de nuvem (Drive etc.)
     * para .zip/.rar é inconsistente (às vezes vem como
     * application/octet-stream, ou até vazio) — por isso checamos tanto o
     * tipo MIME quanto o nome de exibição antes de decidir.
     */
    fun addTrackOrZipFromUri(uri: Uri) {
        if (_editor.value == null) return
        when (detectArchiveKind(uri)) {
            ArchiveKind.ZIP -> addTracksFromZipUri(uri)
            ArchiveKind.RAR -> addTracksFromRarUri(uri)
            ArchiveKind.NONE -> addTrackFromUri(uri)
        }
    }

    private enum class ArchiveKind { ZIP, RAR, NONE }

    private fun detectArchiveKind(uri: Uri): ArchiveKind {
        val context = getApplication<Application>()
        val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val displayName = queryDisplayName(uri)?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "rar" in mimeType || displayName.endsWith(".rar") -> ArchiveKind.RAR
            "zip" in mimeType || displayName.endsWith(".zip") -> ArchiveKind.ZIP
            else -> ArchiveKind.NONE
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    } catch (error: Exception) {
        null
    }

    /**
     * Importa um pacote .zip de pistas (pacotes de stems normalmente vêm
     * como um único .zip com um áudio por pista — igual ao que programas
     * de música já fazem). Extrai cada arquivo de áudio reconhecido dentro
     * do .zip via [MultitrackZipImporter] e importa cada um como uma pista
     * separada, reaproveitando o mesmo caminho de importação de arquivo
     * único ([importSingleTrack]).
     */
    fun addTracksFromZipUri(zipUri: Uri) {
        if (_editor.value == null) return
        viewModelScope.launch {
            _zipImportState.value = ZipImportUiState.Importing()
            val result = withContext(Dispatchers.IO) {
                MultitrackZipImporter.extract(getApplication(), zipUri) { percent ->
                    _zipImportState.value = ZipImportUiState.Importing(percent)
                }
            }
            when (result) {
                is MultitrackZipImporter.ZipImportResult.Success -> {
                    result.entries.forEach { entry -> importSingleTrack(entry.uri, entry.trackNameHint()) }
                    withContext(Dispatchers.IO) { MultitrackZipImporter.cleanup(result.entries) }
                    _zipImportState.value = ZipImportUiState.Done(
                        imported = result.entries.size,
                        skipped = result.skippedNonAudioCount,
                    )
                }
                is MultitrackZipImporter.ZipImportResult.Error -> {
                    _zipImportState.value = ZipImportUiState.Failed(result.message)
                }
            }
        }
    }

    /**
     * Importa um pacote .rar de pistas — mesmo fluxo de [addTracksFromZipUri],
     * usando [MultitrackRarImporter] em vez do extrator de .zip.
     */
    fun addTracksFromRarUri(rarUri: Uri) {
        if (_editor.value == null) return
        viewModelScope.launch {
            _zipImportState.value = ZipImportUiState.Importing()
            val result = withContext(Dispatchers.IO) {
                MultitrackRarImporter.extract(getApplication(), rarUri) { percent ->
                    _zipImportState.value = ZipImportUiState.Importing(percent)
                }
            }
            when (result) {
                is MultitrackRarImporter.RarImportResult.Success -> {
                    result.entries.forEach { entry -> importSingleTrack(entry.uri, entry.trackNameHint()) }
                    withContext(Dispatchers.IO) { MultitrackRarImporter.cleanup(result.entries) }
                    _zipImportState.value = ZipImportUiState.Done(
                        imported = result.entries.size,
                        skipped = result.skippedNonAudioCount,
                    )
                }
                is MultitrackRarImporter.RarImportResult.Error -> {
                    _zipImportState.value = ZipImportUiState.Failed(result.message)
                }
            }
        }
    }

    /**
     * Nome de exibição da pista a partir do nome original do arquivo dentro
     * do pacote (sem extensão). Importante: o arquivo temporário extraído
     * (ver [MultitrackZipImporter.extract]/[MultitrackRarImporter.extract])
     * tem um prefixo UUID no nome para evitar colisão em disco — sem essa
     * dica explícita, a pista herdaria esse nome de arquivo temporário (com
     * o UUID no meio) em vez do nome original do áudio dentro do pacote.
     */
    private fun MultitrackZipImporter.ExtractedAudio.trackNameHint(): String =
        displayName.substringBeforeLast('.').ifBlank { "Pista" }

    private fun MultitrackRarImporter.ExtractedAudio.trackNameHint(): String =
        displayName.substringBeforeLast('.').ifBlank { "Pista" }

    /** Dispensa o aviso de resultado da última importação de pacote. */
    fun dismissZipImportStatus() {
        _zipImportState.value = ZipImportUiState.Idle
    }

    private suspend fun importSingleTrack(uri: Uri, displayNameHint: String?) {
        val projectId = _editor.value?.project?.id ?: return
        val trackId = UUID.randomUUID().toString()
        when (val result = audioStorage.copyFromUri(projectId, trackId, uri)) {
            is MultitrackAudioStorage.ImportResult.Success -> {
                val now = System.currentTimeMillis()
                val fallbackName = result.originalFileName.substringBeforeLast('.').ifBlank { "Pista" }
                val track = MultitrackTrack.create(
                    id = trackId,
                    name = displayNameHint?.takeIf { it.isNotBlank() } ?: fallbackName,
                    internalFilePath = result.internalFilePath,
                    originalFileName = result.originalFileName,
                    durationUs = 0L,
                    createdAt = now,
                )
                updateProject { project -> project.withTrack(track) }
                _editor.update { it?.copy(expandedTrackId = trackId) }
            }
            is MultitrackAudioStorage.ImportResult.Error -> {
                // V1: falha silenciosa por arquivo. Ao importar um .zip com
                // vários áudios, um arquivo com problema não interrompe a
                // importação dos demais. A Fase 3 trata estados de erro de
                // importação explicitamente (seção 22/70).
            }
        }
    }

    private fun updateProject(transform: (MultitrackProject) -> MultitrackProject) {
        val state = _editor.value ?: return
        val updated = transform(state.project)
        if (updated === state.project) return
        val stamped = updated.copy(updatedAt = System.currentTimeMillis())
        _editor.value = state.copy(project = stamped, saveStatus = ProjectSaveStatus.SALVANDO)
        scheduleAutosave(stamped)
    }

    private fun scheduleAutosave(project: MultitrackProject) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            persistNow(project)
        }
    }

    private fun persistNow(project: MultitrackProject) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { repository.saveProject(project) }
            _editor.update { current ->
                if (current == null || current.project.id != project.id) return@update current
                current.copy(saveStatus = if (saved) ProjectSaveStatus.SALVO else ProjectSaveStatus.ERRO_AO_SALVAR)
            }
        }
    }

    private fun EditorState.toUiState(): MultitrackProjectUiState = MultitrackProjectUiState(
        projectId = project.id,
        name = project.name,
        tracks = project.tracks.map { it.toUiState(expandedTrackId == it.id) },
        durationLabel = MultitrackTimeFormat.format(project.durationUs),
        saveStatus = saveStatus,
        linkedSceneId = project.linkedSceneId,
        linkedBoxId = project.linkedBoxId,
    )

    private fun MultitrackTrack.toUiState(expanded: Boolean): MultitrackTrackUiState {
        val status = if (audioStorage.fileExists(internalFilePath)) TrackFileStatus.READY else TrackFileStatus.MISSING
        return MultitrackTrackUiState(
            id = id,
            name = name,
            routing = routing,
            volume = volume,
            muted = muted,
            offsetLabel = MultitrackTimeFormat.format(offsetUs),
            durationLabel = if (durationUs > 0L) MultitrackTimeFormat.format(durationUs) else "--:--",
            offsetUs = offsetUs,
            fileStatus = status,
            expanded = expanded,
        )
    }

    private fun MultitrackProject.toSummaryUiState(): MultitrackProjectSummaryUiState = MultitrackProjectSummaryUiState(
        projectId = id,
        name = name,
        trackCount = tracks.size,
        updatedAtLabel = formatUpdatedAt(updatedAt),
        lastDestinationLabel = if (linkedSceneId != null && linkedBoxId != null) {
            "Cena $linkedSceneId > Box $linkedBoxId"
        } else {
            null
        },
    )

    private fun formatUpdatedAt(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return formatter.format(Date(epochMillis))
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 600L
    }
}
