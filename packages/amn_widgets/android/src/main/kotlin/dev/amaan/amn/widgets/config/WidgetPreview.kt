package dev.amaan.amn.widgets.config

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import dev.amaan.amn.widgets.core.Condition
import dev.amaan.amn.widgets.data.ClockPreview
import dev.amaan.amn.widgets.data.ClockSettings
import dev.amaan.amn.widgets.data.WidgetDebug
import dev.amaan.amn.widgets.data.Units
import dev.amaan.amn.widgets.data.WeatherSettings
import dev.amaan.amn.widgets.data.WeatherState
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.WeatherWidget
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val PREVIEW_VIEW_TYPE = "amn/widget-preview"

/** Creates the edit page's preview: the real Glance widget, rendered to RemoteViews. */
internal class PreviewViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    val views = mutableMapOf<Int, PreviewView>()

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val view = PreviewView(context) { views.remove(viewId) }
        views[viewId] = view
        (args as? Map<*, *>)?.let(view::render)
        return view
    }
}

internal class PreviewView(context: Context, private val onDispose: () -> Unit) : PlatformView {
    private val frame = FrameLayout(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun getView(): View = frame

    override fun dispose() {
        scope.cancel()
        onDispose()
    }

    /** args: kind ("weather"|"clock"), width and height in dp, settings (unsaved). */
    fun render(args: Map<*, *>) {
        val kind = args["kind"] as? String ?: "weather"
        val size = DpSize(((args["width"] as? Number)?.toFloat() ?: 140f).dp, ((args["height"] as? Number)?.toFloat() ?: 140f).dp)
        val settings = args["settings"] as? Map<*, *> ?: emptyMap<String, Any>()
        val widgetId = (args["widgetId"] as? Number)?.toInt()
        // Widget lab overrides: {condition: "rain", hour: 21, dark: true}
        val lab = args["debug"] as? Map<*, *>
        val debug = lab?.let {
            WidgetDebug(
                condition = runCatching { Condition.valueOf((it["condition"] as? String ?: "partly").uppercase()) }.getOrDefault(Condition.PARTLY),
                hour = (it["hour"] as? Number)?.toInt() ?: 12,
                h24 = it["h24"] as? Boolean,
            )
        }
        val dark = lab?.get("dark") as? Boolean
        val hostScale = (args["hostScale"] as? Number)?.toFloat() ?: 1f
        job?.cancel()
        job = scope.launch {
            val ctx = if (dark == null) frame.context else frame.context.withNightMode(dark)
            val rv = runCatching {
                if (kind == "clock") {
                    ClockWidget().compose(ctx, size = size, state = ClockPreview(ClockSettings.from(settings), debug, previewWidgetId = widgetId))
                } else {
                    WeatherWidget().compose(ctx, size = size, state = WeatherState(WeatherSettings.from(settings), Units.from(settings), previewWidgetId = widgetId, debug = debug))
                }
            }.onFailure { Log.w("AmnWidgets", "Preview failed", it) }.getOrNull() ?: return@launch
            frame.removeAllViews()
            // Inflate like a launcher does: a restricted context, which ignores @font and similar resources.
            val hostCtx = runCatching { ctx.createPackageContext(ctx.packageName, Context.CONTEXT_RESTRICTED) }.getOrDefault(ctx)
            val hostView = rv.apply(if (dark == null) hostCtx else hostCtx.withNightMode(dark), frame)
            // hostScale < 1 mimics One UI, which lays a widget out at a larger size and shrinks it.
            val density = ctx.resources.displayMetrics.density
            frame.addView(hostView, FrameLayout.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()))
            hostView.pivotX = 0f
            hostView.pivotY = 0f
            hostView.scaleX = hostScale
            hostView.scaleY = hostScale
        }
    }
}

private fun Context.withNightMode(dark: Boolean): Context {
    val config = Configuration(resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    return createConfigurationContext(config)
}
