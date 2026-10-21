package dev.amaan.amn.widgets

import android.app.Activity
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.amaan.amn.widgets.config.PREVIEW_VIEW_TYPE
import dev.amaan.amn.widgets.config.PreviewViewFactory
import dev.amaan.amn.widgets.config.WidgetConfigActivity
import dev.amaan.amn.widgets.core.CityOffset
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.ForecastCache
import dev.amaan.amn.widgets.data.WeatherRefresher
import dev.amaan.amn.widgets.data.WeatherSchedule
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.data.WeatherSettings
import dev.amaan.amn.widgets.data.WidgetStore
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.WeatherWidget
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
                    store.clock(id).toMap()
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
                        store.saveClock(id, ClockSettings.from(settings, store.clock(id)))
                        glanceId?.let { ClockWidget().update(appContext, it) }
                    } else {
                        store.saveWeather(id, WeatherSettings.from(settings, store.weather(id)))
                        store.saveUnits(Units.from(settings, store.units()))
                        // Units are app-wide, so every weather widget changes.
                        WeatherWidget().updateAll(appContext)
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

            else -> result.notImplemented()
        }
    }
}
