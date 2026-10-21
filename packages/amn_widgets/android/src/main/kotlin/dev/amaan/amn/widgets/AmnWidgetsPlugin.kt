package dev.amaan.amn.widgets

import android.app.Activity
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.amaan.amn.widgets.config.PREVIEW_VIEW_TYPE
import dev.amaan.amn.widgets.config.PreviewViewFactory
import dev.amaan.amn.widgets.config.WidgetConfigActivity
import dev.amaan.amn.widgets.data.ClockSettings
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
                val settings = if (call.argument<String>("kind") == "clock") store.clock(id).toMap() else store.weather(id).toMap()
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
                    }
                    result.success(null)
                    configActivity?.finishWith(true)
                }
            }

            "cancel" -> {
                result.success(null)
                configActivity?.finishWith(false)
            }

            // TODO: run the Open-Meteo refresh job once and return the new fetch time.
            "refreshNow" -> result.success(System.currentTimeMillis())

            else -> result.notImplemented()
        }
    }
}
