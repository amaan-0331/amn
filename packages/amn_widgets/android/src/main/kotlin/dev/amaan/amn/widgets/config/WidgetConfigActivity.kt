package dev.amaan.amn.widgets.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import dev.amaan.amn.widgets.glance.ClockWidgetReceiver
import io.flutter.embedding.android.FlutterActivity

/**
 * The edit page for both widgets. The launcher opens it when a widget is added;
 * a tap on the widget opens it in edit mode. The UI is the Flutter route
 * /widget/{weather|clock}; results go back through [finishWith].
 */
class WidgetConfigActivity : FlutterActivity() {
    companion object {
        const val EXTRA_KIND = "dev.amaan.amn.widgets.KIND"
        const val EXTRA_MODE = "dev.amaan.amn.widgets.MODE"
    }

    val appWidgetId: Int
        get() = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    val mode: String
        get() = intent.getStringExtra(EXTRA_MODE) ?: "add"

    val kind: String
        get() = intent.getStringExtra(EXTRA_KIND)
            ?: AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
                ?.let { if (it == ClockWidgetReceiver::class.java.name) "clock" else "weather" }
            ?: "weather"

    override fun onCreate(savedInstanceState: Bundle?) {
        // If the page closes any other way, the launcher must not add the widget.
        setResult(RESULT_CANCELED, resultIntent())
        super.onCreate(savedInstanceState)
    }

    override fun getInitialRoute(): String = "/widget/$kind?id=$appWidgetId&mode=$mode"

    fun finishWith(ok: Boolean) {
        setResult(if (ok) RESULT_OK else RESULT_CANCELED, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
