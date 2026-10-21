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
import androidx.compose.ui.unit.dp
import androidx.glance.LocalContext
import androidx.glance.layout.height
import androidx.glance.layout.width
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

/**
 * Art that runs off the widget's top-right corner, as in the design. Glance can't place an
 * image past a widget's bounds, so the visible part is cropped here: [bleedRight] and
 * [bleedTop] are how many dp of a [size] dp image fall outside.
 */
internal object Bleed {
    private val cache = object : android.util.LruCache<String, Bitmap>(24) {}

    fun crop(context: Context, resId: Int, size: Float, bleedRight: Float, bleedTop: Float): Bitmap {
        val key = "$resId|$size|$bleedRight|$bleedTop"
        cache.get(key)?.let { return it }
        val full = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
        val x1 = (full.width * (1 - bleedRight / size)).toInt().coerceIn(1, full.width)
        val y0 = (full.height * (bleedTop / size)).toInt().coerceIn(0, full.height - 1)
        return Bitmap.createBitmap(full, 0, y0, x1, full.height - y0).also { cache.put(key, it) }
    }
}

/** A [size] dp art image whose right and top edges may run off the widget. */
@Composable
internal fun BleedArt(resId: Int, size: Float, bleedRight: Float = 0f, bleedTop: Float = 0f, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val bmp = Bleed.crop(context, resId, size, bleedRight, bleedTop)
    androidx.glance.Image(
        provider = androidx.glance.ImageProvider(bmp),
        contentDescription = null,
        modifier = modifier.width((size - bleedRight).dp).height((size - bleedTop).dp),
        contentScale = androidx.glance.layout.ContentScale.Fit,
    )
}

/**
 * The top part of an art image, for a sun sitting on the bottom edge (dawn and dusk).
 * Glance can't place an image past a widget's bounds, so the crop is made here.
 */
internal object Horizon {
    private val cache = HashMap<Int, Bitmap>()

    fun topHalf(context: Context, resId: Int, fraction: Float = .55f): Bitmap = synchronized(cache) {
        cache.getOrPut(resId) {
            val full = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
            Bitmap.createBitmap(full, 0, 0, full.width, (full.height * fraction).toInt())
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
internal fun ClockText(zone: String?, pattern: String, size: Float, color: Int, modifier: GlanceModifier = GlanceModifier, unit: Int = TypedValue.COMPLEX_UNIT_SP) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.amn_text_clock).apply {
        setCharSequence(R.id.amn_clock, "setFormat24Hour", pattern)
        setCharSequence(R.id.amn_clock, "setFormat12Hour", pattern)
        if (zone != null) setString(R.id.amn_clock, "setTimeZone", zone)
        setTextColor(R.id.amn_clock, color)
        setTextViewTextSize(R.id.amn_clock, unit, size)
    }
    AndroidRemoteViews(rv, modifier)
}

/**
 * Numbers in Barlow Condensed, drawn to a bitmap cropped to the glyphs.
 * Launchers inflate widgets with a restricted context that ignores @font resources,
 * so a TextView can't show the bundled font on the home screen.
 */
internal object Numerals {
    private val cache = object : android.util.LruCache<String, Bitmap>(48) {}
    private var typeface: android.graphics.Typeface? = null

    /** [sizeDp] is in dp: numbers are part of the layout, so they don't follow the font-size setting. */
    fun render(context: Context, text: String, sizeDp: Float, color: Int): Bitmap {
        val key = "$text|$sizeDp|$color"
        cache.get(key)?.let { return it }
        val tf = typeface ?: context.resources.getFont(R.font.amn_barlow_condensed_bold).also { typeface = it }
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp, context.resources.displayMetrics)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = tf
            textSize = px
            this.color = color
        }
        // Height from the digits' ink (so every number lines up), stretched to fit the degree sign
        // and minus sign when they reach further; width from the text's advance.
        val ink = android.graphics.Rect().also { paint.getTextBounds("0123456789", 0, 10, it) }
        val own = android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val top = minOf(ink.top, own.top)
        val bottom = maxOf(ink.bottom, own.bottom)
        val pad = (px * .06f).toInt().coerceAtLeast(2)
        val width = maxOf(paint.measureText(text).toInt(), own.right) + pad * 2
        val height = bottom - top + pad * 2
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), (pad - top).toFloat(), paint)
        cache.put(key, bmp)
        return bmp
    }
}

/** A number in Barlow Condensed at [sizeDp]; its box is exactly the digit height. */
@Composable
internal fun Numeral(text: String, sizeDp: Float, color: Int, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val bmp = Numerals.render(context, text, sizeDp, color)
    val density = context.resources.displayMetrics.density
    androidx.glance.Image(
        provider = androidx.glance.ImageProvider(bmp),
        contentDescription = text,
        modifier = modifier.width((bmp.width / density).dp).height((bmp.height / density).dp),
        contentScale = androidx.glance.layout.ContentScale.Fit,
    )
}

/**
 * How a widget fits its space. Launchers report sizes differently (One UI reports a larger
 * size and scales the result down), so layouts are picked by shape and width rather than by
 * fixed breakpoints, and the design's measurements are scaled by [k] to fill the space.
 */
internal data class Fit(val k: Float) {
    fun dp(v: Float) = (v * k).dp
    fun sp(v: Float) = v * k
}

internal fun fitFor(size: androidx.compose.ui.unit.DpSize, designWidth: Float, designHeight: Float, maxK: Float = 1.6f) =
    Fit(minOf(size.width.value / designWidth, size.height.value / designHeight).coerceIn(.8f, maxK))
