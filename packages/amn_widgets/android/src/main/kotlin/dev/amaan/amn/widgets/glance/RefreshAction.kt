package dev.amaan.amn.widgets.glance

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.WeatherRefresher
import dev.amaan.amn.widgets.data.WeatherSchedule

/** When a tap-started refresh began. "Updating…" shows only while this is recent, so it can't stick. */
internal val REFRESH_STARTED = longPreferencesKey("amn_refresh_started")

/** "Offline" shows until this time. */
internal val OFFLINE_UNTIL = longPreferencesKey("amn_offline_until")

/** What the weather widget's refresh button shows. */
enum class RefreshUi { IDLE, BUSY, DONE, OFFLINE }

internal object RefreshTiming {
    /** Longest "Updating…" can show, even if the refresh job is frozen or killed. */
    const val BUSY_MAX_MS = 45_000L
    const val OFFLINE_MS = 5_000L
}

internal fun refreshUi(glanceState: Any?, fetchedAt: Long?): RefreshUi {
    val prefs = glanceState as? Preferences
    val now = System.currentTimeMillis()
    val started = prefs?.get(REFRESH_STARTED) ?: 0L
    return when {
        now - started < RefreshTiming.BUSY_MAX_MS -> RefreshUi.BUSY
        (prefs?.get(OFFLINE_UNTIL) ?: 0L) > now -> RefreshUi.OFFLINE
        fetchedAt != null && now - fetchedAt < WeatherWidget.JUST_NOW_MS -> RefreshUi.DONE
        else -> RefreshUi.IDLE
    }
}

/**
 * The weather widget's refresh button. It marks the widget as updating and hands the work to
 * [RefreshTapWorker], which Android keeps alive properly (the tap handler itself can be frozen or
 * killed by aggressive battery savers such as One UI's). Within two minutes of the last update a
 * tap does nothing; the button already says "Just now".
 */
class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val at = ForecastCache(context).fetchedAt(id)
        val now = System.currentTimeMillis()
        if (at != null && now - at < WeatherWidget.JUST_NOW_MS) return

        updateAppWidgetState(context, glanceId) {
            it[REFRESH_STARTED] = now
            it.remove(OFFLINE_UNTIL)
        }
        WeatherWidget().redraw(context, glanceId)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "amn-weather-tap-$id",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshTapWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf("id" to id))
                .build(),
        )
        // Safety net: redraw once "Updating…" has expired, whatever happened to the job.
        WeatherSchedule.redrawIn(context, RefreshTiming.BUSY_MAX_MS + 2_000, "amn-weather-unstick-$id")
    }
}

/** Runs a tap-started refresh and always clears "Updating…" afterwards. */
class RefreshTapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt("id", -1)
        val glanceId = runCatching { GlanceAppWidgetManager(applicationContext).getGlanceIdBy(id) }.getOrNull()
            ?: return Result.success()
        var ok = false
        try {
            ok = id in WeatherRefresher.refresh(applicationContext, listOf(id))
        } finally {
            updateAppWidgetState(applicationContext, glanceId) {
                it.remove(REFRESH_STARTED)
                if (!ok) it[OFFLINE_UNTIL] = System.currentTimeMillis() + RefreshTiming.OFFLINE_MS
            }
            WeatherWidget().redraw(applicationContext, glanceId)
            if (!ok) WeatherSchedule.redrawIn(applicationContext, RefreshTiming.OFFLINE_MS + 1_000, "amn-weather-offline-$id")
        }
        return Result.success()
    }
}
