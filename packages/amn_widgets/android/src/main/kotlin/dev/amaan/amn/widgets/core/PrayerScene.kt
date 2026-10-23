package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** The prayer widget's sky, one for each stretch between the day's times. */
enum class PrayerSky {
    ISHA, FAJR, MORNING, DHUHR, ASR, MAGHRIB;

    /** Sunrise to Maghrib: the sun is up. */
    val isDay get() = this == MORNING || this == DHUHR || this == ASR

    companion object {
        /** [now] and [day] (the six times) in minutes after midnight. */
        fun at(now: Int, day: IntArray): PrayerSky = when {
            now < day[0] || now >= day[5] -> ISHA
            now < day[1] -> FAJR
            now < day[2] -> MORNING
            now < day[3] -> DHUHR
            now < day[4] -> ASR
            else -> MAGHRIB
        }
    }
}

/** Where the prayer widget is at one instant: today's times, the next prayer, the sky and the sun or moon. */
class PrayerMoment(
    /** Today's six times (UAE date). */
    val day: List<PrayerTime>,
    /** Minutes after midnight, UAE time. */
    val now: Int,
    val next: PrayerTime,
) {
    val minutes = IntArray(6) { day[it].hhmm.let { s -> s.substring(0, 2).toInt() * 60 + s.substring(3).toInt() } }
    val sky = PrayerSky.at(now, minutes)

    /** How far along its arc the sun (sunrise to Maghrib) or the moon (Maghrib to sunrise) is, 0..1. */
    val orb: Float = run {
        val rise = minutes[1]
        val set = minutes[4]
        if (sky.isDay) (now - rise).toFloat() / (set - rise)
        else ((now - set + DAY) % DAY).toFloat() / (rise + DAY - set)
    }.coerceIn(0f, 1f)

    companion object {
        const val DAY = 24 * 60

        fun of(table: PrayerTimetable, zone: Int, now: Instant): PrayerMoment {
            val t = LocalDateTime.ofInstant(now, PrayerTimetable.UAE_OFFSET)
            return PrayerMoment(table.day(zone, t.toLocalDate()), t.hour * 60 + t.minute, table.next(zone, now))
        }

        /**
         * When the widget should next be drawn: at the next of the six times (the next prayer, the
         * sky and the arc change there), or on the next quarter hour so the sun or moon moves on.
         */
        fun nextRedraw(table: PrayerTimetable, zone: Int, now: Instant): Instant {
            val today = PrayerTimetable.uaeDate(now)
            val time = listOf(today, today.plusDays(1)).flatMap { table.day(zone, it) }.map { it.at }.first { it.isAfter(now) }
            val quarter = Instant.ofEpochSecond((now.epochSecond / QUARTER + 1) * QUARTER)
            return minOf(time, quarter)
        }

        private const val QUARTER = 15 * 60L
    }
}

/**
 * The prayer widget's geometry, in the units of its design (a 4×1 drawn at 501×141, 1.5× of 334×94 dp).
 * The renderer scales these to the widget; the light check below uses them as they are. Wider than
 * [WIDE_W] (a 5×1), the next prayer's name and time share one line and the grid keeps to the right.
 */
object PrayerDesign {
    const val W = 501f
    const val H = 141f
    const val FLOOR = 32f
    const val ARC_INSET = 22f
    const val PAD = 24f
    const val TOP = 8f
    const val GRID_X = 184f
    const val CELL_W = 90f
    const val CELL_GAP = 6f
    const val ROW_H = 47f
    const val ROW_GAP = 2f
    const val TRACK_H = 12f
    const val TRACK_BOTTOM = 10f
    const val LIGHT_R = 230f
    const val VIGNETTE_R = 520f

    /** 4:1, between One UI's 4×1 (3.55:1) and 5×1 (4.43:1). */
    const val WIDE_W = 4 * H

    /** Space between the one-line next prayer and the grid. */
    const val WIDE_GAP = 14f

    fun wide(w: Float) = w >= WIDE_W

    /** The grid's left edge on a widget [w] units wide: as on the 4×1, measured from the right on a wide one. */
    fun gridX(w: Float) = if (wide(w)) w - (W - GRID_X) else GRID_X

    /** Top of the countdown. On a wide widget it and the one-line next prayer (about 52 tall) are centred above the floor. */
    fun countdownTop(w: Float, nextSize: Float = 52f) =
        if (wide(w)) TOP + (H - FLOOR - 5 - TOP - (19 + nextSize)) / 2 else TOP

    /** The sun or moon's centre on a widget [w] units wide. */
    fun orb(f: Float, w: Float): Pair<Float, Float> {
        val ground = H - FLOOR
        return (ARC_INSET + f * (w - 2 * ARC_INSET)) to (ground - 6 - sin(f * PI).toFloat() * (ground - 24))
    }

    /** Middles of the text on a widget [w] units wide: the countdown, the next prayer, and the five grid cells. */
    fun textPoints(w: Float = W): List<Pair<Float, Float>> {
        val gx = gridX(w)
        val next = if (wide(w)) {
            val top = countdownTop(w)
            val mid = (PAD + gx - WIDE_GAP) / 2
            listOf(PAD + 50 to top + 9, mid to top + 45, PAD + 30 to top + 45, gx - WIDE_GAP - 30 to top + 45)
        } else {
            listOf(PAD + 50 to TOP + 9, PAD + 55 to TOP + 45, PAD + 30 to TOP + 84)
        }
        return next + (0 until 5).map { i -> gx + (i % 3) * (CELL_W + CELL_GAP) + CELL_W / 2 to TOP + (i / 3) * (ROW_H + ROW_GAP) + ROW_H / 2 }
    }
}

/**
 * The light that follows the sun or moon: a pool of [color] at [alpha] round the orb, fading out
 * over [PrayerDesign.LIGHT_R], and a vignette darkening the far side by [vignette].
 */
data class SkyLight(val color: Int, val alpha: Float, val vignette: Float) {
    /** The light's opacity [d] units from the orb. */
    fun pool(d: Float): Float {
        val t = d / PrayerDesign.LIGHT_R
        return when {
            t < .45f -> alpha * (1 - .7f * t / .45f)
            t < 1f -> alpha * .3f * (1 - (t - .45f) / .55f)
            else -> 0f
        }
    }

    fun shade(d: Float): Float = vignette * ((d / PrayerDesign.VIGNETTE_R - .35f) / .65f).coerceIn(0f, 1f)

    /** The sky's color at ([x], [y]) with this light over it. */
    fun skyAt(p: WidgetPalette, x: Float, y: Float, orb: Pair<Float, Float>): Int {
        val d = hypot(x - orb.first, y - orb.second)
        val sky = Palettes.mix(p.top, p.bottom, (y / PrayerDesign.H).toDouble().coerceIn(0.0, 1.0))
        return Palettes.mix(Palettes.mix(sky, 0xFF000000.toInt(), shade(d).toDouble()), color, pool(d).toDouble())
    }

    /**
     * This light, dimmed until every line of text keeps [Palettes.MIN_CONTRAST] against the sky
     * behind it. The light passes behind the text for part of the day; at dusk and night it
     * brightens the sky behind light text, so it gives way there.
     */
    fun readable(p: WidgetPalette, orb: Pair<Float, Float>, w: Float = PrayerDesign.W): SkyLight {
        var light = this
        val points = PrayerDesign.textPoints(w)
        fun worst() = points.minOf { (x, y) -> Palettes.contrast(p.ink, light.skyAt(p, x, y, orb)) }
        while (worst() < Palettes.MIN_CONTRAST && (light.alpha > 0f || light.vignette > 0f)) {
            light = SkyLight(color, (light.alpha - .05f).coerceAtLeast(0f), (light.vignette - .03f).coerceAtLeast(0f))
        }
        return light
    }

    companion object {
        fun of(sky: PrayerSky) =
            if (sky.isDay) SkyLight(0xFFFFF4C2.toInt(), .62f, .12f) else SkyLight(0xFFD6DEFF.toInt(), .32f, .28f)
    }
}
