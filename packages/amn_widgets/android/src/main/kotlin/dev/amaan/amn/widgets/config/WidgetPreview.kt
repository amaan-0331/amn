package dev.amaan.amn.widgets.config

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import dev.amaan.amn.widgets.data.ClockSettings
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
        job?.cancel()
        job = scope.launch {
            val ctx = frame.context
            val rv = runCatching {
                if (kind == "clock") {
                    ClockWidget().compose(ctx, size = size, state = ClockSettings.from(settings))
                } else {
                    WeatherWidget().compose(ctx, size = size, state = WeatherState(WeatherSettings.from(settings), Units.from(settings), previewWidgetId = widgetId))
                }
            }.onFailure { Log.w("AmnWidgets", "Preview failed", it) }.getOrNull() ?: return@launch
            frame.removeAllViews()
            frame.addView(rv.apply(ctx, frame), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}
