package dev.amaan.amn.widgets.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.amaan.amn.widgets.core.ClockTimeline
import dev.amaan.amn.widgets.core.SunDay
import dev.amaan.amn.widgets.core.SunTimes
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.WeatherWidget
import dev.amaan.amn.widgets.glance.redraw
import dev.amaan.amn.widgets.glance.redrawAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Sunrise/sunset for a clock's city, from its coordinates when known. */
fun ClockSettings.sun(): (LocalDate) -> SunDay? {
    val zone = zoneOrPhone()
    val la = lat
    val lo = lon
    return { d -> if (la != null && lo != null) SunTimes.compute(d, la, lo, zone) else null }
}

fun ClockSettings.zoneOrPhone(): ZoneId = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** Redraws a clock widget at its next phase change or midnight. The time itself ticks in a TextClock. */
object ClockSchedule {
    private const val KEY_ID = "id"

    fun scheduleNext(context: Context, appWidgetId: Int, s: ClockSettings) {
        // In 12-hour time the hour goes from one digit to two at 10:00 and 22:00, and back at 1:00 and 13:00.
        val digitChanges = if (s.h24) emptyList() else listOf(60, 600, 780, 1320)
        val next = ClockTimeline.nextUpdate(Instant.now(), s.zoneOrPhone(), ZoneId.systemDefault(), s.sun(), digitChanges)
        val delay = Duration.between(Instant.now(), next).toMillis().coerceAtLeast(0) + 5_000
        WorkManager.getInstance(context).enqueueUniqueWork(
            "amn-clock-$appWidgetId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ClockUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to appWidgetId))
                .build(),
        )
    }

    fun cancel(context: Context, appWidgetId: Int) =
        WorkManager.getInstance(context).cancelUniqueWork("amn-clock-$appWidgetId")

    class ClockUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = inputData.getInt(KEY_ID, -1)
            val glanceId = runCatching { GlanceAppWidgetManager(applicationContext).getGlanceIdBy(id) }.getOrNull()
            if (glanceId != null) {
                ClockWidget().redraw(applicationContext, glanceId)
                scheduleNext(applicationContext, id, WidgetStore(applicationContext).clock(id))
            }
            return Result.success()
        }
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ClockWidget().redrawAll(context)
                WeatherWidget().redrawAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
