package com.boxplay.data

data class AudioBoxConfig(
    val id: Int,
    val displayName: String,
    val originalFileName: String?,
    val internalFilePath: String?,
    val volume: Float,
    val isLocked: Boolean,
    val updatedAtEpochMillis: Long?,
) {
    init {
        require(id in FIRST_BOX_ID..LAST_BOX_ID) { "Audio box id must be between 1 and 20." }
        require(volume in 0f..1f) { "Volume must be between 0.0 and 1.0." }
    }

    companion object {
        const val FIRST_BOX_ID = 1
        const val LAST_BOX_ID = 20
        const val BOX_COUNT = 20

        fun emptySlots(): List<AudioBoxConfig> =
            (FIRST_BOX_ID..LAST_BOX_ID).map { id ->
                AudioBoxConfig(
                    id = id,
                    displayName = "Box $id",
                    originalFileName = null,
                    internalFilePath = null,
                    volume = 0.80f,
                    isLocked = false,
                    updatedAtEpochMillis = null,
                )
            }
    }
}
