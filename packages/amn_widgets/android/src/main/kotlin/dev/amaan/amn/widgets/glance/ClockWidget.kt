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
import dev.amaan.amn.widgets.data.ClockSchedule
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.WidgetStore
import dev.amaan.amn.widgets.data.sun
import dev.amaan.amn.widgets.data.zoneOrPhone
import java.time.Instant
import java.time.ZoneId

class ClockWidget : GlanceAppWidget() {
    companion object {
        val TINY = DpSize(70.dp, 70.dp)
        val SMALL = DpSize(140.dp, 70.dp)
        val SQUARE = DpSize(140.dp, 140.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TINY, SMALL, SQUARE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val store = WidgetStore(context)
        val initial = store.clock(appWidgetId)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && initial.timeZone != null) {
            ClockSchedule.scheduleNext(context, appWidgetId, initial)
        }
        provideContent {
            val glanceState = LocalState.current
            glanceState.renderVersion()
            val preview = glanceState as? ClockPreview
            ClockContent(preview?.settings ?: store.clock(appWidgetId), appWidgetId, preview?.debug?.hour)
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach {
            store.remove(it)
            ClockSchedule.cancel(context, it)
        }
    }
}

@Composable
private fun ClockContent(s: ClockSettings, appWidgetId: Int, debugHour: Int? = null) {
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
    val pal = Palettes.of(Condition.CLEAR, phase, context.isNightMode())
    val ink = ColorProvider(Color(pal.ink))
    val offset = CityOffset.between(zone, ZoneId.systemDefault(), now).label

    val tiny = size.width < ClockWidget.SMALL.width
    val square = size.height >= ClockWidget.SQUARE.height
    val withSeconds = s.seconds && !tiny
    val pattern = buildString {
        append(if (s.h24) "HH" else "h")
        append(if (tiny) "\n" else ":")
        append("mm")
        if (withSeconds) append(":ss")
        if (!s.h24 && !tiny) append(" a")
    }
    // Sized so "HH:mm:ss" and "h:mm:ss a" still fit on one line.
    val timeSize = when {
        tiny -> 23f
        square -> if (withSeconds || !s.h24) 38f else 52f
        else -> if (withSeconds || !s.h24) 28f else 36f
    }
    val artSize = when {
        tiny -> 40
        square -> 88
        else -> 56
    }
    val phaseLabel = when (phase) {
        Phase.DAWN -> "Dawn"; Phase.DAY -> "Day"; Phase.DUSK -> "Dusk"; Phase.NIGHT -> "Night"
    }
    // By day the sun sits high in the corner; at dawn and dusk it sits half below the bottom edge.
    val low = phase == Phase.DAWN || phase == Phase.DUSK

    Box(
        modifier = GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ImageProvider(Backgrounds.gradient(pal)), ContentScale.FillBounds)
            .clickable(openEditPage(context, appWidgetId, "clock")),
        contentAlignment = if (low) Alignment.BottomEnd else Alignment.TopEnd,
    ) {
        if (low) {
            val sun = Horizon.topHalf(context, Art.drawable(Condition.CLEAR, phase))
            Image(ImageProvider(sun), null, GlanceModifier.width((artSize * .8).dp).height((artSize * .8 * .55).dp))
        } else {
            Image(ImageProvider(Art.drawable(Condition.CLEAR, phase)), null, GlanceModifier.size(artSize.dp))
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (square) 10.dp else 7.dp)) {
            val title = s.cityName ?: "Choose a city"
            Text(
                if (tiny) cityCode(title) else title,
                style = TextStyle(color = ink, fontSize = if (square) 15.sp else 11.5.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (square) Text(phaseLabel, style = TextStyle(color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            // AndroidRemoteViews otherwise takes all remaining height and pushes the offset line out.
            ClockText(zone.id, pattern, timeSize, pal.ink, GlanceModifier.height((timeSize * if (tiny) 1.75f else .95f).dp))
            if (!tiny) Text(offset, style = TextStyle(color = ink, fontSize = if (square) 11.sp else 10.5.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

/** "NY" for New York, "TOK" for Tokyo: initials for several words, else the first three letters. */
internal fun cityCode(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    return (if (words.size > 1) words.joinToString("") { it.take(1) } else name.take(3)).uppercase()
}
