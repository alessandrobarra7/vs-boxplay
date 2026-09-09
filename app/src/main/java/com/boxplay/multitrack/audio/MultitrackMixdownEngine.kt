package com.boxplay.multitrack.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.naman14.androidlame.LameBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Motor de mixagem (bounce) do editor multipista: decodifica cada pista,
 * aplica volume/mudo/roteamento L-C-R e o offset de cada uma, soma tudo
 * num único áudio estéreo e codifica o resultado em MP3.
 *
 * É isso que faz o botão "Exportar para Box" mandar um arquivo leve e
 * "normal" — o Box toca um único áudio, igual a qualquer outro, em vez de
 * ter que sincronizar várias pistas ao mesmo tempo no celular (o que
 * geraria atraso/travamento, exatamente o que o usuário pediu para
 * evitar).
 *
 * A decodificação de cada pista de entrada usa só APIs nativas do Android
 * (MediaExtractor/MediaCodec aceitam qualquer formato que o aparelho já
 * sabe tocar). Só a CODIFICAÇÃO final precisa de uma biblioteca externa
 * (TAndroidLame, wrapper Android da libmp3lame): o Android tem decoder de
 * MP3 embutido, mas não tem encoder — MediaCodec/MediaMuxer não geram
 * arquivos .mp3 de jeito nenhum, só AAC/OPUS/FLAC. Pedido explícito do
 * usuário foi que o áudio exportado fosse mesmo um .mp3, então essa
 * dependência é necessária (ver app/build.gradle.kts).
 */
object MultitrackMixdownEngine {

    private const val TARGET_SAMPLE_RATE = 44_100
    private const val TARGET_CHANNELS = 2
    private const val TIMEOUT_US = 10_000L
    private const val DECODE_PROGRESS_SHARE = 60
    private const val MIX_PROGRESS_MARK = 75
    private const val MP3_BITRATE_KBPS = 128
    private const val MP3_ENCODE_CHUNK_FRAMES = 8_192

    sealed class MixdownResult {
        data class Success(val file: File, val durationUs: Long) : MixdownResult()
        data class Error(val message: String) : MixdownResult()
    }

    data class MixdownTrack(
        val filePath: String,
        val volume: Float,
        val muted: Boolean,
        val offsetUs: Long,
        val panLeft: Boolean,
        val panRight: Boolean,
    )

    /**
     * Mixa [tracks] e grava o resultado em [outputFile] (.mp3). Bloqueante —
     * chame de dentro de [kotlinx.coroutines.Dispatchers.IO].
     *
     * [onProgress] recebe a porcentagem (0-100) da mixagem inteira: 0-60%
     * decodificando cada pista, 60-75% somando/normalizando, 75-100%
     * codificando o áudio final — pedido do usuário para a barra de
     * carregamento mostrar o quanto falta em vez de só girar sem fim.
     *
     * IMPORTANTE sobre memória: cada pista é somada ao acumulador da mixagem
     * (`mixL`/`mixR`) assim que é decodificada, e o áudio bruto dessa pista é
     * descartado antes de decodificar a próxima. A versão anterior mantinha
     * o PCM decodificado de TODAS as pistas na memória ao mesmo tempo antes
     * de somar — com muitas pistas de alguns minutos cada, isso passava
     * facilmente de centenas de MB simultâneos, o que deixava a mixagem
     * extremamente lenta (o coletor de lixo trabalhando o tempo todo) e
     * podia derrubar o app com falta de memória. Processando uma pista de
     * cada vez, o pico de memória não cresce mais com o número de pistas.
     */
    fun render(tracks: List<MixdownTrack>, outputFile: File, onProgress: (Int) -> Unit = {}): MixdownResult {
        val audible = tracks.filter { !it.muted && File(it.filePath).isFile }
        if (audible.isEmpty()) {
            return MixdownResult.Error("Nenhuma pista com áudio válido para mixar.")
        }
        onProgress(0)

        return try {
            var mixL = FloatArray(0)
            var mixR = FloatArray(0)
            var masterLength = 0
            var decodedAny = false

            audible.forEachIndexed { index, track ->
                val pcm = runCatching { decodeToStereoPcm(track.filePath) }.getOrNull()
                if (pcm != null && pcm.isNotEmpty()) {
                    decodedAny = true
                    val startFrame = samplesFromUs(max(0L, track.offsetUs))
                    val frameCount = pcm.size / TARGET_CHANNELS
                    val neededLength = startFrame + frameCount
                    if (neededLength > mixL.size) {
                        mixL = mixL.copyOf(neededLength)
                        mixR = mixR.copyOf(neededLength)
                    }
                    masterLength = max(masterLength, neededLength)

                    val volume = track.volume.coerceIn(0f, 1f)
                    val leftGain = if (track.panLeft) volume else if (track.panRight) 0f else volume
                    val rightGain = if (track.panRight) volume else if (track.panLeft) 0f else volume
                    for (frame in 0 until frameCount) {
                        val destFrame = startFrame + frame
                        mixL[destFrame] += pcm[frame * 2] * leftGain
                        mixR[destFrame] += pcm[frame * 2 + 1] * rightGain
                    }
                }
                // `pcm` sai de escopo aqui — fica livre para o coletor de
                // lixo liberar antes de decodificar a próxima pista, em vez
                // de ficar retido junto com todas as outras até o final.
                onProgress((((index + 1).toFloat() / audible.size) * DECODE_PROGRESS_SHARE).toInt())
            }

            if (!decodedAny || masterLength == 0) {
                return MixdownResult.Error("Não foi possível decodificar nenhuma das pistas.")
            }

            var peak = 0f
            for (i in 0 until masterLength) {
                peak = max(peak, max(abs(mixL[i]), abs(mixR[i])))
            }
            // Só reduz o ganho quando realmente ultrapassa o limite — evita
            // "estourar" (clipping) sem abaixar o volume de mixagens que já
            // cabiam dentro da faixa dinâmica.
            val scale = if (peak > Short.MAX_VALUE) Short.MAX_VALUE / peak else 1f

            val leftOut = ShortArray(masterLength)
            val rightOut = ShortArray(masterLength)
            for (i in 0 until masterLength) {
                leftOut[i] = clampToShort(mixL[i] * scale)
                rightOut[i] = clampToShort(mixR[i] * scale)
            }
            // Libera o acumulador de mixagem antes de codificar — a
            // codificação já precisa de outros dois buffers grandes
            // (`leftOut`/`rightOut`), sem motivo para manter os quatro vivos
            // ao mesmo tempo.
            mixL = FloatArray(0)
            mixR = FloatArray(0)
            onProgress(MIX_PROGRESS_MARK)

            encodeToMp3(leftOut, rightOut, outputFile) { encodePercent ->
                val mapped = MIX_PROGRESS_MARK +
                    (encodePercent * (100 - MIX_PROGRESS_MARK) / 100)
                onProgress(mapped.coerceIn(MIX_PROGRESS_MARK, 100))
            }
            onProgress(100)
            MixdownResult.Success(outputFile, usFromSamples(masterLength))
        } catch (error: Throwable) {
            outputFile.delete()
            // OutOfMemoryError é um Error, não uma Exception — sem capturar
            // Throwable aqui, ele derrubava o app inteiro em vez de aparecer
            // como uma mensagem de erro normal na tela.
            val message = if (error is OutOfMemoryError) {
                "Memória insuficiente para mixar todas as pistas de uma vez. " +
                    "Tente reduzir o número de pistas do projeto."
            } else {
                error.message ?: "Falha ao gerar o áudio final."
            }
            MixdownResult.Error(message)
        }
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private fun samplesFromUs(us: Long): Int = ((us * TARGET_SAMPLE_RATE) / 1_000_000L).toInt()
    private fun usFromSamples(samples: Int): Long = (samples.toLong() * 1_000_000L) / TARGET_SAMPLE_RATE

    /** Decodifica qualquer formato de áudio suportado pelo Android para PCM 16-bit estéreo em [TARGET_SAMPLE_RATE]. */
    private fun decodeToStereoPcm(path: String): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                throw IllegalStateException("Nenhuma trilha de áudio em $path")
            }
            extractor.selectTrack(trackIndex)

            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val rawShorts = if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                readRawPcm(extractor)
            } else {
                decodeCompressed(extractor, format, mime)
            }

            return resampleToTarget(rawShorts, sourceSampleRate, sourceChannels)
        } finally {
            extractor.release()
        }
    }

    private fun readRawPcm(extractor: MediaExtractor): ShortArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(1 shl 20)
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val chunk = ByteArray(sampleSize)
            buffer.get(chunk)
            out.write(chunk)
            extractor.advance()
        }
        return bytesToShorts(out.toByteArray())
    }

    private fun decodeCompressed(extractor: MediaExtractor, format: MediaFormat, mime: String): ShortArray {
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    if (bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmOut.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        return bytesToShorts(pcmOut.toByteArray())
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    /** Converte para estéreo e reamostra (aproximação linear) para [TARGET_SAMPLE_RATE]. */
    private fun resampleToTarget(input: ShortArray, sourceSampleRate: Int, sourceChannels: Int): ShortArray {
        if (sourceChannels <= 0 || input.isEmpty()) return ShortArray(0)

        val frameCount = input.size / sourceChannels
        val stereo = ShortArray(frameCount * TARGET_CHANNELS)
        for (frame in 0 until frameCount) {
            if (sourceChannels == 1) {
                val sample = input[frame]
                stereo[frame * 2] = sample
                stereo[frame * 2 + 1] = sample
            } else {
                stereo[frame * 2] = input[frame * sourceChannels]
                stereo[frame * 2 + 1] = input[frame * sourceChannels + 1]
            }
        }

        if (sourceSampleRate == TARGET_SAMPLE_RATE || frameCount == 0) return stereo

        val targetFrames = ((frameCount.toLong() * TARGET_SAMPLE_RATE) / sourceSampleRate).toInt()
        val resampled = ShortArray(targetFrames * TARGET_CHANNELS)
        for (i in 0 until targetFrames) {
            val sourceIndex = ((i.toLong() * sourceSampleRate) / TARGET_SAMPLE_RATE).toInt()
                .coerceIn(0, frameCount - 1)
            resampled[i * 2] = stereo[sourceIndex * 2]
            resampled[i * 2 + 1] = stereo[sourceIndex * 2 + 1]
        }
        return resampled
    }

    /**
     * Codifica PCM 16-bit estéreo (já separado em canal esquerdo/direito,
     * sem interleaving) em MP3 de verdade via LAME (TAndroidLame). Processa
     * em blocos de [MP3_ENCODE_CHUNK_FRAMES] amostras por canal — o mesmo
     * padrão usado pela própria lib para gravação em tempo real — em vez de
     * mandar o áudio inteiro de uma vez, para não precisar de um buffer de
     * saída do tamanho do arquivo inteiro.
     *
     * O tamanho do buffer de saída (`7200 + amostras * 2 * 1.25`) é a
     * fórmula recomendada pelo próprio LAME para o pior caso de expansão.
     */
    private fun encodeToMp3(bufferLeft: ShortArray, bufferRight: ShortArray, outputFile: File, onProgress: (Int) -> Unit = {}) {
        val totalSamples = bufferLeft.size
        val lame = LameBuilder()
            .setInSampleRate(TARGET_SAMPLE_RATE)
            .setOutChannels(TARGET_CHANNELS)
            .setOutBitrate(MP3_BITRATE_KBPS)
            .setOutSampleRate(TARGET_SAMPLE_RATE)
            .build()

        val chunkLeft = ShortArray(MP3_ENCODE_CHUNK_FRAMES)
        val chunkRight = ShortArray(MP3_ENCODE_CHUNK_FRAMES)
        val mp3buf = ByteArray((7_200 + MP3_ENCODE_CHUNK_FRAMES * 2 * 1.25).toInt())

        FileOutputStream(outputFile).use { output ->
            var offset = 0
            while (offset < totalSamples) {
                val count = min(MP3_ENCODE_CHUNK_FRAMES, totalSamples - offset)
                System.arraycopy(bufferLeft, offset, chunkLeft, 0, count)
                System.arraycopy(bufferRight, offset, chunkRight, 0, count)

                val bytesEncoded = lame.encode(chunkLeft, chunkRight, count, mp3buf)
                if (bytesEncoded > 0) output.write(mp3buf, 0, bytesEncoded)

                offset += count
                if (totalSamples > 0) {
                    onProgress(((offset.toFloat() / totalSamples) * 100).toInt().coerceIn(0, 100))
                }
            }

            val flushedBytes = lame.flush(mp3buf)
            if (flushedBytes > 0) output.write(mp3buf, 0, flushedBytes)
        }
    }
}
