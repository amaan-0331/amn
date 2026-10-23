package dev.amaan.amn.widgets.glance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.LocalState
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import dev.amaan.amn.widgets.R
import dev.amaan.amn.widgets.core.PrayerDesign
import dev.amaan.amn.widgets.core.PrayerMoment
import dev.amaan.amn.widgets.core.PrayerPalette
import dev.amaan.amn.widgets.core.PrayerTimetable
import dev.amaan.amn.widgets.core.Palettes
import dev.amaan.amn.widgets.core.SkyLight
import dev.amaan.amn.widgets.data.PrayerSchedule
import dev.amaan.amn.widgets.data.PrayerStore
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.min

/**
 * Today's prayer times on a 4×1: the next prayer large, the other five in a grid, and a scene
 * behind them (the sky for the time of day, the sun or moon on its arc, a mosque, and the day
 * track). It's drawn as one bitmap and redrawn at each of the six times and every quarter hour
 * (see PrayerSchedule); only the countdown ticks live.
 */
class PrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PrayerSchedule.scheduleNext(context)
        provideContent {
            LocalState.current.renderVersion()
            PrayerContent()
        }
    }

    companion object {
        /** The Flutter route the app opens on, for a tap on the widget. Read by the app's MainActivity. */
        const val EXTRA_ROUTE = "dev.amaan.amn.ROUTE"
    }
}

/** Glance state for the widget lab's preview: a time of day ([minute] after midnight, UAE) and clock format. */
class PrayerPreview(val minute: Int?, val h24: Boolean?)

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PrayerSchedule.cancel(context)
    }
}

@Composable
private fun PrayerContent() {
    val context = LocalContext.current
    val size = LocalSize.current
    val store = PrayerStore(context)
    val zone = store.area().zone
    // The widget lab sets the time of day and the clock format.
    val preview = LocalState.current as? PrayerPreview
    val real = Instant.now()
    val now = preview?.minute?.let { m ->
        PrayerTimetable.uaeDate(real).atTime(m / 60, m % 60).toInstant(PrayerTimetable.UAE_OFFSET)
    } ?: real
    val moment = PrayerMoment.of(store.timetable(), zone, now)
    val pal = Palettes.prayer(moment.sky, context.isNightMode())
    val h24 = preview?.h24 ?: DateFormat.is24HourFormat(context)
    val density = context.resources.displayMetrics.density
    val scene = PrayerArt.render(context, size, density, moment, pal, h24)
    val d = scene.unit / density

    Box(
        GlanceModifier.fillMaxSize()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(openPrayerPage(context)),
    ) {
        Image(ImageProvider(scene.bitmap), describe(moment, h24), GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Box(GlanceModifier.padding(start = (PrayerDesign.PAD * d).dp, top = (PrayerDesign.TOP * d).dp)) {
            Countdown(moment.next.at.toEpochMilli() - now.toEpochMilli(), 15 * d, Palettes.mix(pal.base.ink, pal.base.top, .12))
        }
    }
}

/** "Next · in 2:58:12", counting down by itself; the widget is redrawn when it reaches the prayer. */
@Composable
private fun Countdown(millis: Long, sizeDp: Float, color: Int) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.amn_prayer_countdown).apply {
        setChronometer(R.id.amn_countdown, SystemClock.elapsedRealtime() + millis, "Next · in %s", true)
        setChronometerCountDown(R.id.amn_countdown, true)
        setTextColor(R.id.amn_countdown, color)
        setTextViewTextSize(R.id.amn_countdown, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    }
    AndroidRemoteViews(rv)
}

private fun describe(m: PrayerMoment, h24: Boolean): String =
    "Next: ${m.next.prayer.label} at ${clock(m.next.hhmm, h24).let { (t, a) -> "$t $a".trim() }}. " +
        m.day.filter { it.prayer != m.next.prayer }.joinToString(", ") { "${it.prayer.label} ${clock(it.hhmm, h24).let { (t, a) -> "$t $a".trim() }}" }

/** "17:48" as ("5:48", "PM"), or ("17:48", "") on a 24-hour phone. */
internal fun clock(hhmm: String, h24: Boolean): Pair<String, String> {
    if (h24) return hhmm to ""
    val h = hhmm.substring(0, 2).toInt()
    return "${if (h % 12 == 0) 12 else h % 12}${hhmm.substring(2)}" to if (h < 12) "AM" else "PM"
}

/** Opens the app on its prayer page, with home underneath. */
private fun openPrayerPage(context: Context): Action {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    return actionStartActivity(
        launch.putExtra(PrayerWidget.EXTRA_ROUTE, "/prayer")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
}

/**
 * Draws the widget. Design units ([PrayerDesign]) become pixels at [Scene.unit] per unit, fitted
 * to the height so the text always fits; extra width goes to the sky between the grid and the mosque.
 */
internal object PrayerArt {
    class Scene(val bitmap: Bitmap, val unit: Float)

    private var barlow: Typeface? = null
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun render(context: Context, size: DpSize, density: Float, m: PrayerMoment, pal: PrayerPalette, h24: Boolean): Scene {
        val wPx = (size.width.value * density).toInt().coerceAtLeast(1)
        val hPx = (size.height.value * density).toInt().coerceAtLeast(1)
        val u = min(wPx / PrayerDesign.W, hPx / PrayerDesign.H)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = wPx / u // the widget's width in design units
        c.scale(u, u)
        val p = pal.base
        val ink = p.ink
        val H = hPx / u

        // Sky, then the light that follows the sun or moon.
        val orb = PrayerDesign.orb(m.orb, w).let { (x, y) -> x to y + (H - PrayerDesign.H) }
        val light = SkyLight.of(m.sky).readable(p, PrayerDesign.orb(m.orb, w))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, H, p.top, p.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, H, paint)
        paint.shader = RadialGradient(
            orb.first, orb.second, PrayerDesign.VIGNETTE_R,
            intArrayOf(0, 0, alpha(0xFF000000.toInt(), light.vignette)), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, H, paint)
        paint.shader = RadialGradient(
            orb.first, orb.second, PrayerDesign.LIGHT_R,
            intArrayOf(alpha(light.color, light.alpha), alpha(light.color, light.alpha * .3f), alpha(light.color, 0f)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, H, paint)
        paint.shader = null

        // The mosque is see-through, so the sun or moon is cut out behind it rather than showing through.
        val ground = H - PrayerDesign.FLOOR
        val mosque = Path().apply { addPath(MOSQUE, Matrix().apply { setScale(.7f, .7f); postTranslate(w - 55f, ground) }) }
        // ...and below the horizon, so it rises out of the ground at sunrise and Maghrib.
        c.save()
        c.clipRect(0f, 0f, w, ground)
        c.clipOutPath(mosque)
        if (m.sky.isDay) sun(c, orb.first, orb.second) else moon(c, orb.first, orb.second)
        c.restore()

        paint.color = alpha(ink, .2f)
        c.drawPath(mosque, paint)
        paint.color = alpha(ink, .12f)
        c.drawPath(Path().apply { addPath(DOOR, Matrix().apply { setScale(.7f, .7f); postTranslate(w - 55f, ground) }) }, paint)
        paint.color = alpha(ink, .16f)
        c.drawRect(0f, ground, w, H, paint)
        track(c, m, ink, PrayerDesign.PAD, w - PrayerDesign.PAD, H - PrayerDesign.TRACK_BOTTOM - PrayerDesign.TRACK_H)

        // Text wears a soft halo of the sky behind it, so it stays clear where the sun or moon passes under it.
        val designOrb = PrayerDesign.orb(m.orb, w)
        text(context, c, m, pal, h24) { x, y -> light.skyAt(p, x, y, designOrb) }
        return Scene(bmp, u)
    }

    private fun sun(c: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(x, y, 24f, intArrayOf(0x99FFE38A.toInt(), 0x99FFE38A.toInt(), 0x00FFE38A), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 24f, paint)
        // A lit sphere: the highlight up and to the left.
        paint.shader = RadialGradient(x - 2.9f, y - 3.6f, 16.8f, intArrayOf(0xFFFFF8D6.toInt(), 0xFFFFD24A.toInt(), 0xFFF08A1C.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 12f, paint)
    }

    private fun moon(c: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(x, y, 20f, intArrayOf(0x4DFFF0B8, 0x4DFFF0B8, 0x00FFF0B8), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 20f, paint)
        // A crescent: the full disc with a smaller one cut out of its upper right.
        val layer = c.saveLayer(x - 12f, y - 12f, x + 12f, y + 12f, null)
        paint.shader = RadialGradient(x - 3.3f, y - 4.4f, 17.6f, intArrayOf(0xFFFFF3C4.toInt(), 0xFFFFD36B.toInt(), 0xFFE39A2B.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 11f, paint)
        paint.shader = null
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawCircle(x + 5f, y - 4f, 9f, paint)
        c.restoreToCount(layer)
    }

    /** A mosque standing on the ground at (0, 0), drawn at 70%: a domed hall between two minarets. */
    private val MOSQUE = Path().apply {
        addRect(-38f, -24f, 38f, 0f, Path.Direction.CW)
        moveTo(-26f, -24f)
        cubicTo(-28f, -43f, -10f, -52f, 0f, -60f)
        cubicTo(10f, -52f, 28f, -43f, 26f, -24f)
        close()
        addRect(-1f, -70f, 1f, -60f, Path.Direction.CW)
        addCircle(0f, -72f, 2.4f, Path.Direction.CW)
        for (side in listOf(-1f, 1f)) {
            val mx = side * 53.5f
            addRect(mx - 4.5f, -64f, mx + 4.5f, 0f, Path.Direction.CW)
            moveTo(mx - 6f, -64f); lineTo(mx, -78f); lineTo(mx + 6f, -64f); close()
            addRect(mx - 6.5f, -48f, mx + 6.5f, -45f, Path.Direction.CW)
        }
    }

    private val DOOR = Path().apply {
        moveTo(-7f, 0f); lineTo(-7f, -11f)
        arcTo(RectF(-7f, -18f, 7f, -4f), 180f, 180f)
        lineTo(7f, 0f); close()
    }

    /**
     * The day from midnight to midnight: Fajr to Isha shaded, sunrise to Maghrib brighter, a tick at
     * each of the six times, and a stick for now. The stick is thin so it never hides a tick, even
     * between Maghrib and Isha.
     */
    private fun track(c: Canvas, m: PrayerMoment, ink: Int, x0: Float, x1: Float, top: Float) {
        val h = PrayerDesign.TRACK_H
        val at = { min: Int -> x0 + (x1 - x0) * min / PrayerMoment.DAY.toFloat() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bar = RectF(x0, top, x1, top + h)
        c.save()
        c.clipPath(Path().apply { addRoundRect(bar, h / 2, h / 2, Path.Direction.CW) })
        paint.color = alpha(ink, .16f)
        c.drawRect(bar, paint)
        paint.color = alpha(ink, .18f)
        c.drawRect(at(m.minutes[0]), top, at(m.minutes[5]), top + h, paint)
        paint.color = alpha(ink, .36f)
        c.drawRect(at(m.minutes[1]), top, at(m.minutes[4]), top + h, paint)
        paint.color = alpha(ink, .55f)
        m.minutes.forEach { c.drawRect(at(it) - 1f, top, at(it) + 1f, top + h, paint) }
        c.restore()

        // Now: a stick standing out of the track, with a sky-colored edge so it reads over a tick.
        val x = at(m.now)
        val stick = RectF(x - 2f, top - 5f, x + 2f, top + h + 5f)
        paint.color = alpha(ink, .3f)
        c.drawRoundRect(RectF(stick.left - 1.5f, stick.top - 1.5f, stick.right + 1.5f, stick.bottom + 1.5f), 3.5f, 3.5f, paint)
        paint.color = ink
        c.drawRoundRect(stick, 2f, 2f, paint)
    }

    private fun text(context: Context, c: Canvas, m: PrayerMoment, pal: PrayerPalette, h24: Boolean, sky: (Float, Float) -> Int) {
        val ink = pal.base.ink
        val digits = barlow ?: context.resources.getFont(R.font.amn_barlow_condensed_bold).also { barlow = it }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = digits }
        val label = Paint(Paint.ANTI_ALIAS_FLAG)

        // The countdown's line (18 units) is the live Chronometer laid over this spot.
        var y = PrayerDesign.TOP + 19f
        val (t, ap) = clock(m.next.hhmm, h24)
        time(c, big, t, ap, PrayerDesign.PAD, y, 52f, 19f, ink, sky(PrayerDesign.PAD + 55, y + 26))
        y += 52f + 1f
        label.typeface = sansMedium
        line(c, label, m.next.prayer.label, PrayerDesign.PAD, y, 23f, 24f, pal.accent, sky(PrayerDesign.PAD + 30, y + 12))

        // The other five, in the day's order: three on top, two below. Those already past are faded.
        m.day.filter { it.prayer != m.next.prayer }.forEachIndexed { i, pt ->
            val x = PrayerDesign.GRID_X + (i % 3) * (PrayerDesign.CELL_W + PrayerDesign.CELL_GAP)
            val top = PrayerDesign.TOP + (i / 3) * (PrayerDesign.ROW_H + PrayerDesign.ROW_GAP)
            val past = m.minutes[pt.prayer.ordinal] <= m.now
            val fade = if (past) .5f else 1f
            val (ct, cap) = clock(pt.hhmm, h24)
            val halo = sky(x + PrayerDesign.CELL_W / 2, top + PrayerDesign.ROW_H / 2)
            time(c, big, ct, cap, x, top, 29f, 14f, alpha(ink, fade), halo)
            label.typeface = sans
            line(c, label, pt.prayer.label, x, top + 29f + 1f, 16f, 17f, alpha(ink, .8f * fade), halo)
        }
    }

    /** A time in Barlow with a smaller " AM", in a box [size] tall from [top]. */
    private fun time(c: Canvas, paint: Paint, t: String, ap: String, x: Float, top: Float, size: Float, apSize: Float, color: Int, halo: Int) {
        paint.color = color
        paint.setShadowLayer(size * .16f, 0f, 0f, halo)
        paint.textSize = size
        val base = baseline(paint, top, size)
        c.drawText(t, x, base, paint)
        if (ap.isNotEmpty()) {
            val w = paint.measureText(t)
            paint.textSize = apSize
            c.drawText(" $ap", x + w, base, paint)
        }
    }

    private fun line(c: Canvas, paint: Paint, s: String, x: Float, top: Float, size: Float, height: Float, color: Int, halo: Int) {
        paint.color = color
        paint.setShadowLayer(size * .25f, 0f, 0f, halo)
        paint.textSize = size
        c.drawText(s, x, baseline(paint, top, height), paint)
    }

    /** The baseline that centres a line's ascent and descent in a box [height] tall, as CSS does. */
    private fun baseline(paint: Paint, top: Float, height: Float): Float {
        val fm = paint.fontMetrics
        return top + (height - (fm.descent - fm.ascent)) / 2 - fm.ascent
    }

    private fun alpha(color: Int, a: Float): Int = (ceil(a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0xFFFFFF)
}
