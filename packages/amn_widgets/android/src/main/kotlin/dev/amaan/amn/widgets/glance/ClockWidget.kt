package dev.amaan.amn.widgets.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.LocalState
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import dev.amaan.amn.widgets.core.WidgetPalette
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.amaan.amn.widgets.core.CityOffset
import dev.amaan.amn.widgets.core.ClockTimeline
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.Palettes
import dev.amaan.amn.widgets.core.Phase
import dev.amaan.amn.widgets.core.SunDay
import dev.amaan.amn.widgets.core.TimeOfDay
import java.time.LocalDate
import dev.amaan.amn.widgets.data.ClockPreview
import dev.amaan.amn.widgets.data.ClockWeather
import dev.amaan.amn.widgets.data.SampleForecast
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.core.Forecast
import dev.amaan.amn.widgets.core.ForecastBuilder
import androidx.glance.layout.Row
import dev.amaan.amn.widgets.data.ClockSchedule
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.WidgetStore
import dev.amaan.amn.widgets.data.sun
import dev.amaan.amn.widgets.data.zoneOrPhone
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockWidget : GlanceAppWidget() {
    // Exact: the layout is picked from the real size and shape (see ClockLayout.of).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val store = WidgetStore(context)
        val initial = store.clock(appWidgetId)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && initial.timeZone != null) {
            ClockSchedule.scheduleNext(context, appWidgetId, initial)
            // Keeps the hourly weather job alive for clocks added before it existed (KEEP: no-op if scheduled).
            if (initial.showWeather) ClockWeather.ensure(context)
        }
        provideContent {
            val glanceState = LocalState.current
            glanceState.renderVersion()
            val preview = glanceState as? ClockPreview
            val settings = preview?.settings ?: store.clock(appWidgetId)
            val debug = preview?.debug
            val weather = when {
                !settings.showWeather -> null
                debug != null -> SampleForecast.of(debug.condition, debug.hour)
                else -> ClockWeather.cache(context).load(preview?.previewWidgetId ?: appWidgetId)
                    ?.let { ForecastBuilder.build(it.raw, Instant.now()) }
                    ?.takeIf { System.currentTimeMillis() - it.fetchedAtEpochMs < ClockWeather.MAX_AGE_MS }
            }
            ClockContent(settings, appWidgetId, debug?.hour, weather, store.units())
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ClockWeather.ensure(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ClockWeather.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        val cache = ClockWeather.cache(context)
        appWidgetIds.forEach {
            store.remove(it)
            cache.remove(it)
            ClockSchedule.cancel(context, it)
        }
    }
}

/** The three clock layouts and their design sizes (dp). */
internal enum class ClockLayout(val w: Float, val h: Float) {
    TINY(70f, 70f), SMALL(140f, 70f), SQUARE(140f, 140f);

    companion object {
        fun of(size: DpSize): ClockLayout {
            val ratio = size.width.value / size.height.value
            return when {
                ratio >= 1.4f -> SMALL
                size.width < 120.dp -> TINY // 1×1 is ~70 dp (design) to ~100 dp (One UI); 2×2 is 140–200
                else -> SQUARE
            }
        }
    }
}

@Composable
private fun ClockContent(s: ClockSettings, appWidgetId: Int, debugHour: Int?, weather: Forecast?, units: Units) {
    val context = LocalContext.current
    val size = LocalSize.current
    val zone = s.zoneOrPhone()
    val now = Instant.now()
    val phase = if (debugHour != null) {
        val sun = s.sun()(LocalDate.now(zone)) ?: SunDay(6 * 60 + 30, 18 * 60)
        TimeOfDay.phase(debugHour * 60, sun.sunriseMin, sun.sunsetMin)
    } else {
        ClockTimeline.phaseAt(now, zone, s.sun())
    }
    // With weather, the clock takes the weather widget's look: condition art and condition colors.
    // Without it (switched off, or not fetched yet) it falls back to the sky for the time of day.
    val sky = weather?.condition ?: Condition.CLEAR
    val pal = Palettes.of(sky, phase, context.isNightMode())
    val ink = ColorProvider(Color(pal.ink))
    val offset = CityOffset.between(zone, ZoneId.systemDefault(), now).label

    val layout = ClockLayout.of(size)
    val fit = fitFor(size, layout.w, layout.h, maxK = 1.8f)
    val k = fit::dp
    val tiny = layout == ClockLayout.TINY
    val square = layout == ClockLayout.SQUARE
    val withSeconds = s.seconds && !tiny
    val pattern = buildString {
        append(if (s.h24) "HH" else "h")
        append(if (tiny) "\n" else ":")
        append("mm")
        if (withSeconds) append(":ss")
        if (!s.h24 && !tiny) append(" a")
    }
    // The design size, shrunk when the format wouldn't fit the width on one line.
    val designTime = when (layout) {
        ClockLayout.TINY -> 23f
        ClockLayout.SQUARE -> 52f
        ClockLayout.SMALL -> 32f
    }
    val available = (layout.w - 24f) * fit.k
    var timeSize = minOf(designTime * fit.k, available / timeWidthEm(pattern.substringBefore('\n')))
    val artSize = fit.k * when (layout) {
        ClockLayout.TINY -> 40f
        ClockLayout.SQUARE -> 88f
        ClockLayout.SMALL -> 56f
    }
    val phaseLabel = when (phase) {
        Phase.DAWN -> "Dawn"; Phase.DAY -> "Day"; Phase.DUSK -> "Dusk"; Phase.NIGHT -> "Night"
    }
    // A clear sky at dawn or dusk keeps the sun half below the bottom edge; everything else sits in the corner.
    val low = (phase == Phase.DAWN || phase == Phase.DUSK) && sky == Condition.CLEAR
    fun style(sp: Float, weight: FontWeight) = TextStyle(color = ink, fontSize = fit.sp(sp).sp, fontWeight = weight)

    // Spare height in a square clock becomes the next hours' weather, not a gap between the labels and the time.
    val fontScale = context.resources.configuration.fontScale
    val textLine = { sp: Float -> sp * fit.k * fontScale * 1.3f }
    val usedHeight = 20 * fit.k + textLine(15f) + textLine(11.5f) + timeSize + textLine(11f)
    val hoursHeight = HOURS_ROW_DP * minOf(fit.k, HOURS_MAX_K)
    val hoursCount = if (size.width.value >= 250) 6 else 4
    // The time may shrink by up to 30% to make room for the row; beyond that, no row.
    val shortBy = hoursHeight + 8 * fit.k - (size.height.value - usedHeight)
    val showHours = square && weather != null && shortBy <= timeSize * .3f
    if (showHours && shortBy > 0) timeSize -= shortBy

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "clock")),
        contentAlignment = if (low) Alignment.BottomEnd else Alignment.TopEnd,
    ) {
        if (low) {
            val sun = Horizon.topHalf(context, Art.drawable(Condition.CLEAR, phase))
            Image(ImageProvider(sun), null, GlanceModifier.width((artSize * .8f).dp).height((artSize * .8f * .55f).dp))
        } else if (weather != null) {
            // Clouds and rain are taller than a plain sun, so weather art is drawn at 80% to stay clear of the time.
            val z = artSize * .8f
            // The art uses the width of the time as shown now (9:41 is a digit shorter than 12:41);
            // the clock redraws when the hour gains or loses a digit.
            val singleDigitHour = !s.h24 && ZonedDateTime.now(zone).hour % 12 in 1..9
            val roomy = roomyArt(layout, size, fit, timeSize, timeSize + if (showHours) hoursHeight + 8 * fit.k else 0f, pattern, singleDigitHour, z)
            if (roomy != null) {
                // Room to spare: a bigger image that runs off the top-right corner, so the space is filled.
                BleedArt(Art.drawable(sky, phase), roomy.size, bleedRight = roomy.size * roomy.bleedRight, bleedTop = roomy.size * roomy.bleedTop)
            } else {
                BleedArt(Art.drawable(sky, phase), z, bleedRight = z * .14f, bleedTop = z * .2f)
            }
        } else {
            // Runs off the top-right corner by a fifth, as in the design.
            BleedArt(Art.drawable(Condition.CLEAR, phase), artSize, bleedRight = artSize * .2f, bleedTop = artSize * .2f)
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = k(12f), vertical = k(if (square) 10f else 7f))) {
            val title = s.cityName ?: "Choose a city"
            Text(if (tiny) cityCode(title) else title, style = style(if (square) 15f else 11.5f, FontWeight.Bold), maxLines = 1)
            if (square) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(phaseLabel, style = style(11.5f, FontWeight.Medium))
                    if (weather != null) WeatherBit(weather, units, ::style, 11.5f)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            // The time is part of the layout, so it's sized in dp and ignores the font-size setting.
            // A fixed height stops AndroidRemoteViews from taking all remaining space.
            Row(verticalAlignment = Alignment.Bottom) {
                ClockText(
                    zone.id, pattern.removeSuffix(" a"), timeSize, pal.ink,
                    GlanceModifier.height((timeSize * if (tiny) 2.05f else 1.0f).dp),
                    unit = android.util.TypedValue.COMPLEX_UNIT_DIP,
                )
                // AM/PM at 42% of the time, as in the design, so the digits dominate.
                if (pattern.endsWith(" a")) {
                    val ap = timeSize * .42f
                    ClockText(
                        zone.id, "a", ap, pal.ink,
                        GlanceModifier.height((ap * 1.35f).dp).padding(start = (ap * .3f).dp, bottom = (timeSize * .08f).dp),
                        unit = android.util.TypedValue.COMPLEX_UNIT_DIP,
                    )
                }
            }
            if (!tiny) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(offset, style = style(if (square) 11f else 10.5f, FontWeight.Medium), maxLines = 1)
                    // 2×1 has no line under the city, so its weather sits next to the offset.
                    if (!square && weather != null) WeatherBit(weather, units, ::style, 10.5f)
                }
            }
            if (showHours && weather != null) {
                Spacer(GlanceModifier.height(k(8f)))
                ClockHours(weather, units, pal, Fit(minOf(fit.k, HOURS_MAX_K)), s.h24, hoursCount)
            }
        }
    }
}

/** How much of a roomy image runs off the top and right edges. */
private const val ROOMY_BLEED = .12f

/**
 * The size for weather art when the clock has room to spare, or null when it's tight and the art
 * should run off the corner as usual. Wide layouts use the width beside the time; 2×2-style ones
 * the height above it. The image may poke [ROOMY_BLEED] past the top and right edges, so the
 * visible part is what has to fit.
 */
private data class RoomyArt(val size: Float, val bleedRight: Float, val bleedTop: Float)

/**
 * Weather art when the clock has room to spare, or null when it's tight and the art should run off
 * the corner at its usual size.
 *
 * Wide clocks (2×1-style): the art is up to about as tall as the widget, fills the space beside the
 * time and runs off the top and right edges.
 * 2×2-style clocks: the art fills the height above the time, next to the city labels.
 */
private fun roomyArt(
    layout: ClockLayout, size: DpSize, fit: Fit, timeSize: Float, contentBelow: Float,
    pattern: String, singleDigitHour: Boolean, z: Float,
): RoomyArt? {
    val pad = 12 * fit.k
    return when (layout) {
        ClockLayout.SMALL -> {
            val bleedRight = .16f
            val tuck = .05f // a sliver of the art may sit behind the end of the time, no more
            val timeWidth = timeSize * timeInkEm(pattern, singleDigitHour)
            val beside = size.width.value - pad - timeWidth - 4 * fit.k
            val art = minOf(beside / (1 - bleedRight - tuck), size.height.value * 1.1f)
            if (art >= z) RoomyArt(art, bleedRight, .14f) else null
        }
        ClockLayout.SQUARE -> {
            // Above the time (and the hours row); up to about 60% of the width, beside the city labels.
            val visible = 1 - ROOMY_BLEED
            val above = size.height.value - pad - contentBelow - 16 * fit.k
            if (above >= z * .6f) RoomyArt(minOf(above / visible, size.width.value * .6f, z * 1.8f), ROOMY_BLEED, ROOMY_BLEED) else null
        }
        ClockLayout.TINY -> null
    }
}

/** Height of the clock's hours row at design scale. */
private const val HOURS_ROW_DP = 50f

/** The hours row grows with the widget only up to this scale, so it stays a supporting detail. */
private const val HOURS_MAX_K = 1.25f

/** The city's next hours (hour, condition, temperature) in a soft panel, like the weather widget's strip. [fit] is the row's own, capped scale. */
@Composable
private fun ClockHours(f: Forecast, units: Units, pal: WidgetPalette, fit: Fit, h24: Boolean, count: Int) {
    val k = fit::dp
    val ink = ColorProvider(Color(pal.ink))
    Row(
        GlanceModifier.fillMaxWidth().height(k(HOURS_ROW_DP)).background(ColorProvider(Color(pal.ink).copy(alpha = .11f)))
            .cornerRadius(k(14f)).padding(vertical = k(4f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        f.hourly.drop(1).take(count).forEach { h ->
            val phase = TimeOfDay.phase(h.hour * 60, f.sunriseMin, f.sunsetMin)
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                val label = if (h24) "%02d".format(h.hour) else "${if (h.hour % 12 == 0) 12 else h.hour % 12} ${if (h.hour < 12) "AM" else "PM"}"
                Text(label, style = TextStyle(color = ink, fontSize = fit.sp(9.5f).sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Image(ImageProvider(Art.drawable(h.condition, phase)), null, GlanceModifier.size(k(15f)))
                Numeral("${units.temp(h.tempC)}°", 13 * fit.k, pal.ink)
            }
        }
    }
}

/** " · 13°": the city's temperature after another label. The art already shows the condition. */
@Composable
private fun WeatherBit(f: Forecast, units: Units, style: (Float, FontWeight) -> TextStyle, sp: Float) {
    Text(" · ${units.temp(f.nowTempC)}°", style = style(sp, FontWeight.Bold), maxLines = 1)
}

/** "NY" for New York, "TOK" for Tokyo: initials for several words, else the first three letters. */
internal fun cityCode(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    return (if (words.size > 1) words.joinToString("") { it.take(1) } else name.take(3)).uppercase()
}

/**
 * Rough width of a time pattern in ems for the system's condensed bold face:
 * digits about 0.56 em, separators and spaces 0.27 em; "AM"/"PM" is drawn at 42% of the size.
 */
/**
 * Closer estimate of how wide the time actually draws, for working out the space beside it
 * (digits about 0.5 em, separators 0.22 em, AM/PM at 42%). [timeWidthEm] stays generous because
 * it decides whether the time fits at all.
 */
internal fun timeInkEm(pattern: String, singleDigitHour: Boolean): Float {
    val digits = pattern.count { it == 'H' || it == 'm' || it == 's' } + if (pattern.startsWith("h")) (if (singleDigitHour) 1 else 2) else 0
    val separators = pattern.count { it == ':' }
    val amPm = if (pattern.contains('a')) 1.1f * .42f + .12f else 0f
    return digits * .5f + separators * .22f + amPm
}

internal fun timeWidthEm(pattern: String): Float {
    val digits = pattern.count { it == 'H' || it == 'm' || it == 's' } + if (pattern.startsWith("h")) 2 else 0
    val separators = pattern.count { it == ':' || it == ' ' }
    val amPm = if (pattern.contains('a')) 1.25f * .42f + .15f else 0f
    return digits * .56f + separators * .27f + amPm
}
