package dev.amaan.amn.widgets.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.Forecast
import dev.amaan.amn.widgets.core.ForecastBuilder
import dev.amaan.amn.widgets.core.Palettes
import dev.amaan.amn.widgets.core.Phase
import dev.amaan.amn.widgets.core.Summary
import dev.amaan.amn.widgets.core.TimeOfDay
import dev.amaan.amn.widgets.core.WidgetPalette
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.SampleForecast
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.data.WeatherSchedule
import dev.amaan.amn.widgets.data.WeatherState
import dev.amaan.amn.widgets.data.WidgetStore
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

class WeatherWidget : GlanceAppWidget() {
    companion object {
        /** After this, the widget shows a warning next to the time. */
        const val STALE_MS = 3 * 60 * 60_000L

        /** For this long after an update the refresh button says "Just now" and ignores taps. */
        const val JUST_NOW_MS = 2 * 60_000L
    }

    // Exact: the layout is picked from the real size and shape (see WeatherLayout.of).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val store = WidgetStore(context)
        provideContent {
            val glanceState = LocalState.current
            // The edit page's preview passes unsaved settings as state.
            val preview = glanceState as? WeatherState
            // Reading the render version makes redraw() recompose this with fresh settings and forecast.
            glanceState.renderVersion()
            val state = preview ?: WeatherState(store.weather(appWidgetId), store.units())
            WeatherContent(state, preview?.previewWidgetId ?: appWidgetId, isPreview = preview != null, glanceState = glanceState)
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSchedule.ensure(context)
        WeatherSchedule.now(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherSchedule.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        val cache = ForecastCache(context)
        appWidgetIds.forEach { store.remove(it); cache.remove(it) }
    }
}

private val CONDITION_LABEL = mapOf(
    Condition.CLEAR to "Clear", Condition.PARTLY to "Partly cloudy", Condition.CLOUDY to "Cloudy",
    Condition.RAIN to "Rain", Condition.STORM to "Thunderstorm", Condition.SNOW to "Snow", Condition.FOG to "Fog",
)

private class Look(val pal: WidgetPalette, val fit: Fit) {
    val ink = ColorProvider(Color(pal.ink))
    val fill = ColorProvider(Color(pal.ink).copy(alpha = .15f))
    val panel = ColorProvider(Color(pal.ink).copy(alpha = .11f))
    val track = ColorProvider(Color(pal.ink).copy(alpha = .18f))

    /** Text at a design size in sp, scaled to the widget. */
    fun style(sp: Float, weight: FontWeight = FontWeight.Medium, align: TextAlign = TextAlign.Start) =
        TextStyle(color = ink, fontSize = fit.sp(sp).sp, fontWeight = weight, textAlign = align)

    @Composable
    fun Num(text: String, designDp: Float) = Numeral(text, designDp * fit.k, pal.ink)
}

/** The five weather layouts and their design sizes (dp). */
internal enum class WeatherLayout(val w: Float, val h: Float, val maxSummary: Int) {
    SMALL(140f, 70f, 17), SQUARE(140f, 140f, 16), // 2×2 shares its bottom row with the refresh button
    WIDE(290f, 140f, 22), TALL(290f, 215f, 24), LARGE(290f, 290f, 24);

    companion object {
        /** By width and shape: 4×2 is about twice as wide as tall, 4×3 a bit wider than tall. */
        fun of(size: DpSize): WeatherLayout {
            val ratio = size.height.value / size.width.value
            return if (size.width < 215.dp) {
                if (ratio < .75f) SMALL else SQUARE
            } else when {
                ratio < .66f -> WIDE
                ratio < .9f -> TALL
                else -> LARGE
            }
        }
    }
}

@Composable
private fun WeatherContent(state: WeatherState, appWidgetId: Int, isPreview: Boolean, glanceState: Any?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = Instant.now()
    val debug = state.debug
    val cached = if (debug == null) ForecastCache(context).load(appWidgetId) else null
    val live = cached?.let { ForecastBuilder.build(it.raw, now) }
    // Real widgets wait for data; the preview shows sample weather until the first update.
    val f: Forecast = when {
        debug != null -> SampleForecast.of(debug.condition, debug.hour)
        live != null -> live
        isPreview -> SampleForecast.at(LocalTime.now().hour)
        else -> return LoadingContent(state, appWidgetId)
    }
    val localNow = when {
        debug != null -> LocalTime.of(debug.hour, 0)
        else -> cached?.raw?.let { ForecastBuilder.localNow(it, now) }?.toLocalTime() ?: LocalTime.now()
    }
    val zone = cached?.raw?.timeZone?.takeIf { live != null }
    val layout = WeatherLayout.of(size)

    val fit = fitFor(size, layout.w, layout.h)
    val phase = TimeOfDay.phase(localNow.hour * 60 + localNow.minute, f.sunriseMin, f.sunsetMin)
    val look = Look(Palettes.of(f.condition, phase, context.isNightMode()), fit)
    val h24 = debug?.h24 ?: DateFormat.is24HourFormat(context)
    val u = state.units
    // A new seed every two hours (offset per widget) rotates the line without flicker.
    val seed = debug?.seed ?: (System.currentTimeMillis() / 7_200_000L + appWidgetId)
    val fontScale = context.resources.configuration.fontScale
    val maxSummary = if (layout == WeatherLayout.SQUARE) squareChipChars(size, fit, fontScale) else layout.maxSummary
    val line = Summary.of(f, h24, deg = { "${u.temp(it)}°" }, maxLen = maxSummary, seed = seed).text
    // Spare height becomes forecast days rather than gaps: 4×3 gains rows when the slot is taller
    // than the design's proportions, 4×4 shows up to five days.
    val dayRows = when (layout) {
        WeatherLayout.TALL -> ((size.height.value - layout.h * fit.k) / (DAY_ROW_DP * fit.k)).toInt()
        WeatherLayout.LARGE -> 3 + ((size.height.value - layout.h * fit.k) / (DAY_ROW_DP * fit.k)).toInt()
        else -> 0
    }.coerceIn(0, f.days.size)
    fun t(c: Double) = "${u.temp(c)}°"
    val place = if (state.settings.useGps) cached?.placeName ?: "Current location" else state.settings.cityName
    val stale = live != null && System.currentTimeMillis() - f.fetchedAtEpochMs > WeatherWidget.STALE_MS
    val big = layout == WeatherLayout.TALL || layout == WeatherLayout.LARGE
    val refresh = debug?.refresh?.let { runCatching { RefreshUi.valueOf(it.uppercase()) }.getOrNull() }
        ?: refreshUi(glanceState, live?.fetchedAtEpochMs)
    val refreshLabel = when (refresh) {
        RefreshUi.BUSY -> "Updating…"
        RefreshUi.DONE -> "Just now"
        RefreshUi.OFFLINE -> "Offline"
        // The lab shows what users see ("12 min ago"); the edit page's preview admits it's sample data.
        RefreshUi.IDLE -> agoLabel(f.fetchedAtEpochMs, live == null && debug == null, stale)
    }
    val wide = layout != WeatherLayout.SMALL && layout != WeatherLayout.SQUARE
    val k = fit::dp

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(look.pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "weather")),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Art size and how far it runs off the corner, from the design (dp, before scaling).
        val art = Art.drawable(f.condition, phase)
        when (layout) {
            WeatherLayout.TALL, WeatherLayout.LARGE -> BleedArt(art, 118 * fit.k, bleedRight = 12 * fit.k, modifier = GlanceModifier.padding(top = k(30f)))
            WeatherLayout.WIDE -> BleedArt(art, 104 * fit.k, bleedRight = 8 * fit.k, bleedTop = 16 * fit.k)
            WeatherLayout.SQUARE -> BleedArt(art, 96 * fit.k, bleedRight = 18 * fit.k, bleedTop = 14 * fit.k)
            WeatherLayout.SMALL -> BleedArt(art, 64 * fit.k, bleedRight = 10 * fit.k, modifier = GlanceModifier.padding(top = k(3f)))
        }
        val padH = if (big) 14f else 12f
        Column(
            GlanceModifier.fillMaxSize().padding(
                start = k(padH), end = k(padH),
                top = k(if (big) 13f else if (wide) 11f else if (layout == WeatherLayout.SQUARE) 12f else 8f),
                bottom = k(if (big) 13f else if (wide) 9f else if (layout == WeatherLayout.SQUARE) 12f else 7f),
            ),
        ) {
            val metaSize = when {
                big -> 12f
                layout == WeatherLayout.SMALL -> 10.5f
                else -> 11f
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val wideButtonLabel = layout == WeatherLayout.WIDE && refresh != RefreshUi.IDLE
                Text(ellipsize(place, if (wideButtonLabel) 12 else if (wide) 22 else 11), style = look.style(metaSize, FontWeight.Bold), maxLines = 1)
                Text(" · ", style = look.style(metaSize))
                // AndroidRemoteViews otherwise stretches to the end of the row and squeezes out the refresh
                // button, so the time gets a width for its longest text ("12:59 PM") and a matching height.
                val metaPx = fit.sp(metaSize) * LocalContext.current.resources.configuration.fontScale
                ClockText(
                    zone, if (h24) "HH:mm" else "h:mm a", fit.sp(metaSize), look.pal.ink,
                    GlanceModifier.width((metaPx * if (h24) 2.9f else 4.6f).dp).height((metaPx * 1.3f).dp),
                )
                if (stale && !big) Text("  ⚠", style = look.style(metaSize, FontWeight.Bold))
                when {
                    // 4×3 and 4×4: the button and the update time share one chip, top right.
                    big -> {
                        Spacer(GlanceModifier.defaultWeight())
                        RefreshControl(look, refresh, refreshLabel, isPreview)
                    }
                    // 4×2: right after the time; it only gets words while updating or just updated.
                    layout == WeatherLayout.WIDE -> {
                        Spacer(GlanceModifier.width(k(6f)))
                        RefreshControl(look, refresh, if (refresh == RefreshUi.IDLE) null else refreshLabel, isPreview)
                    }
                    else -> {}
                }
            }
            // Leftover height is shared between the gaps (weighted spacers), not dumped in one place.
            when (layout) {
                WeatherLayout.TALL, WeatherLayout.LARGE -> {
                    Spacer(GlanceModifier.defaultWeight())
                    // Grouped: Glance drops anything past a Column's tenth child.
                    Column {
                        Text(line, style = look.style(18f, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(k(190f)))
                        Spacer(GlanceModifier.height(k(6f)))
                        Row(verticalAlignment = Alignment.Bottom) {
                            look.Num(t(f.nowTempC), 68f)
                            Column(GlanceModifier.padding(start = k(10f))) {
                                Text(CONDITION_LABEL.getValue(f.condition), style = look.style(11.5f, FontWeight.Bold))
                                Text("Feels ${t(f.feelsLikeC)}", style = look.style(11.5f))
                                Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)}", style = look.style(11.5f))
                            }
                        }
                        Spacer(GlanceModifier.height(k(7f)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(ImageProvider(dev.amaan.amn.widgets.R.drawable.amn_droplet), null, GlanceModifier.size(k(13f)))
                            Text(" ${f.today.pop}% chance · ${amount(f, u)} today", style = look.style(11.5f), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    HourlyStrip(f, look, u, withPop = true, h24 = h24)
                    if (dayRows > 0) {
                        Spacer(GlanceModifier.defaultWeight())
                        DailyRows(f, look, u, rowWidth = size.width - k(padH) * 2, count = dayRows)
                    }
                }
                WeatherLayout.WIDE -> {
                    Spacer(GlanceModifier.defaultWeight())
                    Row(verticalAlignment = Alignment.Bottom) {
                        look.Num(t(f.nowTempC), 52f)
                        Column(GlanceModifier.padding(start = k(10f)).width(k(160f))) {
                            Text(line, style = look.style(14f, FontWeight.Bold), maxLines = 1)
                            Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)} · Feels ${t(f.feelsLikeC)}", style = look.style(10.5f), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    HourlyStrip(f, look, u, withPop = false, h24 = h24)
                }
                WeatherLayout.SQUARE -> {
                    Spacer(GlanceModifier.defaultWeight())
                    look.Num(t(f.nowTempC), 60f)
                    Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)}", style = look.style(11.5f, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.padding(top = k(4f)))
                    Spacer(GlanceModifier.defaultWeight())
                    // 2×2: the button sits bottom right, next to the summary chip.
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.background(look.fill).cornerRadius(k(14f)).padding(horizontal = k(10f), vertical = k(5f))) {
                            Text(ellipsize(line, maxSummary), style = look.style(12f, FontWeight.Bold), maxLines = 1)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        RefreshControl(look, refresh, null, isPreview)
                    }
                }
                WeatherLayout.SMALL -> {
                    Spacer(GlanceModifier.defaultWeight())
                    look.Num(t(f.nowTempC), 30f)
                    Spacer(GlanceModifier.defaultWeight())
                    Text(line, style = look.style(11.5f, FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HourlyStrip(f: Forecast, look: Look, u: Units, withPop: Boolean, h24: Boolean) {
    val k = look.fit::dp
    Row(
        GlanceModifier.fillMaxWidth().background(look.panel).cornerRadius(k(14f)).padding(vertical = k(5f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        f.hourly.take(6).forEachIndexed { i, h ->
            val phase = TimeOfDay.phase(h.hour * 60, f.sunriseMin, f.sunsetMin)
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "Now" else hourLabel(h.hour, h24), style = look.style(9.5f, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                Image(ImageProvider(Art.drawable(h.condition, phase)), null, GlanceModifier.size(k(if (withPop) 16f else 14f)).padding(vertical = k(1f)))
                look.Num("${u.temp(h.tempC)}°", 14f)
                if (withPop) Text(if (h.pop >= 20) "${h.pop}%" else " ", style = look.style(9.5f, FontWeight.Bold, TextAlign.Center))
            }
        }
    }
}

@Composable
private fun DailyRows(f: Forecast, look: Look, u: Units, rowWidth: Dp, count: Int) {
    val k = look.fit::dp
    val days = f.days.take(count)
    val all = days.flatMap { listOf(it.lowC, it.highC) }
    val mn = all.min()
    val span = (all.max() - mn).coerceAtLeast(1.0)
    // Fixed columns: day 58, icon 18, chance 30, low 26, high 26, plus five 6dp gaps (188 in all).
    val barWidth = (rowWidth - k(188f)).coerceAtLeast(k(30f))
    Column(GlanceModifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = k(3f)), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "Tomorrow" else d.weekday, style = look.style(11.5f, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.width(k(58f)))
                Spacer(GlanceModifier.width(k(6f)))
                Image(ImageProvider(Art.drawable(d.condition, Phase.DAY)), null, GlanceModifier.size(k(18f)))
                Spacer(GlanceModifier.width(k(6f)))
                Text(if (d.pop >= 20) "${d.pop}%" else "", style = look.style(10f, FontWeight.Bold), modifier = GlanceModifier.width(k(30f)))
                Box(GlanceModifier.width(k(26f)), contentAlignment = Alignment.CenterEnd) { look.Num("${u.temp(d.lowC)}°", 15f) }
                Spacer(GlanceModifier.width(k(6f)))
                val start = barWidth * ((d.lowC - mn) / span).toFloat()
                val fill = (barWidth * ((d.highC - d.lowC) / span).toFloat()).coerceAtLeast(k(6f))
                Box(GlanceModifier.width(barWidth).height(k(5f)).background(look.track).cornerRadius(k(3f))) {
                    Row {
                        Spacer(GlanceModifier.width(start))
                        Box(GlanceModifier.width(fill).height(k(5f)).background(look.ink).cornerRadius(k(3f))) {}
                    }
                }
                Spacer(GlanceModifier.width(k(6f)))
                Box(GlanceModifier.width(k(26f)), contentAlignment = Alignment.CenterEnd) { look.Num("${u.temp(d.highC)}°", 15f) }
            }
        }
    }
}

/** Shown on the home screen until the first forecast arrives. */
@Composable
private fun LoadingContent(state: WeatherState, appWidgetId: Int) {
    val context = LocalContext.current
    val now = LocalTime.now()
    val look = Look(Palettes.of(Condition.CLOUDY, TimeOfDay.phaseForHour(now.hour, now.minute), context.isNightMode()), Fit(1f))
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(look.pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "weather"))
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(if (state.settings.useGps) "Current location" else state.settings.cityName, style = look.style(11f, FontWeight.Bold), maxLines = 1)
            Text("Getting the weather…", style = look.style(13f, FontWeight.Bold), maxLines = 2)
        }
    }
}

/**
 * The refresh button: a ↻ in a soft pill, with an optional [label]. It's dimmed while updating and
 * for two minutes after, when taps are ignored. It's a single Text on purpose: Glance drops views
 * nested too deeply, and a Row holding an image and a text inside this header was dropped.
 */
@Composable
private fun RefreshControl(look: Look, ui: RefreshUi, label: String?, isPreview: Boolean) {
    val k = look.fit::dp
    val dim = ui == RefreshUi.BUSY || ui == RefreshUi.DONE
    val tint = ColorProvider(Color(look.pal.ink).copy(alpha = if (dim) .55f else 1f))
    var pill = GlanceModifier.background(look.fill).cornerRadius(k(99f))
        .padding(horizontal = k(if (label != null) 8f else 7f), vertical = k(3f))
    if (!isPreview) pill = pill.clickable(actionRunCallback<RefreshWeatherAction>())
    Text(
        if (label != null) "↻ $label" else "↻",
        modifier = pill,
        style = TextStyle(color = tint, fontSize = look.fit.sp(if (label != null) 10.5f else 12f).sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

/** Height of one forecast day row at design scale (3 dp padding above and below an 18 dp row). */
private const val DAY_ROW_DP = 24f

/**
 * How many characters the 2×2 summary chip can hold next to the refresh button, from the real
 * width and font size, so the button never gets pushed off the edge.
 */
private fun squareChipChars(size: DpSize, fit: Fit, fontScale: Float): Int {
    val content = size.width.value - 2 * 12 * fit.k - 30 * fit.k - 6 * fit.k - 20 * fit.k
    val charWidth = 12 * fit.k * fontScale * .5f
    return (content / charWidth).toInt().coerceIn(11, 19)
}

/** Glance can't ellipsize reliably, so long names are shortened here. */
private fun ellipsize(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

private fun hourLabel(h: Int, h24: Boolean): String =
    if (h24) String.format(Locale.US, "%02d", h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

private fun amount(f: Forecast, u: Units): String =
    if (f.today.snowCm > 0) {
        if (u.inches) String.format(Locale.US, "%.1f in snow", f.today.snowCm / 2.54) else String.format(Locale.US, "%.1f cm snow", f.today.snowCm)
    } else {
        if (u.inches) String.format(Locale.US, "%.2f in", f.today.precipMm / 25.4) else String.format(Locale.US, "%.1f mm", f.today.precipMm)
    }

/** "12 min ago", "3 h ago" (with a warning when stale), or "Sample data" in previews. */
private fun agoLabel(fetchedAt: Long, sample: Boolean, stale: Boolean): String {
    if (sample) return "Sample data"
    val min = ((System.currentTimeMillis() - fetchedAt) / 60_000).coerceAtLeast(0)
    val ago = when {
        min < 2 -> "Just now"
        min < 60 -> "$min min ago"
        else -> "${min / 60} h ago"
    }
    return if (stale) "⚠ $ago" else ago
}

