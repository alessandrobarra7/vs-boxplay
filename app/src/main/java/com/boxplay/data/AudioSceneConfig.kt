package com.boxplay.data

data class AudioSceneConfig(
    val id: Int,
    val name: String,
    val isLocked: Boolean,
    val boxes: List<AudioBoxConfig>,
    val updatedAtEpochMillis: Long? = null,
) {
    val boxCount: Int
        get() = boxes.size

    init {
        require(id > 0) { "Scene id must be positive." }
        require(name.isNotBlank()) { "Scene name must not be blank." }
        require(boxes.size <= AudioBoxConfig.MAX_BOX_COUNT) { "Scene can have at most 40 boxes." }
        require(boxes.map { it.id }.distinct().size == boxes.size) { "Scene boxes must have unique ids." }
    }

    companion object {
        const val DEFAULT_SCENE_ID = 1
        const val DEFAULT_SCENE_NAME = "bloco"
        const val MAX_SCENE_COUNT = 15

        fun default(): AudioSceneConfig = AudioSceneConfig(
            id = DEFAULT_SCENE_ID,
            name = DEFAULT_SCENE_NAME,
            isLocked = false,
            boxes = emptyList(),
            updatedAtEpochMillis = null,
        )
    }
}

data class AudioSceneState(
    val scenes: List<AudioSceneConfig>,
    val selectedSceneId: Int,
) {
    val selectedScene: AudioSceneConfig
        get() = scenes.firstOrNull { it.id == selectedSceneId } ?: scenes.first()

    val canCreateScene: Boolean
        get() = scenes.size < AudioSceneConfig.MAX_SCENE_COUNT

    companion object {
        fun default(): AudioSceneState = AudioSceneState(
            scenes = listOf(AudioSceneConfig.default()),
            selectedSceneId = AudioSceneConfig.DEFAULT_SCENE_ID,
        )
    }
}