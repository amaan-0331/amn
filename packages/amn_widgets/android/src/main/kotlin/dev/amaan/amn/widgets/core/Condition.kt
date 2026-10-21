package dev.amaan.amn.widgets.core

/** The seven conditions the widgets draw. */
enum class Condition {
    CLEAR, PARTLY, CLOUDY, RAIN, STORM, SNOW, FOG;

    /** Rain, snow or storm: counts as "wet" for the summary rules. */
    val isWet: Boolean get() = this == RAIN || this == SNOW || this == STORM

    companion object {
        /** Maps a WMO weather code (as returned by Open-Meteo). */
        fun fromWmo(code: Int): Condition = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57, in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUDY
        }
    }
}
