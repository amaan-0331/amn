package dev.amaan.amn.widgets.glance

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Glance keeps a widget's composition alive for a while and only recomposes when the
 * widget's own Glance state changes. Settings and forecasts live outside that state, so
 * every redraw bumps this key; the widgets read it, which makes them recompose and re-read
 * their data.
 */
internal val RENDER_VERSION = longPreferencesKey("amn_render_version")

/** Read inside a widget's content to subscribe to redraws. */
internal fun Any?.renderVersion(): Long? = (this as? Preferences)?.get(RENDER_VERSION)

suspend fun GlanceAppWidget.redraw(context: Context, id: GlanceId) {
    updateAppWidgetState(context, id) { it[RENDER_VERSION] = System.nanoTime() }
    update(context, id)
}

suspend fun GlanceAppWidget.redrawAll(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(this::class.java).forEach { redraw(context, it) }
}
