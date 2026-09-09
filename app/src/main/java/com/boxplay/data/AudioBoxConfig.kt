package com.boxplay.data

data class AudioBoxConfig(
    val id: Int,
    val displayName: String,
    val customLabel: String? = null,
    val originalFileName: String?,
    val internalFilePath: String?,
    val volume: Float,
    val isLocked: Boolean,
    val updatedAtEpochMillis: Long?,
) {
    init {
        require(id in FIRST_BOX_ID..MAX_BOX_COUNT) { "Audio box id must be between 1 and 40." }
        require(volume in 0f..1f) { "Volume must be between 0.0 and 1.0." }
    }

    companion object {
        const val FIRST_BOX_ID = 1
        const val MAX_BOX_COUNT = 40
        const val DEFAULT_BOX_COUNT = 0

        fun emptySlot(id: Int): AudioBoxConfig {
            require(id in FIRST_BOX_ID..MAX_BOX_COUNT) { "Audio box id must be between 1 and 40." }
            return AudioBoxConfig(
                id = id,
                displayName = "Box $id",
                customLabel = null,
                originalFileName = null,
                internalFilePath = null,
                volume = 0.80f,
                isLocked = false,
                updatedAtEpochMillis = null,
            )
        }

        fun emptySlots(count: Int = DEFAULT_BOX_COUNT): List<AudioBoxConfig> {
            require(count in DEFAULT_BOX_COUNT..MAX_BOX_COUNT) { "Audio box count must be between 0 and 40." }
            return (FIRST_BOX_ID..count).map(::emptySlot)
        }
    }
}