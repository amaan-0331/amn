package dev.amaan.amn.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.amaan.amn.widgets.config.PREVIEW_VIEW_TYPE
import dev.amaan.amn.widgets.config.PreviewViewFactory
import dev.amaan.amn.widgets.config.WidgetConfigActivity
import dev.amaan.amn.widgets.core.CityOffset
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.Forecast
import dev.amaan.amn.widgets.core.ForecastBuilder
import dev.amaan.amn.widgets.core.RawForecast
import dev.amaan.amn.widgets.core.Summary
import dev.amaan.amn.widgets.data.SampleForecast
import dev.amaan.amn.widgets.data.ClockSchedule
import dev.amaan.amn.widgets.data.PrayerSchedule
import dev.amaan.amn.widgets.data.PrayerStore
import dev.amaan.amn.widgets.data.Awqaf
import dev.amaan.amn.widgets.data.ClockWeather
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.WeatherRefresher
import dev.amaan.amn.widgets.data.WeatherSchedule
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.data.WeatherSettings
import dev.amaan.amn.widgets.data.WidgetStore
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.ClockWidgetReceiver
import dev.amaan.amn.widgets.glance.WeatherWidget
import dev.amaan.amn.widgets.glance.WeatherWidgetReceiver
import dev.amaan.amn.widgets.glance.redraw
import dev.amaan.amn.widgets.glance.redrawAll
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The amn/widgets channel and the preview platform view. See the spec's Architecture section. */
class AmnWidgetsPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context
    private val previews = PreviewViewFactory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activity: Activity? = null

    private val configActivity get() = activity as? WidgetConfigActivity

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "amn/widgets")
        channel.setMethodCallHandler(this)
        binding.platformViewRegistry.registerViewFactory(PREVIEW_VIEW_TYPE, previews)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val store = WidgetStore(appContext)
        when (call.method) {
            "launchArgs" -> result.success(configActivity?.let { mapOf("id" to it.appWidgetId, "kind" to it.kind, "mode" to it.mode) })

            "getSettings" -> {
                val id = call.argument<Int>("id") ?: return result.error("args", "id missing", null)
                val settings = if (call.argument<String>("kind") == "clock") {
                    store.clock(id).toMap() + mapOf("fetchedAt" to ClockWeather.cache(appContext).fetchedAt(id))
                } else {
                    val cache = ForecastCache(appContext)
                    store.weather(id).toMap() + mapOf("fetchedAt" to cache.fetchedAt(id), "placeName" to cache.placeName(id))
                }
                result.success(settings + store.units().toMap())
            }

            "previewChanged" -> {
                val viewId = call.argument<Int>("viewId")
                (call.arguments as? Map<*, *>)?.let { args -> previews.views[viewId]?.render(args) }
                result.success(null)
            }

            "save" -> {
                val id = call.argument<Int>("id") ?: return result.error("args", "id missing", null)
                val kind = call.argument<String>("kind")
                val settings = call.argument<Map<String, Any?>>("settings") ?: emptyMap()
                scope.launch {
                    val glanceId = runCatching { GlanceAppWidgetManager(appContext).getGlanceIdBy(id) }.getOrNull()
                    if (kind == "clock") {
                        val clock = ClockSettings.from(settings, store.clock(id))
                        store.saveClock(id, clock)
                        glanceId?.let { ClockWidget().redraw(appContext, it) }
                        if (clock.timeZone != null) ClockSchedule.scheduleNext(appContext, id, clock)
                        if (clock.showWeather) {
                            ClockWeather.ensure(appContext)
                            ClockWeather.now(appContext)
                        }
                    } else {
                        store.saveWeather(id, WeatherSettings.from(settings, store.weather(id)))
                        store.saveUnits(Units.from(settings, store.units()))
                        // Units are app-wide, so every weather widget changes.
                        WeatherWidget().redrawAll(appContext)
                        WeatherSchedule.ensure(appContext)
                        WeatherSchedule.now(appContext)
                    }
                    result.success(null)
                    configActivity?.finishWith(true)
                }
            }

            // The app's home: every placed widget, with what it currently shows.
            "listWidgets" -> {
                val mgr = AppWidgetManager.getInstance(appContext)
                fun ids(receiver: Class<*>) = mgr.getAppWidgetIds(ComponentName(appContext, receiver)).sorted()
                val now = Instant.now()
                val forecasts = ForecastCache(appContext)
                // Night where the widget is, not where the phone is.
                fun night(raw: RawForecast, f: Forecast?): Boolean? {
                    f ?: return null
                    val t = ForecastBuilder.localNow(raw, now).toLocalTime()
                    val min = t.hour * 60 + t.minute
                    return min < f.sunriseMin || min >= f.sunsetMin
                }
                val weather = ids(WeatherWidgetReceiver::class.java).map { id ->
                    val s = store.weather(id)
                    val cached = forecasts.load(id)
                    val live = cached?.let { ForecastBuilder.build(it.raw, now) }
                    mapOf(
                        "id" to id, "kind" to "weather",
                        "place" to (forecasts.placeName(id) ?: if (s.useGps) null else s.cityName),
                        "gps" to s.useGps,
                        "tempC" to live?.nowTempC, "condition" to live?.condition?.name?.lowercase(),
                        "night" to cached?.let { night(it.raw, live) },
                        "fetchedAt" to forecasts.fetchedAt(id),
                    )
                }
                val clockCache = ClockWeather.cache(appContext)
                val clocks = ids(ClockWidgetReceiver::class.java).map { id ->
                    val s = store.clock(id)
                    val live = if (!s.showWeather) null else clockCache.load(id)?.let { ForecastBuilder.build(it.raw, now) }
                        ?.takeIf { System.currentTimeMillis() - it.fetchedAtEpochMs < ClockWeather.MAX_AGE_MS }
                    mapOf(
                        "id" to id, "kind" to "clock",
                        "place" to s.cityName, "timeZone" to s.timeZone,
                        "tempC" to live?.nowTempC, "condition" to live?.condition?.name?.lowercase(),
                    )
                }
                result.success(mapOf("widgets" to weather + clocks, "fahrenheit" to store.units().fahrenheit))
            }

            // Opens a placed widget's edit page on top of the app; back returns here.
            "openEditor" -> {
                val id = call.argument<Int>("id") ?: return result.error("args", "id missing", null)
                val host = activity ?: return result.error("activity", "no activity", null)
                host.startActivity(
                    Intent(host, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(WidgetConfigActivity.EXTRA_KIND, call.argument<String>("kind") ?: "weather")
                        .putExtra(WidgetConfigActivity.EXTRA_MODE, "edit"),
                )
                result.success(null)
            }

            // Prayer times: the app's page reads and writes them here; widgets read PrayerStore directly.
            "prayerLoad" -> scope.launch {
                val prayer = PrayerStore(appContext)
                val data = withContext(Dispatchers.IO) {
                    mapOf(
                        "bundle" to prayer.bundleJson(),
                        "refreshed" to prayer.refreshed().mapKeys { it.key.toString() },
                        "area" to prayer.areaId(),
                        "refreshedAt" to prayer.refreshedAt(),
                    )
                }
                result.success(data)
            }

            "prayerSetArea" -> {
                val area = call.argument<Int>("area") ?: return result.error("args", "area missing", null)
                PrayerStore(appContext).saveArea(area)
                result.success(null)
                scope.launch { PrayerSchedule.redraw(appContext) }
            }

            "prayerRefresh" -> scope.launch {
                val outcome = Awqaf.refresh(appContext)
                val prayer = PrayerStore(appContext)
                result.success(
                    outcome.toMap() + mapOf(
                        "refreshed" to withContext(Dispatchers.IO) { prayer.refreshed().mapKeys { it.key.toString() } },
                        "refreshedAt" to prayer.refreshedAt(),
                    ),
                )
                if (outcome.changed > 0) PrayerSchedule.redraw(appContext)
            }

            "cancel" -> {
                result.success(null)
                configActivity?.finishWith(false)
            }

            "refreshNow" -> {
                val id = call.argument<Int>("id") ?: return result.error("args", "id missing", null)
                if (call.argument<String>("kind") == "clock") {
                    // The clock's saved city; unsaved changes aren't fetched.
                    scope.launch {
                        ClockWeather.refresh(appContext, listOf(id))
                        val cache = ClockWeather.cache(appContext)
                        result.success(mapOf("fetchedAt" to cache.fetchedAt(id), "placeName" to cache.placeName(id)))
                    }
                    return
                }
                scope.launch {
                    WeatherRefresher.refresh(appContext, listOf(id))
                    val cache = ForecastCache(appContext)
                    result.success(mapOf("fetchedAt" to cache.fetchedAt(id), "placeName" to cache.placeName(id)))
                }
            }

            // Current time, offset from the phone and GMT label for each IANA zone (clock search results).
            "describeZones" -> {
                val zones = call.argument<List<String>>("zones").orEmpty()
                val h24 = call.argument<Boolean>("h24") ?: true
                val fmt = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.ENGLISH)
                val now = Instant.now()
                val phone = ZoneId.systemDefault()
                result.success(zones.associateWith { z ->
                    val zone = runCatching { ZoneId.of(z) }.getOrNull() ?: return@associateWith null
                    val off = zone.rules.getOffset(now).totalSeconds / 60
                    val gmt = "GMT${if (off >= 0) "+" else "−"}${kotlin.math.abs(off) / 60}${if (off % 60 != 0) ":%02d".format(kotlin.math.abs(off) % 60) else ""}"
                    mapOf("time" to now.atZone(zone).format(fmt), "offset" to CityOffset.between(zone, phone, now).label, "gmt" to gmt)
                })
            }

            // Widget lab: how the summary line is picked for a sample condition, per widget size.
            "explainSummary" -> {
                val condition = runCatching { Condition.valueOf((call.argument<String>("condition") ?: "partly").uppercase()) }.getOrDefault(Condition.PARTLY)
                val hour = call.argument<Int>("hour") ?: 12
                val h24 = call.argument<Boolean>("h24") ?: true
                val seed = (call.argument<Number>("seed"))?.toLong() ?: 0L
                val units = Units(fahrenheit = call.argument<Boolean>("fahrenheit") ?: false)
                val f = SampleForecast.of(condition, hour)
                val deg = { c: Double -> "${units.temp(c)}°" }
                val sizes = listOf("2×1" to 17, "2×2" to 16, "4×2" to 22, "4×3 / 4×4" to 24)
                val detail = Summary.explain(f, h24, deg, 24, seed)
                result.success(mapOf(
                    "seed" to seed,
                    "winner" to detail.winner,
                    "text" to detail.line.text,
                    "candidateIndex" to detail.candidateIndex,
                    "candidateCount" to detail.candidateCount,
                    "variantIndex" to detail.variantIndex,
                    "variantCount" to detail.variantCount,
                    "rules" to detail.rules.map {
                        mapOf("id" to it.id, "urgent" to it.urgent, "description" to it.description, "variants" to it.variants, "fitting" to it.fitting)
                    },
                    "sizes" to sizes.map { (label, max) ->
                        mapOf("label" to label, "maxLen" to max, "text" to Summary.of(f, h24, deg, max, seed).text)
                    },
                ))
            }

            else -> result.notImplemented()
        }
    }
}
