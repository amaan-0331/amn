package dev.amaan.amn.widgets.data

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.amaan.amn.widgets.glance.ClockWidget
import dev.amaan.amn.widgets.glance.redrawAll
import java.util.concurrent.TimeUnit

/** Each clock's city weather: fetched hourly, cached per clock, drawn from the cache. */
object ClockWeather {
    private const val TAG = "AmnWidgets"
    private const val PERIODIC = "amn-clock-weather"
    private const val NOW = "amn-clock-weather-now"

    /** Older than this, a clock hides its weather instead of showing stale data. */
    const val MAX_AGE_MS = 3 * 60 * 60_000L

    fun cache(context: Context) = ForecastCache(context, "cw")

    suspend fun clockIds(context: Context): List<Int> {
        val gm = GlanceAppWidgetManager(context)
        return gm.getGlanceIds(ClockWidget::class.java).map { gm.getAppWidgetId(it) }
    }

    /** Clocks in the same city share one request. Failures keep the previous cache. */
    suspend fun refresh(context: Context, ids: List<Int>) {
        val store = WidgetStore(context)
        val cache = cache(context)
        val fetched = HashMap<String, Pair<String, Long>>()
        for (id in ids) {
            val s = store.clock(id)
            val lat = s.lat ?: continue
            val lon = s.lon ?: continue
            if (!s.showWeather) continue
            val key = "%.2f,%.2f".format(lat, lon)
            runCatching {
                val (json, at) = fetched[key] ?: run {
                    val now = System.currentTimeMillis()
                    val body = OpenMeteo.fetch(lat, lon)
                    OpenMeteo.parse(body, now) // validate before caching
                    (body to now).also { fetched[key] = it }
                }
                cache.save(id, json, at, s.cityName)
            }.onFailure { Log.w(TAG, "Clock weather refresh failed for $id", it) }
        }
        ClockWidget().redrawAll(context)
    }

    suspend fun refreshAll(context: Context) = refresh(context, clockIds(context))

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensure(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ClockWeatherWorker>(1, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ClockWeatherWorker>().setConstraints(network).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}

class ClockWeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ClockWeather.refreshAll(applicationContext)
        return Result.success()
    }
}
