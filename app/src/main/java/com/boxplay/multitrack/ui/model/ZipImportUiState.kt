package com.boxplay.multitrack.ui.model

/**
 * Estado da importação de um pacote .zip/.rar de pistas (ver
 * [com.boxplay.multitrack.data.MultitrackZipImporter] e
 * [com.boxplay.multitrack.data.MultitrackRarImporter]), para a tela do
 * editor mostrar progresso/resultado em vez de importar tudo em silêncio.
 *
 * [Importing.percent] é a porcentagem concluída (0-100) quando o
 * importador consegue calcular (número de arquivos de áudio já
 * extraídos / total) — pedido do usuário para deixar claro o quanto falta,
 * não só que "está rodando". Fica `null` só nos poucos casos em que a
 * contagem total não pôde ser feita, e a UI cai de volta pra uma barra
 * indeterminada nesse caso.
 */
sealed class ZipImportUiState {
    object Idle : ZipImportUiState()
    data class Importing(val percent: Int? = null) : ZipImportUiState()
    data class Done(val imported: Int, val skipped: Int) : ZipImportUiState()
    data class Failed(val message: String) : ZipImportUiState()
}
