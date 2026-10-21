package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Sunrise and sunset in minutes since local midnight. */
data class SunDay(val sunriseMin: Int, val sunsetMin: Int)

/**
 * Sunrise equation (NOAA-style, about ±2 minutes), so the clock knows a city's
 * dawn and dusk without a network call.
 */
object SunTimes {
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /** Null during polar day or night. */
    fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): SunDay? {
        val jdNoon = date.toEpochDay() + UNIX_EPOCH_JD + 0.5
        val n = ceil(jdNoon - J2000 + 0.0008)
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val c = 1.9148 * sin(rad(m)) + 0.02 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
        val lambda = (m + c + 180 + 102.9372).mod(360.0)
        val jTransit = J2000 + jStar + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
        val decl = asin(sin(rad(lambda)) * sin(rad(23.4397)))
        val cosW = (sin(rad(-0.833)) - sin(rad(lat)) * sin(decl)) / (cos(rad(lat)) * cos(decl))
        if (abs(cosW) > 1) return null
        val w = deg(acos(cosW))
        fun minutes(jd: Double): Int {
            val t = Instant.ofEpochMilli(((jd - UNIX_EPOCH_JD) * 86_400_000).toLong()).atZone(zone)
            return t.hour * 60 + t.minute
        }
        return SunDay(minutes(jTransit - w / 360), minutes(jTransit + w / 360))
    }
}
