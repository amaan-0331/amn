package dev.amaan.amn.widgets.core

enum class Phase { DAWN, DAY, DUSK, NIGHT }

object TimeOfDay {
    /**
     * All arguments are minutes since local midnight.
     * Dawn: sunrise -30 to +60 min. Dusk: sunset -60 to +30 min.
     */
    fun phase(nowMin: Int, sunriseMin: Int, sunsetMin: Int): Phase = when (nowMin) {
        in (sunriseMin - 30) until (sunriseMin + 60) -> Phase.DAWN
        in (sunsetMin - 60) until (sunsetMin + 30) -> Phase.DUSK
        in (sunriseMin + 60) until (sunsetMin - 60) -> Phase.DAY
        else -> Phase.NIGHT
    }

    /** Fallback when sunrise/sunset are unknown: sunrise 06:30, sunset 18:00. */
    fun phaseForHour(hour: Int, minute: Int = 0): Phase = phase(hour * 60 + minute, 6 * 60 + 30, 18 * 60)
}
