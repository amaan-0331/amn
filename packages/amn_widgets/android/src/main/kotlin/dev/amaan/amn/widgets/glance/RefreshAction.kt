package dev.amaan.amn.widgets.glance

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.WeatherRefresher
import kotlinx.coroutines.delay

internal val REFRESHING = booleanPreferencesKey("amn_refreshing")
internal val OFFLINE = booleanPreferencesKey("amn_offline")

/** What the weather widget's refresh button shows. */
enum class RefreshUi { IDLE, BUSY, DONE, OFFLINE }

/** BUSY and OFFLINE come from the widget's Glance state; DONE means updated in the last two minutes. */
internal fun refreshUi(glanceState: Any?, fetchedAt: Long?): RefreshUi {
    val prefs = glanceState as? Preferences
    return when {
        prefs?.get(REFRESHING) == true -> RefreshUi.BUSY
        prefs?.get(OFFLINE) == true -> RefreshUi.OFFLINE
        fetchedAt != null && System.currentTimeMillis() - fetchedAt < WeatherWidget.JUST_NOW_MS -> RefreshUi.DONE
        else -> RefreshUi.IDLE
    }
}

/**
 * The weather widget's refresh button: fetches this widget's weather without opening the app.
 * Within two minutes of the last update a tap does nothing; the button already says "Just now".
 */
class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widget = WeatherWidget()
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val at = ForecastCache(context).fetchedAt(id)
        if (at != null && System.currentTimeMillis() - at < WeatherWidget.JUST_NOW_MS) return

        updateAppWidgetState(context, glanceId) {
            it[REFRESHING] = true
            it.remove(OFFLINE)
        }
        widget.redraw(context, glanceId)
        val ok = id in WeatherRefresher.refresh(context, listOf(id))
        updateAppWidgetState(context, glanceId) {
            it[REFRESHING] = false
            if (!ok) it[OFFLINE] = true
        }
        widget.redraw(context, glanceId)
        if (!ok) {
            // Show "Offline" briefly, then go back to the last update time.
            delay(4_000)
            updateAppWidgetState(context, glanceId) { it.remove(OFFLINE) }
            widget.redraw(context, glanceId)
        }
    }
}
