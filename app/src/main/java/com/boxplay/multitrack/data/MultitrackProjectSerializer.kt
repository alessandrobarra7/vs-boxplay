package com.boxplay.multitrack.data

import com.boxplay.multitrack.data.json.JsonParseException
import com.boxplay.multitrack.data.json.JsonValue
import com.boxplay.multitrack.data.json.parseJson
import com.boxplay.multitrack.data.json.writeJson
import com.boxplay.multitrack.model.MultitrackProject
import com.boxplay.multitrack.model.MultitrackTrack
import com.boxplay.multitrack.model.TrackRouting

/**
 * Serialização JSON de [MultitrackProject] (ver especificação, seções 17-19).
 *
 * O schema é versionado via [MultitrackProject.schemaVersion]. Um arquivo com
 * uma versão futura/desconhecida nunca é sobrescrito automaticamente — ele é
 * reportado como [ParseResult.UnsupportedSchema] e o chamador decide o que
 * fazer (a V1 não sabe migrar de nenhuma versão anterior porque é a
 * primeira).
 */
object MultitrackProjectSerializer {

    fun toJson(project: MultitrackProject): String = writeJson(projectToJson(project))

    private fun projectToJson(project: MultitrackProject): JsonValue.Obj {
        val tracksArray = JsonValue.Arr.of(project.tracks.map { trackToJson(it) })
        return JsonValue.Obj()
            .put("schemaVersion", project.schemaVersion)
            .put("id", project.id)
            .put("name", project.name)
            .put("linkedSceneId", project.linkedSceneId)
            .put("linkedBoxId", project.linkedBoxId)
            .put("lastRenderedFilePath", project.lastRenderedFilePath)
            .put("createdAt", project.createdAt)
            .put("updatedAt", project.updatedAt)
            .put("tracks", tracksArray)
    }

    private fun trackToJson(track: MultitrackTrack): JsonValue.Obj =
        JsonValue.Obj()
            .put("id", track.id)
            .put("name", track.name)
            .put("internalFilePath", track.internalFilePath)
            .put("originalFileName", track.originalFileName)
            .put("durationUs", track.durationUs)
            .put("offsetUs", track.offsetUs)
            .put("volume", track.volume)
            .put("muted", track.muted)
            .put("routing", track.routing.name)
            .put("sampleRate", track.sampleRate)
            .put("channelCount", track.channelCount)
            .put("createdAt", track.createdAt)
            .put("updatedAt", track.updatedAt)

    sealed class ParseResult {
        data class Success(val project: MultitrackProject) : ParseResult()
        data class UnsupportedSchema(val foundVersion: Int) : ParseResult()
        data class Corrupted(val reason: String) : ParseResult()
    }

    fun fromJson(json: String): ParseResult {
        val root = try {
            parseJson(json) as? JsonValue.Obj
                ?: return ParseResult.Corrupted("Raiz do JSON não é um objeto.")
        } catch (error: JsonParseException) {
            return ParseResult.Corrupted(error.message ?: "JSON malformado.")
        }

        val schemaVersion = root.int("schemaVersion")
            ?: return ParseResult.Corrupted("Campo obrigatório 'schemaVersion' ausente.")

        if (schemaVersion > MultitrackProject.CURRENT_SCHEMA_VERSION) {
            return ParseResult.UnsupportedSchema(schemaVersion)
        }
        // schemaVersion < CURRENT_SCHEMA_VERSION seria migrado aqui em versões futuras.
        // A V1 não tem nada para migrar porque é o próprio schema inicial.

        val id = root.str("id") ?: return ParseResult.Corrupted("Campo obrigatório 'id' ausente.")
        val name = root.str("name") ?: return ParseResult.Corrupted("Campo obrigatório 'name' ausente.")
        val createdAt = root.long("createdAt")
            ?: return ParseResult.Corrupted("Campo obrigatório 'createdAt' ausente.")
        val updatedAt = root.long("updatedAt")
            ?: return ParseResult.Corrupted("Campo obrigatório 'updatedAt' ausente.")

        val tracksArray = root.arr("tracks") ?: JsonValue.Arr()
        val tracks = mutableListOf<MultitrackTrack>()
        for (item in tracksArray.items) {
            val trackObj = item as? JsonValue.Obj
                ?: return ParseResult.Corrupted("Uma das pistas do projeto não é um objeto JSON válido.")
            val track = trackFromJson(trackObj)
                ?: return ParseResult.Corrupted("Uma pista do projeto está com campos obrigatórios ausentes.")
            tracks.add(track)
        }

        val project = MultitrackProject(
            id = id,
            name = name,
            tracks = tracks,
            linkedSceneId = root.int("linkedSceneId"),
            linkedBoxId = root.int("linkedBoxId"),
            lastRenderedFilePath = root.str("lastRenderedFilePath"),
            createdAt = createdAt,
            updatedAt = updatedAt,
            schemaVersion = schemaVersion,
        )
        return ParseResult.Success(project)
    }

    private fun trackFromJson(obj: JsonValue.Obj): MultitrackTrack? {
        val id = obj.str("id") ?: return null
        val name = obj.str("name") ?: return null
        val internalFilePath = obj.str("internalFilePath") ?: return null
        val durationUs = obj.long("durationUs") ?: return null
        val createdAt = obj.long("createdAt") ?: return null
        val offsetUs = obj.long("offsetUs") ?: 0L
        val volume = obj.float("volume") ?: MultitrackTrack.MAX_VOLUME
        val muted = obj.bool("muted") ?: false
        val routing = TrackRouting.fromNameOrNull(obj.str("routing")) ?: TrackRouting.CENTER
        val updatedAt = obj.long("updatedAt") ?: createdAt

        return MultitrackTrack.create(
            id = id,
            name = name,
            internalFilePath = internalFilePath,
            originalFileName = obj.str("originalFileName"),
            durationUs = durationUs,
            offsetUs = offsetUs,
            volume = volume,
            muted = muted,
            routing = routing,
            sampleRate = obj.int("sampleRate"),
            channelCount = obj.int("channelCount"),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
