package com.filmemo.data

data class Film(
    val id: Long = 0,
    val name: String,
    val iso: Int,
    val status: String = STATUS_ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_FINISHED = "finished"
    }

    val isActive: Boolean get() = status == STATUS_ACTIVE
    val isFinished: Boolean get() = status == STATUS_FINISHED
}

data class Exposure(
    val id: Long = 0,
    val filmId: Long,
    val aperture: Double,
    val shutterSpeed: String,
    val iso: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0,
    val hasFlash: Boolean = false,
    val flashGN: Double? = null
)

data class FilmWithExposures(
    val film: Film,
    val exposureCount: Int
)
