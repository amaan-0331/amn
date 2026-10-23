package dev.amaan.amn.widgets.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The five prayers, plus sunrise, which ends Fajr's time. Order matches the timetable. */
enum class Prayer(val label: String) {
    FAJR("Fajr"), SUNRISE("Sunrise"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha");

    val isPrayer get() = this != SUNRISE
}

/** One prayer on one day: the exact instant, and the time as Awqaf prints it ("05:04", UAE time). */
data class PrayerTime(val prayer: Prayer, val at: Instant, val hhmm: String)

/** A neighbourhood from Awqaf's list, and the timetable zone it follows. */
data class PrayerArea(val emirate: Int, val id: Int, val zone: Int, val name: String, val nameAr: String)

/**
 * The official UAE timetable. Awqaf's table is fixed by day of year, so each zone is one
 * leap year of days: 366 x 6 times x HHMM (see tool/prayer/fetch.py). Mirrors
 * lib/prayer/timetable.dart, which the app's prayer page uses.
 */
class PrayerTimetable(
    /** Zone id to its 366-day string. */
    val zones: Map<Int, String>,
) {
    /** The six times for [zone] on [date], a UAE calendar date. */
    fun day(zone: Int, date: LocalDate): List<PrayerTime> {
        val s = zones.getValue(zone)
        val base = dayIndex(date.monthValue, date.dayOfMonth) * 24
        return Prayer.entries.map { p ->
            val i = base + p.ordinal * 4
            val h = s.substring(i, i + 2).toInt()
            val m = s.substring(i + 2, i + 4).toInt()
            PrayerTime(p, date.atTime(h, m).toInstant(UAE_OFFSET), "${s.substring(i, i + 2)}:${s.substring(i + 2, i + 4)}")
        }
    }

    /** The next prayer (not sunrise) after [now], looking into tomorrow after Isha. */
    fun next(zone: Int, now: Instant): PrayerTime {
        val today = uaeDate(now)
        for (d in listOf(today, today.plusDays(1))) {
            day(zone, d).firstOrNull { it.prayer.isPrayer && it.at.isAfter(now) }?.let { return it }
        }
        error("no prayer within two days")
    }

    /** The one before [next]: the prayer whose time it is now. */
    fun current(zone: Int, now: Instant): PrayerTime {
        val today = uaeDate(now)
        for (d in listOf(today, today.minusDays(1))) {
            day(zone, d).lastOrNull { it.prayer.isPrayer && !it.at.isAfter(now) }?.let { return it }
        }
        error("no prayer within two days")
    }

    companion object {
        const val DAYS_LENGTH = 366 * 24

        /** UAE time is UTC+4 all year. */
        val UAE_OFFSET: ZoneOffset = ZoneOffset.ofHours(4)

        /** Whether [times] is a well-formed zone string. */
        fun isValid(times: String) = times.length == DAYS_LENGTH && times.all { it in '0'..'9' }

        /** Day of a leap year (0-365): 29 Feb is its own day; other years keep their leap-year index from March. */
        fun dayIndex(month: Int, day: Int) = LocalDate.of(2028, month, day).dayOfYear - 1

        /** Today's date in the UAE. */
        fun uaeDate(now: Instant): LocalDate = LocalDateTime.ofInstant(now, UAE_OFFSET).toLocalDate()
    }
}
