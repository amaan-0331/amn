package dev.amaan.amn.widgets.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.format.DateFormat
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.amaan.amn.widgets.core.Palettes
import dev.amaan.amn.widgets.core.Summary
import dev.amaan.amn.widgets.core.TimeOfDay
import dev.amaan.amn.widgets.core.english
import dev.amaan.amn.widgets.data.SampleForecast
import dev.amaan.amn.widgets.data.WeatherState
import dev.amaan.amn.widgets.data.WidgetStore
import java.time.LocalTime

class WeatherWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(140.dp, 70.dp)
        val SQUARE = DpSize(140.dp, 140.dp)
        val WIDE = DpSize(290.dp, 140.dp)
        val TALL = DpSize(290.dp, 215.dp)
        val LARGE = DpSize(290.dp, 290.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, SQUARE, WIDE, TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val store = WidgetStore(context)
        val stored = WeatherState(store.weather(appWidgetId), store.units())
        provideContent {
            // The edit page's preview passes unsaved settings as state.
            val state = LocalState.current as? WeatherState ?: stored
            WeatherContent(state, appWidgetId)
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::remove)
    }
}

/**
 * First pass of the layouts in the design spec. Still to do: hourly strip (4×2+),
 * precipitation row and 3-day outlook (4×3/4×4), bleed crops, Barlow Condensed numerals.
 */
@Composable
private fun WeatherContent(state: WeatherState, appWidgetId: Int) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = LocalTime.now()
    val f = SampleForecast.at(now.hour)
    val phase = TimeOfDay.phase(now.hour * 60 + now.minute, f.sunriseMin, f.sunsetMin)
    val pal = Palettes.of(f.condition, phase, context.isNightMode())
    val ink = ColorProvider(Color(pal.ink))
    val h24 = DateFormat.is24HourFormat(context)
    val line = Summary.of(f).english(h24)
    fun t(c: Double) = "${state.units.temp(c)}°"

    val wide = size.width >= WeatherWidget.WIDE.width
    val tall = size.height >= WeatherWidget.SQUARE.height
    val artSize = when {
        wide -> 92.dp
        tall -> 96.dp
        else -> 60.dp
    }
    val city = if (state.settings.useGps) state.settings.cityName else state.settings.cityName

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "weather")),
        contentAlignment = Alignment.TopEnd,
    ) {
        Image(ImageProvider(Art.drawable(f.condition, phase)), contentDescription = null, modifier = GlanceModifier.size(artSize).padding(2.dp))
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city, style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text("  ", style = TextStyle(color = ink, fontSize = 11.sp))
                ClockText(null, if (h24) "HH:mm" else "h:mm a", 11f, pal.ink)
            }
            Text(
                t(f.nowTempC),
                style = TextStyle(color = ink, fontSize = if (tall) 54.sp else 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed")),
                maxLines = 1,
            )
            if (tall) {
                val extra = if (wide) " · Feels ${t(f.feelsLikeC)}" else ""
                Text("H ${t(f.today.highC)} · L ${t(f.today.lowC)}$extra", style = TextStyle(color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(line, style = TextStyle(color = ink, fontSize = if (wide) 15.sp else 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}
