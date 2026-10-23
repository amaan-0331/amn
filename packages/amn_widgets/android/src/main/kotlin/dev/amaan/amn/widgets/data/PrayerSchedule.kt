package dev.amaan.amn.widgets.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.amaan.amn.widgets.core.PrayerMoment
import dev.amaan.amn.widgets.glance.PrayerWidget
import dev.amaan.amn.widgets.glance.redrawAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Redraws the prayer widgets at each of the day's six times and every quarter hour between
 * (PrayerMoment.nextRedraw). One alarm serves every prayer widget, since they share an area.
 * It's exact so a countdown never runs past its prayer, and RTC rather than a wakeup: while
 * the screen is off it waits for the phone to wake.
 */
object PrayerSchedule {
    fun scheduleNext(context: Context) {
        val store = PrayerStore(context)
        val at = PrayerMoment.nextRedraw(store.timetable(), store.area().zone, Instant.now()).toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) alarms.setExact(AlarmManager.RTC, at, pending(context))
        else alarms.set(AlarmManager.RTC, at, pending(context))
    }

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(pending(context))

    /** Redraws now (the area, the timetable or the clock changed) and schedules the next one, if there are prayer widgets. */
    suspend fun redraw(context: Context) = withContext(Dispatchers.Default) {
        if (GlanceAppWidgetManager(context).getGlanceIds(PrayerWidget::class.java).isEmpty()) return@withContext cancel(context)
        PrayerWidget().redrawAll(context)
        scheduleNext(context)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, Receiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    redraw(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
