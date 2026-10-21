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
import dev.amaan.amn.widgets.glance.WeatherWidget
import dev.amaan.amn.widgets.glance.redrawAll
import java.util.concurrent.TimeUnit

/** Fetches and caches the forecast for weather widgets, then redraws them. */
object WeatherRefresher {
    private const val TAG = "AmnWidgets"

    suspend fun weatherWidgetIds(context: Context): List<Int> {
        val gm = GlanceAppWidgetManager(context)
        return gm.getGlanceIds(WeatherWidget::class.java).map { gm.getAppWidgetId(it) }
    }

    /** Returns the ids that got fresh data. Failures keep the previous cache. */
    suspend fun refresh(context: Context, ids: List<Int>): List<Int> {
        val store = WidgetStore(context)
        val cache = ForecastCache(context)
        val location = LocationSource(context)
        var gpsFix: android.location.Location? = null
        var gpsTried = false
        var gpsPlace: String? = null
        val done = mutableListOf<Int>()
        for (id in ids) {
            val s = store.weather(id)
            var lat = s.lat
            var lon = s.lon
            var place: String? = s.cityName
            if (s.useGps && location.hasPermission()) {
                if (!gpsTried) {
                    gpsFix = location.current()
                    gpsTried = true
                }
                val fix = gpsFix
                // No fix (for example no "Allow all the time"): fall back to the pinned city, labelled as such.
                if (fix != null) {
                    lat = fix.latitude
                    lon = fix.longitude
                    place = gpsPlace ?: location.placeName(lat, lon).also { gpsPlace = it }
                }
            }
            runCatching {
                val now = System.currentTimeMillis()
                val json = OpenMeteo.fetch(lat, lon)
                OpenMeteo.parse(json, now) // validate before caching
                cache.save(id, json, now, place)
                done += id
            }.onFailure { Log.w(TAG, "Weather refresh failed for $id", it) }
        }
        WeatherWidget().redrawAll(context)
        return done
    }

    suspend fun refreshAll(context: Context) = refresh(context, weatherWidgetIds(context))
}

class WeatherRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WeatherRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}

/** About every 30 minutes while at least one weather widget exists. */
object WeatherSchedule {
    private const val PERIODIC = "amn-weather"
    private const val NOW = "amn-weather-now"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensure(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
        )
    }

    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeatherRefreshWorker>().setConstraints(network).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
    }
}
