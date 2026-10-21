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
            val settings = glanceState as? ClockSettings ?: store.clock(appWidgetId)
            ClockContent(settings, appWidgetId)
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
private fun ClockContent(s: ClockSettings, appWidgetId: Int) {
    val context = LocalContext.current
    val size = LocalSize.current
    val zone = s.zoneOrPhone()
    val now = Instant.now()
    val phase = ClockTimeline.phaseAt(now, zone, s.sun())
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
    val timeSize = when {
        tiny -> 28f
        square -> 54f
        else -> 38f
    }
    val artSize = when {
        tiny -> 44
        square -> 96
        else -> 64
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
        Image(ImageProvider(Art.drawable(Condition.CLEAR, phase)), contentDescription = null, modifier = GlanceModifier.size((artSize * if (low) .7 else 1.0).dp))
        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            val title = s.cityName ?: "Choose a city"
            Text(
                if (tiny) title.take(3).uppercase() else title,
                style = TextStyle(color = ink, fontSize = if (square) 15.sp else 11.5.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (square) Text(phaseLabel, style = TextStyle(color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            ClockText(zone.id, pattern, timeSize, pal.ink)
            if (!tiny) Text(offset, style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp))
        }
    }
}
