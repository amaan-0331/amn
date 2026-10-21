package dev.amaan.amn.widgets.core

/** The one-line summary. [rule] is the rule number from the spec (1-9). */
sealed interface SummaryLine {
    val rule: Int

    data class StormsUntil(val hour: Int) : SummaryLine { override val rule = 1 }
    data class StormsFrom(val hour: Int) : SummaryLine { override val rule = 1 }
    data object StormsTonight : SummaryLine { override val rule = 1 }
    data class DryBy(val hour: Int) : SummaryLine { override val rule = 2 }
    data class AllDay(val snow: Boolean) : SummaryLine { override val rule = 3 }
    data class IntoTonight(val snow: Boolean) : SummaryLine { override val rule = 3 }
    data class WetFrom(val snow: Boolean, val hour: Int) : SummaryLine { override val rule = 4 }
    data class FogClearsBy(val hour: Int) : SummaryLine { override val rule = 5 }
    data class WetTomorrow(val snow: Boolean) : SummaryLine { override val rule = 6 }
    data class TodayVsYesterday(val hotter: Boolean) : SummaryLine { override val rule = 7 }
    data class TomorrowVsToday(val hotter: Boolean) : SummaryLine { override val rule = 8 }
    data object LikeYesterday : SummaryLine { override val rule = 9 }
}

object Summary {
    const val TEMP_DELTA_C = 3.0
    const val WET_POP = 50

    fun of(f: Forecast): SummaryLine {
        val h = f.hourly
        val now = h.first().hour
        val left = 24 - now
        fun today(i: Int) = i < left
        fun wet(i: Int) = h[i].pop >= WET_POP && h[i].condition.isWet
        fun firstFrom(start: Int, test: (Int) -> Boolean) = (start until h.size).firstOrNull(test) ?: -1

        val r1 = {
            val i = firstFrom(0) { today(it) && h[it].condition == Condition.STORM }
            when {
                i < 0 -> null
                i == 0 -> {
                    val e = firstFrom(1) { h[it].condition != Condition.STORM }
                    if (e > 0 && today(e)) SummaryLine.StormsUntil(h[e].hour) else SummaryLine.StormsTonight
                }
                else -> SummaryLine.StormsFrom(h[i].hour)
            }
        }
        val r2 = {
            if (!wet(0)) null else {
                val e = firstFrom(1) { !wet(it) }
                if (e > 0 && today(e)) SummaryLine.DryBy(h[e].hour) else null
            }
        }
        val r3 = {
            if (!wet(0)) null else {
                val snow = h[0].condition == Condition.SNOW
                if (now < 15) SummaryLine.AllDay(snow) else SummaryLine.IntoTonight(snow)
            }
        }
        val r4 = {
            val i = firstFrom(1) { today(it) && wet(it) }
            if (i > 0) SummaryLine.WetFrom(h[i].condition == Condition.SNOW, h[i].hour) else null
        }
        val r5 = {
            if (h[0].condition != Condition.FOG) null else {
                val e = firstFrom(0) { h[it].condition != Condition.FOG }
                if (e > 0 && today(e)) SummaryLine.FogClearsBy(h[e].hour) else null
            }
        }
        val tomorrow = f.days.first()
        val r6 = {
            if (tomorrow.pop >= WET_POP && tomorrow.condition.isWet)
                SummaryLine.WetTomorrow(tomorrow.condition == Condition.SNOW) else null
        }
        val r7 = {
            val d = f.today.highC - f.yesterdayHighC
            when {
                d >= TEMP_DELTA_C -> SummaryLine.TodayVsYesterday(hotter = true)
                d <= -TEMP_DELTA_C -> SummaryLine.TodayVsYesterday(hotter = false)
                else -> null
            }
        }
        val r8 = {
            val d = tomorrow.highC - f.today.highC
            when {
                d >= TEMP_DELTA_C -> SummaryLine.TomorrowVsToday(hotter = true)
                d <= -TEMP_DELTA_C -> SummaryLine.TomorrowVsToday(hotter = false)
                else -> null
            }
        }
        // From 18:00, tomorrow matters more than today.
        val order = if (now >= 18) listOf(r1, r2, r3, r4, r5, r6, r8, r7) else listOf(r1, r2, r3, r4, r5, r6, r7, r8)
        return order.firstNotNullOfOrNull { it() } ?: SummaryLine.LikeYesterday
    }
}

/** English text. Move to string resources when adding translations. */
fun SummaryLine.english(h24: Boolean): String {
    fun at(hour: Int) = if (h24) "%02d:00".format(hour) else "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"
    fun kind(snow: Boolean) = if (snow) "Snow" else "Rain"
    return when (this) {
        is SummaryLine.StormsUntil -> "Storms until ${at(hour)}"
        is SummaryLine.StormsFrom -> "Storms from ${at(hour)}"
        SummaryLine.StormsTonight -> "Storms tonight"
        is SummaryLine.DryBy -> "Dry by ${at(hour)}"
        is SummaryLine.AllDay -> "${kind(snow)} all day"
        is SummaryLine.IntoTonight -> "${kind(snow)} into tonight"
        is SummaryLine.WetFrom -> "${kind(snow)} from ${at(hour)}"
        is SummaryLine.FogClearsBy -> "Fog clears by ${at(hour)}"
        is SummaryLine.WetTomorrow -> "${kind(snow)} tomorrow"
        is SummaryLine.TodayVsYesterday -> if (hotter) "Hotter today" else "Cooler today"
        is SummaryLine.TomorrowVsToday -> if (hotter) "Hotter tomorrow" else "Cooler tomorrow"
        SummaryLine.LikeYesterday -> "Like yesterday"
    }
}
