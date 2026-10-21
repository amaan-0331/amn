package dev.amaan.amn.widgets.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import dev.amaan.amn.widgets.R
import dev.amaan.amn.widgets.config.WidgetConfigActivity
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.Phase
import dev.amaan.amn.widgets.core.WidgetPalette

internal object Art {
    /** Fluent Emoji 3D (MIT). Only Clear and Partly cloudy change at night. */
    fun drawable(c: Condition, p: Phase): Int {
        val night = p == Phase.NIGHT
        return when (c) {
            Condition.CLEAR -> if (night) R.drawable.amn_clear_night else R.drawable.amn_clear_day
            Condition.PARTLY -> if (night) R.drawable.amn_partly_night else R.drawable.amn_partly_day
            Condition.CLOUDY -> R.drawable.amn_cloudy
            Condition.RAIN -> R.drawable.amn_rain
            Condition.STORM -> R.drawable.amn_storm
            Condition.SNOW -> R.drawable.amn_snow
            Condition.FOG -> R.drawable.amn_fog
        }
    }
}

/** Glance has no gradient brush, so the background is a small bitmap stretched to fit. */
internal object Backgrounds {
    private val cache = HashMap<Long, Bitmap>()

    fun gradient(p: WidgetPalette): Bitmap = synchronized(cache) {
        cache.getOrPut((p.top.toLong() shl 32) or (p.bottom.toLong() and 0xFFFFFFFFL)) {
            val size = 64
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply {
                // Roughly 165°: top-left to bottom-right, mostly vertical.
                shader = LinearGradient(0f, 0f, size * .27f, size.toFloat(), p.top, p.bottom, Shader.TileMode.CLAMP)
            }
            Canvas(bmp).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            bmp
        }
    }
}

internal fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Tap action: opens the widget's edit page in edit mode. */
internal fun openEditPage(context: Context, appWidgetId: Int, kind: String): Action = actionStartActivity(
    Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .putExtra(WidgetConfigActivity.EXTRA_KIND, kind)
        .putExtra(WidgetConfigActivity.EXTRA_MODE, "edit")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
)

/**
 * Live time through a TextClock, which ticks by itself.
 * [zone] null means the phone's own time zone.
 */
@Composable
internal fun ClockText(zone: String?, pattern: String, sizeSp: Float, color: Int, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.amn_text_clock).apply {
        setCharSequence(R.id.amn_clock, "setFormat24Hour", pattern)
        setCharSequence(R.id.amn_clock, "setFormat12Hour", pattern)
        if (zone != null) setString(R.id.amn_clock, "setTimeZone", zone)
        setTextColor(R.id.amn_clock, color)
        setTextViewTextSize(R.id.amn_clock, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv, modifier)
}

/** Static text in Barlow Condensed (temperatures). */
@Composable
internal fun DisplayText(text: String, sizeSp: Float, color: Int, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.amn_display_text).apply {
        setTextViewText(R.id.amn_display, text)
        setTextColor(R.id.amn_display, color)
        setTextViewTextSize(R.id.amn_display, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv, modifier)
}

