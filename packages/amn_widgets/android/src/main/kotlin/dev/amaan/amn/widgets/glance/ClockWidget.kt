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
    val timeSize = minOf(designTime * fit.k, available / timeWidthEm(pattern.substringBefore('\n')))
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
            val roomy = roomyArtSize(layout, size, fit, timeSize, pattern, z)
            if (roomy != null) {
                // Room to spare: a bigger image that runs just slightly off the top-right corner,
                // so it fills the space without being cut off much.
                BleedArt(Art.drawable(sky, phase), roomy, bleedRight = roomy * ROOMY_BLEED, bleedTop = roomy * ROOMY_BLEED)
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
private fun roomyArtSize(layout: ClockLayout, size: DpSize, fit: Fit, timeSize: Float, pattern: String, z: Float): Float? {
    val pad = 12 * fit.k
    val visible = 1 - ROOMY_BLEED
    return when (layout) {
        ClockLayout.SMALL -> {
            val beside = size.width.value - pad - timeSize * timeWidthEm(pattern) - 10 * fit.k
            if (beside >= z * .9f) minOf(beside / visible, size.height.value * 1.05f, z * 2f) else null
        }
        ClockLayout.SQUARE -> {
            // Above the time and the offset line; up to about 60% of the width, beside the city labels.
            val above = size.height.value - pad - timeSize - 16 * fit.k
            if (above >= z * .6f) minOf(above / visible, size.width.value * .6f, z * 1.8f) else null
        }
        ClockLayout.TINY -> null
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
internal fun timeWidthEm(pattern: String): Float {
    val digits = pattern.count { it == 'H' || it == 'm' || it == 's' } + if (pattern.startsWith("h")) 2 else 0
    val separators = pattern.count { it == ':' || it == ' ' }
    val amPm = if (pattern.contains('a')) 1.25f * .42f + .15f else 0f
    return digits * .56f + separators * .27f + amPm
}
