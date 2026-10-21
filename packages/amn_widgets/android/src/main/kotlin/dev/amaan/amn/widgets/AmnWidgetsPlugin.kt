package dev.amaan.amn.widgets

import android.app.Activity
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.amaan.amn.widgets.config.PREVIEW_VIEW_TYPE
import dev.amaan.amn.widgets.config.PreviewViewFactory
import dev.amaan.amn.widgets.config.WidgetConfigActivity
import dev.amaan.amn.widgets.core.CityOffset
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.core.Summary
import dev.amaan.amn.widgets.data.SampleForecast
import dev.amaan.amn.widgets.data.ClockSchedule
import dev.amaan.amn.widgets.data.ClockWeather
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.WeatherRefresher
import dev.amaan.amn.widgets.data.WeatherSchedule
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.data.WeatherSettings
import dev.amaan.amn.widgets.data.WidgetStore
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.WeatherWidget
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

            "cancel" -> {
                result.success(null)
                configActivity?.finishWith(false)
            }

            "refreshNow" -> {
                val id = call.argument<Int>("id") ?: return result.error("args", "id missing", null)
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
                val sizes = listOf("2×1" to 17, "2×2" to 19, "4×2" to 22, "4×3 / 4×4" to 24)
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
