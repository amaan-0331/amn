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
import dev.amaan.amn.widgets.core.english
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
        val SMALL = DpSize(140.dp, 70.dp)
        val SQUARE = DpSize(140.dp, 140.dp)
        val WIDE = DpSize(290.dp, 140.dp)
        val TALL = DpSize(290.dp, 215.dp)
        val LARGE = DpSize(290.dp, 290.dp)

        /** After this, the widget shows a warning next to the time. */
        const val STALE_MS = 3 * 60 * 60_000L
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, SQUARE, WIDE, TALL, LARGE))

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
            WeatherContent(state, preview?.previewWidgetId ?: appWidgetId, isPreview = preview != null)
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

private class Look(val pal: WidgetPalette) {
    val ink = ColorProvider(Color(pal.ink))
    val fill = ColorProvider(Color(pal.ink).copy(alpha = .15f))
    val panel = ColorProvider(Color(pal.ink).copy(alpha = .11f))
    val track = ColorProvider(Color(pal.ink).copy(alpha = .18f))
    fun style(sp: Float, weight: FontWeight = FontWeight.Medium, align: TextAlign = TextAlign.Start) =
        TextStyle(color = ink, fontSize = sp.sp, fontWeight = weight, textAlign = align)
    val numberFamily = FontFamily("sans-serif-condensed")
}

@Composable
private fun WeatherContent(state: WeatherState, appWidgetId: Int, isPreview: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = Instant.now()
    val cached = ForecastCache(context).load(appWidgetId)
    val live = cached?.let { ForecastBuilder.build(it.raw, now) }
    // Real widgets wait for data; the preview shows sample weather until the first update.
    val f: Forecast = live ?: if (isPreview) SampleForecast.at(LocalTime.now().hour) else return LoadingContent(state, appWidgetId)
    val localNow = cached?.raw?.let { ForecastBuilder.localNow(it, now) }?.toLocalTime() ?: LocalTime.now()
    val zone = cached?.raw?.timeZone?.takeIf { live != null }

    val phase = TimeOfDay.phase(localNow.hour * 60 + localNow.minute, f.sunriseMin, f.sunsetMin)
    val look = Look(Palettes.of(f.condition, phase, context.isNightMode()))
    val h24 = DateFormat.is24HourFormat(context)
    val line = Summary.of(f).english(h24)
    val u = state.units
    fun t(c: Double) = "${u.temp(c)}°"
    val place = if (state.settings.useGps) cached?.placeName ?: "Current location" else state.settings.cityName
    val stale = live != null && System.currentTimeMillis() - f.fetchedAtEpochMs > WeatherWidget.STALE_MS

    val wide = size.width >= WeatherWidget.WIDE.width
    val tall = size.height >= WeatherWidget.SQUARE.height
    val big = wide && size.height >= WeatherWidget.TALL.height
    val large = wide && size.height >= WeatherWidget.LARGE.height

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(look.pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "weather")),
        contentAlignment = Alignment.TopEnd,
    ) {
        val art = ImageProvider(Art.drawable(f.condition, phase))
        when {
            big -> Image(art, null, GlanceModifier.size(92.dp).padding(top = 30.dp, end = 4.dp))
            wide -> Image(art, null, GlanceModifier.size(92.dp))
            tall -> Image(art, null, GlanceModifier.size(96.dp))
            else -> Image(art, null, GlanceModifier.size(60.dp).padding(2.dp))
        }
        Column(GlanceModifier.fillMaxSize().padding(horizontal = if (big) 14.dp else 12.dp, vertical = if (big) 12.dp else 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(place, style = look.style(if (big) 12f else 11f, FontWeight.Bold), maxLines = 1)
                Text("  ", style = look.style(11f))
                ClockText(zone, if (h24) "HH:mm" else "h:mm a", if (big) 12f else 11f, look.pal.ink)
                if (stale && !big) Text("  ⚠", style = look.style(11f, FontWeight.Bold))
                if (big) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(updatedLabel(f.fetchedAtEpochMs, live == null, stale), style = look.style(10f), maxLines = 1)
                }
            }
            when {
                big -> {
                    Text(line, style = look.style(18f, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        DisplayText(t(f.nowTempC), 68f, look.pal.ink)
                        Column(GlanceModifier.padding(start = 10.dp, bottom = 4.dp)) {
                            Text(CONDITION_LABEL.getValue(f.condition), style = look.style(11.5f, FontWeight.Bold))
                            Text("Feels ${t(f.feelsLikeC)}", style = look.style(11.5f))
                            Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)}", style = look.style(11.5f))
                        }
                    }
                    Text("💧 ${f.today.pop}% chance · ${amount(f, u)} today", style = look.style(11.5f), maxLines = 1)
                    if (large) {
                        Spacer(GlanceModifier.height(4.dp))
                        HourlyStrip(f, look, u, withPop = true, h24 = h24)
                        Spacer(GlanceModifier.defaultWeight())
                        DailyRows(f, look, u, rowWidth = size.width - 28.dp)
                    } else {
                        Spacer(GlanceModifier.defaultWeight())
                        HourlyStrip(f, look, u, withPop = true, h24 = h24)
                    }
                }
                wide -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        DisplayText(t(f.nowTempC), 52f, look.pal.ink)
                        Column(GlanceModifier.padding(start = 10.dp, bottom = 2.dp).width(124.dp)) {
                            Text(line, style = look.style(14f, FontWeight.Bold), maxLines = 2)
                            Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)} · Feels ${t(f.feelsLikeC)}", style = look.style(10.5f), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    HourlyStrip(f, look, u, withPop = false, h24 = h24)
                }
                tall -> {
                    Spacer(GlanceModifier.defaultWeight())
                    DisplayText(t(f.nowTempC), 58f, look.pal.ink)
                    Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)}", style = look.style(11.5f, FontWeight.Medium), maxLines = 1)
                    Spacer(GlanceModifier.height(6.dp))
                    Box(GlanceModifier.background(look.fill).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(line, style = look.style(12f, FontWeight.Bold), maxLines = 1)
                    }
                }
                else -> {
                    DisplayText(t(f.nowTempC), 34f, look.pal.ink)
                    Spacer(GlanceModifier.defaultWeight())
                    Text(line, style = look.style(11.5f, FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HourlyStrip(f: Forecast, look: Look, u: Units, withPop: Boolean, h24: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().background(look.panel).cornerRadius(14.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        f.hourly.take(6).forEachIndexed { i, h ->
            val phase = TimeOfDay.phase(h.hour * 60, f.sunriseMin, f.sunsetMin)
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "Now" else hourLabel(h.hour, h24), style = look.style(9.5f, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                Image(ImageProvider(Art.drawable(h.condition, phase)), null, GlanceModifier.size(if (withPop) 16.dp else 14.dp))
                Text("${u.temp(h.tempC)}°", style = TextStyle(color = look.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = look.numberFamily))
                if (withPop) Text(if (h.pop >= 20) "${h.pop}%" else " ", style = look.style(9.5f, FontWeight.Bold, TextAlign.Center))
            }
        }
    }
}

@Composable
private fun DailyRows(f: Forecast, look: Look, u: Units, rowWidth: Dp) {
    val all = f.days.flatMap { listOf(it.lowC, it.highC) }
    val mn = all.min()
    val span = (all.max() - mn).coerceAtLeast(1.0)
    // Fixed columns: day 58, icon 18, chance 30, low 26, high 26, plus five 6dp gaps.
    val barWidth = (rowWidth - 188.dp).coerceAtLeast(30.dp)
    Column(GlanceModifier.fillMaxWidth()) {
        f.days.take(3).forEachIndexed { i, d ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "Tomorrow" else d.weekday, style = look.style(11.5f, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.width(58.dp))
                Spacer(GlanceModifier.width(6.dp))
                Image(ImageProvider(Art.drawable(d.condition, Phase.DAY)), null, GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(if (d.pop >= 20) "${d.pop}%" else "", style = look.style(10f, FontWeight.Bold), modifier = GlanceModifier.width(30.dp))
                Text("${u.temp(d.lowC)}°", style = look.style(13f, FontWeight.Bold, TextAlign.End), modifier = GlanceModifier.width(26.dp))
                Spacer(GlanceModifier.width(6.dp))
                val start = barWidth * ((d.lowC - mn) / span).toFloat()
                val fill = (barWidth * ((d.highC - d.lowC) / span).toFloat()).coerceAtLeast(6.dp)
                Box(GlanceModifier.width(barWidth).height(5.dp).background(look.track).cornerRadius(3.dp)) {
                    Row {
                        Spacer(GlanceModifier.width(start))
                        Box(GlanceModifier.width(fill).height(5.dp).background(look.ink).cornerRadius(3.dp)) {}
                    }
                }
                Spacer(GlanceModifier.width(6.dp))
                Text("${u.temp(d.highC)}°", style = look.style(13f, FontWeight.Bold, TextAlign.End), modifier = GlanceModifier.width(26.dp))
            }
        }
    }
}

/** Shown on the home screen until the first forecast arrives. */
@Composable
private fun LoadingContent(state: WeatherState, appWidgetId: Int) {
    val context = LocalContext.current
    val now = LocalTime.now()
    val look = Look(Palettes.of(Condition.CLOUDY, TimeOfDay.phaseForHour(now.hour, now.minute), context.isNightMode()))
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

private fun hourLabel(h: Int, h24: Boolean): String =
    if (h24) String.format(Locale.US, "%02d", h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

private fun amount(f: Forecast, u: Units): String =
    if (f.today.snowCm > 0) {
        if (u.inches) String.format(Locale.US, "%.1f in snow", f.today.snowCm / 2.54) else String.format(Locale.US, "%.1f cm snow", f.today.snowCm)
    } else {
        if (u.inches) String.format(Locale.US, "%.2f in", f.today.precipMm / 25.4) else String.format(Locale.US, "%.1f mm", f.today.precipMm)
    }

private fun updatedLabel(fetchedAt: Long, sample: Boolean, stale: Boolean): String {
    if (sample) return "Sample data"
    val min = ((System.currentTimeMillis() - fetchedAt) / 60_000).coerceAtLeast(0)
    val ago = when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        else -> "${min / 60} h ago"
    }
    return if (stale) "⚠ Updated $ago" else "Updated $ago"
}

