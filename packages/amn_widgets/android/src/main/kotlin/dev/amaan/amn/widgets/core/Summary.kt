package dev.amaan.amn.widgets.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** The one-line summary: which rule produced it, and the text. */
data class SummaryLine(val rule: String, val text: String)

/**
 * One rule's verdict. [variants] is null when the rule doesn't apply; [fitting] are the
 * phrasings short enough for the widget size.
 */
data class RuleTrace(val id: String, val urgent: Boolean, val description: String, val variants: List<String>?, val fitting: List<String>) {
    val applies get() = variants != null
}

/**
 * How a line was picked: [winner] is the rule id; for a rotated (flavor) pick, [candidateIndex] is
 * seed mod [candidateCount], and [variantIndex] is (seed / candidates) mod [variantCount].
 */
data class Explanation(
    val seed: Long,
    val maxLen: Int,
    val rules: List<RuleTrace>,
    val winner: String,
    val line: SummaryLine,
    val candidateIndex: Int,
    val candidateCount: Int,
    val variantIndex: Int,
    val variantCount: Int,
)

/**
 * The summary line, in two tiers.
 *
 * Urgent rules (storms, rain or snow now or soon, fog, extreme heat, frost) are checked in order and
 * the first match wins, because they change what you do. Otherwise every "flavor" rule that applies
 * is a candidate, and [seed] rotates between candidates and their phrasings. The widget changes the
 * seed every two hours, so the line varies through the day without flickering between redraws.
 *
 * Only phrasings up to [maxLen] characters are used, so each widget size gets a line that fits.
 * Every urgent rule has a phrasing of 16 characters or less (the 2×2 limit), so a small widget
 * never skips an urgent rule just because its phrasings are too long.
 * Temperatures in the text go through [deg], so they follow the unit setting.
 */
object Summary {
    const val TEMP_DELTA_C = 3.0
    const val WET_POP = 50

    fun of(
        f: Forecast,
        h24: Boolean,
        deg: (Double) -> String = { "${it.roundToInt()}°" },
        maxLen: Int = 24,
        seed: Long = 0,
    ): SummaryLine = explain(f, h24, deg, maxLen, seed).line

    /** Every rule's verdict and how the line was picked, for the widget lab. */
    fun explain(
        f: Forecast,
        h24: Boolean,
        deg: (Double) -> String = { "${it.roundToInt()}°" },
        maxLen: Int = 24,
        seed: Long = 0,
    ): Explanation {
        val c = Ctx(f, h24, deg)
        val traces = mutableListOf<RuleTrace>()
        var line: SummaryLine? = null
        var winner = ""
        var candidateIndex = -1
        var candidateCount = 0
        var variantIndex = 0
        var variantCount = 0

        for (r in urgent) {
            val cand = r.eval(c)
            val fit = cand?.variants.orEmpty().filter { it.length <= maxLen }
            traces += RuleTrace(r.id, true, r.description, cand?.variants, fit)
            if (line == null && cand != null && fit.isNotEmpty()) {
                variantCount = fit.size
                variantIndex = seed.mod(fit.size)
                line = SummaryLine(cand.rule, fit[variantIndex])
                winner = r.id
            }
        }
        val flavors = flavor.map { r ->
            val cand = r.eval(c)
            RuleTrace(r.id, false, r.description, cand?.variants, cand?.variants.orEmpty().filter { it.length <= maxLen }) to cand
        }
        traces += flavors.map { it.first }
        if (line == null) {
            val candidates = flavors.filter { it.first.fitting.isNotEmpty() }
            if (candidates.isNotEmpty()) {
                candidateCount = candidates.size
                candidateIndex = seed.mod(candidates.size)
                val (trace, cand) = candidates[candidateIndex]
                variantCount = trace.fitting.size
                variantIndex = (seed / candidates.size).mod(variantCount)
                line = SummaryLine(cand!!.rule, trace.fitting[variantIndex])
                winner = trace.id
            }
        }
        val fbVariants = fallback(c)
        val fbFit = fbVariants.filter { it.length <= maxLen }
        traces += RuleTrace("steady", false, "Nothing notable: a calm line that fits the sky", fbVariants, fbFit)
        if (line == null) {
            variantCount = fbFit.size.coerceAtLeast(1)
            variantIndex = seed.mod(variantCount)
            line = SummaryLine("steady", fbFit.getOrNull(variantIndex) ?: fbVariants.minBy { it.length })
            winner = "steady"
        }
        return Explanation(seed, maxLen, traces, winner, line, candidateIndex, candidateCount, variantIndex, variantCount)
    }

    private class Rule(val id: String, val description: String, val eval: (Ctx) -> Cand?)

    private class Cand(val rule: String, val variants: List<String>)

    private class Ctx(val f: Forecast, val h24: Boolean, val deg: (Double) -> String) {
        val h = f.hourly
        val now = h.first().hour
        val nowMin = now * 60
        val left = 24 - now
        val tomorrow = f.days.first()
        fun today(i: Int) = i < left
        fun wet(i: Int) = h[i].pop >= WET_POP && h[i].condition.isWet
        fun firstFrom(start: Int, test: (Int) -> Boolean) = (start until h.size).firstOrNull(test) ?: -1
        fun at(hour: Int) = clock(hour * 60)
        fun clock(min: Int): String {
            val hh = (min / 60) % 24
            val mm = min % 60
            return if (h24) "%02d:%02d".format(hh, mm)
            else "${if (hh % 12 == 0) 12 else hh % 12}${if (mm == 0) "" else ":%02d".format(mm)} ${if (hh < 12) "AM" else "PM"}"
        }
        val isNight get() = nowMin >= f.sunsetMin + 30 || nowMin < f.sunriseMin - 30
    }

    // ---- Urgent: first match wins ----

    private val urgent: List<Rule> = listOf(
        // Thunderstorms in the rest of today.
        Rule("storm", "Thunderstorm in the rest of today") { c ->
            val i = c.firstFrom(0) { c.today(it) && c.h[it].condition == Condition.STORM }
            when {
                i < 0 -> null
                i == 0 -> {
                    val e = c.firstFrom(1) { c.h[it].condition != Condition.STORM }
                    if (e > 0 && c.today(e)) {
                        val t = c.at(c.h[e].hour)
                        Cand("storm", listOf("Storms until $t", "Stormy till $t", "Thunder until $t", "Storm till $t"))
                    } else {
                        Cand("storm", listOf("Storms tonight", "Stormy night ahead", "Thunder tonight"))
                    }
                }
                else -> {
                    val t = c.at(c.h[i].hour)
                    Cand("storm", listOf("Storms from $t", "Thunder by $t", "Storms roll in at $t", "Storms at $t"))
                }
            }
        },
        // Raining or snowing now, and it stops today.
        Rule("wet-now-stops", "Raining or snowing now, stops before midnight") { c ->
            if (!c.wet(0)) null else {
                val e = c.firstFrom(1) { !c.wet(it) }
                if (e > 0 && c.today(e)) {
                    val t = c.at(c.h[e].hour)
                    if (c.h[0].condition == Condition.SNOW) Cand("wet-now-stops", listOf("Dry by $t", "Snow stops by $t", "Snow eases by $t"))
                    else Cand("wet-now-stops", listOf("Dry by $t", "Rain eases by $t", "Clearing up by $t"))
                } else null
            }
        },
        // Raining or snowing now, and it carries on.
        Rule("wet-all-day", "Raining or snowing now, and it carries on") { c ->
            if (!c.wet(0)) null else {
                val snow = c.h[0].condition == Condition.SNOW
                when {
                    c.now < 15 && snow -> Cand("wet-all-day", listOf("Snow all day", "Snow day", "Snow keeps coming"))
                    c.now < 15 -> Cand("wet-all-day", listOf("Rain all day", "Umbrella day", "A wet one today"))
                    snow -> Cand("wet-all-day", listOf("Snow into tonight", "Snowy night ahead", "Snow all night"))
                    else -> Cand("wet-all-day", listOf("Rain into tonight", "Wet evening ahead", "Rain on into the night", "Rain all night"))
                }
            }
        },
        // Dry now, rain or snow later today.
        Rule("wet-later", "Dry now, rain or snow later today") { c ->
            val i = c.firstFrom(1) { c.today(it) && c.wet(it) }
            if (i <= 0) null else {
                val t = c.at(c.h[i].hour)
                if (c.h[i].condition == Condition.SNOW) Cand("wet-later", listOf("Snow from $t", "Snow at $t", "Snow arrives at $t"))
                else Cand("wet-later", listOf("Rain from $t", "Rain at $t", "Umbrella by $t", "Rain moves in at $t"))
            }
        },
        // Foggy now, clears today.
        Rule("fog", "Foggy now, clears today") { c ->
            if (c.h[0].condition != Condition.FOG) null else {
                val e = c.firstFrom(0) { c.h[it].condition != Condition.FOG }
                if (e > 0 && c.today(e)) {
                    val t = c.at(c.h[e].hour)
                    Cand("fog", listOf("Fog clears by $t", "Foggy till $t", "Misty until $t"))
                } else null
            }
        },
        // Extreme heat (40 °C and up) while the day is still ahead.
        Rule("scorcher", "40 °C or hotter, with the day still ahead") { c ->
            if (c.f.today.highC >= 40 && c.now < 17) {
                val d = c.deg(c.f.today.highC)
                Cand("scorcher", listOf("Scorcher: $d today", "Peak heat $d", "$d today. Stay cool"))
            } else null
        },
        // Below zero tonight.
        Rule("frost", "Below zero tonight") { c ->
            val coldest = c.h.filter { it.hour >= 18 || it.hour < 9 }.minOfOrNull { it.tempC }
            if (c.now >= 15 && coldest != null && coldest <= 0) {
                Cand("frost", listOf("Freezing tonight", "Frost tonight", "Below zero tonight"))
            } else null
        },
    )

    // ---- Flavor: every match is a candidate, rotated by seed ----

    private val flavor: List<Rule> = listOf(
        // Rain or snow tomorrow.
        Rule("wet-tomorrow", "Rain or snow tomorrow (50%+)") { c ->
            val d = c.tomorrow
            when {
                d.pop < WET_POP || !d.condition.isWet -> null
                d.condition == Condition.SNOW -> Cand("wet-tomorrow", listOf("Snow tomorrow", "Snow day tomorrow", "White one tomorrow"))
                else -> Cand("wet-tomorrow", listOf("Rain tomorrow", "Umbrella tomorrow", "Wet day tomorrow"))
            }
        },
        // Today against yesterday. Not after 18:00, when today is mostly over.
        Rule("vs-yesterday", "Today's high vs yesterday's (±3°), before 18:00") { c ->
            val d = c.f.today.highC - c.f.yesterdayHighC
            val n = abs(d).roundToInt()
            when {
                c.now >= 18 -> null
                d >= TEMP_DELTA_C -> Cand("vs-yesterday", listOf("Hotter today", "Warmer than yesterday", "Up $n° on yesterday"))
                d <= -TEMP_DELTA_C -> Cand("vs-yesterday", listOf("Cooler today", "Cooler than yesterday", "Down $n° on yesterday"))
                else -> null
            }
        },
        // Tomorrow against today.
        Rule("vs-tomorrow", "Tomorrow's high vs today's (±3°)") { c ->
            val d = c.tomorrow.highC - c.f.today.highC
            when {
                d >= TEMP_DELTA_C -> Cand("vs-tomorrow", listOf("Hotter tomorrow", "Warmer tomorrow", "Heats up tomorrow"))
                d <= -TEMP_DELTA_C -> Cand("vs-tomorrow", listOf("Cooler tomorrow", "Cools off tomorrow", "Fresher tomorrow"))
                else -> null
            }
        },
        // The next days are all dry, or mostly wet.
        Rule("days-ahead", "Next days all dry, or mostly wet") { c ->
            val days = c.f.days.take(3)
            val wetDays = days.count { it.pop >= WET_POP && it.condition.isWet }
            when {
                days.size >= 2 && days.all { it.pop < 20 && !it.condition.isWet } ->
                    Cand("dry-ahead", listOf("Dry days ahead", "No rain in sight", "Dry for days"))
                wetDays >= 2 -> Cand("wet-ahead", listOf("Wet days ahead", "Rainy days ahead", "Keep the umbrella out"))
                else -> null
            }
        },
        // Sunrise or sunset within the hour.
        Rule("sun", "Sunrise or sunset within the hour") { c ->
            val skyVisible = c.f.condition == Condition.CLEAR || c.f.condition == Condition.PARTLY
            when {
                c.nowMin in (c.f.sunsetMin - 60) until c.f.sunsetMin && skyVisible ->
                    Cand("sunset", listOf("Sunset at ${c.clock(c.f.sunsetMin)}", "Golden hour soon", "Sun sets at ${c.clock(c.f.sunsetMin)}"))
                c.nowMin in (c.f.sunriseMin - 60) until c.f.sunriseMin ->
                    Cand("sunrise", listOf("Sunrise at ${c.clock(c.f.sunriseMin)}", "Sun's up at ${c.clock(c.f.sunriseMin)}"))
                else -> null
            }
        },
        // A clear night.
        Rule("clear-night", "A clear night") { c ->
            if (c.isNight && c.f.condition == Condition.CLEAR) Cand("clear-night", listOf("Clear skies tonight", "Starry night", "Clear night out"))
            else null
        },
        // Cooling off this evening.
        Rule("evening-cool", "Drops 5° or more by this evening") { c ->
            if (c.now !in 15..20) null else {
                val later = c.h.getOrNull(5) ?: return@Rule null
                if (c.f.nowTempC - later.tempC >= 5) {
                    val t = c.at(later.hour)
                    val d = c.deg(later.tempC)
                    Cand("evening-cool", listOf("Down to $d by $t", "Cooling to $d by $t", "Cooler by $t"))
                } else null
            }
        },
        // Feels noticeably different from the thermometer.
        Rule("feels", "Feels 3° or more off the thermometer") { c ->
            val gap = c.f.feelsLikeC - c.f.nowTempC
            val d = c.deg(c.f.feelsLikeC)
            when {
                gap >= TEMP_DELTA_C -> Cand("feels", listOf("Feels like $d", "Feels hotter: $d", "Feels more like $d"))
                gap <= -TEMP_DELTA_C -> Cand("feels", listOf("Feels like $d", "Feels colder: $d", "Wind chill: $d"))
                else -> null
            }
        },
        // A cool start that warms up a lot.
        Rule("warms-up", "Cool morning that warms 8° or more") { c ->
            if (c.now < 11 && c.f.today.highC - c.f.nowTempC >= 8) {
                val d = c.deg(c.f.today.highC)
                Cand("warms-up", listOf("Warms up to $d", "Up to $d later", "Heading for $d"))
            } else null
        },
        // Hot, but not extreme.
        Rule("hot", "Hot (35–40 °C), daytime") { c ->
            if (c.f.today.highC in 35.0..39.99 && c.now in 8..15) {
                val d = c.deg(c.f.today.highC)
                Cand("hot", listOf("Hot one: $d", "Peaks at $d", "Heat builds to $d"))
            } else null
        },
        // Pleasant: mild, dry, sunny, daytime.
        Rule("pleasant", "Mild, dry and sunny") { c ->
            val nice = c.f.today.highC in 18.0..27.0 && c.f.today.pop < 20 &&
                (c.f.condition == Condition.CLEAR || c.f.condition == Condition.PARTLY) && !c.isNight
            if (nice) Cand("pleasant", listOf("Lovely day out", "Perfect weather", "A nice one today")) else null
        },
    )

    /** Nothing notable: a calm line that still fits the sky. */
    private fun fallback(c: Ctx): List<String> = when {
        c.isNight && c.f.condition == Condition.CLEAR -> listOf("Like yesterday", "Calm, clear night")
        c.isNight -> listOf("Like yesterday", "Quiet night ahead", "Calm night")
        c.f.condition == Condition.CLEAR -> listOf("Like yesterday", "Clear and steady", "Blue skies")
        c.f.condition == Condition.PARTLY -> listOf("Like yesterday", "Sun and clouds", "Steady day")
        c.f.condition == Condition.CLOUDY -> listOf("Like yesterday", "Grey but dry", "Overcast, calm")
        else -> listOf("Like yesterday", "Steady day", "Same as yesterday")
    }
}
