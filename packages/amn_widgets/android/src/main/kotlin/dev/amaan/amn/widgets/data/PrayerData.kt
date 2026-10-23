package dev.amaan.amn.widgets.data

import android.content.Context
import android.util.Log
import dev.amaan.amn.widgets.core.PrayerArea
import dev.amaan.amn.widgets.core.PrayerTimetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** The bundled timetable: emirates, areas, zone names and each zone's 366-day string. */
class PrayerBundle(
    val emirates: Map<Int, String>,
    val areas: List<PrayerArea>,
    val zoneNames: Map<Int, String>,
    val zones: Map<Int, String>,
    /** When tool/prayer/fetch.py fetched it, "2026-10-24". */
    val fetched: String,
) {
    private val byId = areas.associateBy { it.id }

    fun area(id: Int): PrayerArea? = byId[id]

    companion object {
        fun parse(json: String): PrayerBundle {
            val o = JSONObject(json)
            val zones = o.getJSONObject("zones")
            val emirates = o.getJSONObject("emirates")
            val areas = o.getJSONArray("areas")
            return PrayerBundle(
                emirates = emirates.keys().asSequence().associate { it.toInt() to emirates.getString(it) },
                areas = (0 until areas.length()).map { i ->
                    val a = areas.getJSONArray(i)
                    PrayerArea(a.getInt(0), a.getInt(1), a.getInt(2), a.getString(3), a.getString(4))
                },
                zoneNames = zones.keys().asSequence().associate { it.toInt() to zones.getJSONObject(it).getString("name") },
                zones = zones.keys().asSequence().associate { it.toInt() to zones.getJSONObject(it).getString("times") },
                fetched = o.getString("fetched"),
            )
        }
    }
}

/**
 * Prayer data for the app and the widgets. Kotlin owns it; Flutter reads and writes only
 * through the amn/widgets channel. The bundled timetable is an asset of this package
 * (rebuilt by tool/prayer/fetch.py); a refresh from Awqaf saves its zones to a file that
 * takes their place; the chosen area is shared by the app's prayer page and every widget.
 */
class PrayerStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("amn_prayer", Context.MODE_PRIVATE)
    private val zonesFile = File(appContext.filesDir, "prayer/zones.json")

    /** The bundled timetable as JSON, for the app to parse itself. */
    fun bundleJson(): String = bundleJson ?: appContext.assets.open(ASSET).bufferedReader().use { it.readText() }.also { bundleJson = it }

    fun bundle(): PrayerBundle = bundle ?: PrayerBundle.parse(bundleJson()).also { bundle = it }

    /** Zones from the last refresh, which replace the bundled ones. */
    fun refreshed(): Map<Int, String> {
        if (!zonesFile.exists()) return emptyMap()
        return runCatching {
            val o = JSONObject(zonesFile.readText())
            o.keys().asSequence().associate { it.toInt() to o.getString(it) }.filterValues(PrayerTimetable::isValid)
        }.onFailure { Log.w("AmnWidgets", "Bad refreshed prayer zones", it) }.getOrDefault(emptyMap())
    }

    /** Bundled zones with any refreshed ones on top. */
    fun timetable(): PrayerTimetable {
        val zones = bundle().zones
        return PrayerTimetable(zones + refreshed().filterKeys(zones::containsKey))
    }

    /** Epoch ms of the last successful refresh; null while on the bundled table. */
    fun refreshedAt(): Long? = prefs.getLong("refreshedAt", 0).takeIf { it > 0 }

    /** The chosen area's id, without parsing the bundle. */
    fun areaId(): Int = prefs.getInt("area", DEFAULT_AREA)

    /** The chosen area; Al Karama, Dubai until one is picked. */
    fun area(): PrayerArea = bundle().let { b -> b.area(areaId()) ?: b.area(DEFAULT_AREA)!! }

    fun saveArea(id: Int) = prefs.edit().putInt("area", id).apply()

    /** Replaces the refreshed zones, all at once. */
    fun saveRefreshed(zones: Map<Int, String>, at: Long) {
        zonesFile.parentFile?.mkdirs()
        val tmp = File(zonesFile.path + ".tmp")
        tmp.writeText(JSONObject(zones.mapKeys { it.key.toString() }).toString())
        tmp.renameTo(zonesFile)
        prefs.edit().putLong("refreshedAt", at).apply()
    }

    companion object {
        const val ASSET = "prayer/uae.json"
        const val DEFAULT_AREA = 50326

        // The bundle never changes while the app runs; parse it once per process.
        @Volatile private var bundleJson: String? = null
        @Volatile private var bundle: PrayerBundle? = null
    }
}

/** Why a refresh failed, in words for the page. */
class AwqafException(message: String) : Exception(message)

/** What a refresh did. */
data class PrayerRefreshOutcome(val checked: Int, val changed: Int, val error: String?) {
    fun toMap(): Map<String, Any?> = mapOf("checked" to checked, "changed" to changed, "error" to error)
}

/** Awqaf's prayer-time API, the one awqaf.gov.ae/prayer-times calls. Keep in step with tool/prayer/fetch.py. */
object Awqaf {
    private const val URL = "https://internationalprayertime.awqaf.gov.ae/api/v1/prayertimes"

    /** The public key the website itself sends. If Awqaf rotates it, refresh fails clearly and the bundled table stays. */
    private const val KEY = "ak_1148a6086913233acc33709fc9aa6e93"
    private val FIELDS = listOf("fajer", "shuroq", "dhohur", "aser", "maghreb", "isha")

    /** One leap year of [zone]'s official times, encoded like the bundled asset. */
    suspend fun fetchZone(zone: Int): String = withContext(Dispatchers.IO) {
        val conn = runCatching { java.net.URL(URL).openConnection() as java.net.HttpURLConnection }
            .getOrElse { throw AwqafException("Couldn't reach Awqaf. Check your connection.") }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Language", "en")
            conn.setRequestProperty("x-api-key", KEY)
            val code = try {
                conn.outputStream.use { it.write("""{"start_date":"2028-01-01","end_date":"2028-12-31","official_id":$zone}""".toByteArray()) }
                conn.responseCode
            } catch (e: java.io.IOException) {
                throw AwqafException("Couldn't reach Awqaf. Check your connection.")
            }
            if (code == 401 || code == 403) throw AwqafException("Awqaf no longer accepts AMN's access key. An app update is needed to refresh.")
            if (code != 200) throw AwqafException("Awqaf answered with an error ($code).")
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Encodes a 366-day response; throws if it isn't the shape the app knows. */
    fun parse(body: String): String {
        val times = runCatching {
            val result = JSONObject(body).getJSONObject("result")
            val days = result.getJSONArray("prayer_times")
            val official = result.getJSONObject("meta_data").getJSONObject("method").optBoolean("official")
            val s = buildString {
                for (i in 0 until days.length()) {
                    val d = days.getJSONObject(i)
                    FIELDS.forEach { append(d.getString(it).replace(":", "")) }
                }
            }
            s.takeIf { official && days.length() == 366 && PrayerTimetable.isValid(it) }
        }.getOrNull()
        return times ?: throw AwqafException("Awqaf's data has changed format. An app update is needed to refresh.")
    }

    /**
     * Fetches every zone again, a few at a time. Saves only if all of them come back valid;
     * otherwise the current table stays as it is.
     */
    suspend fun refresh(context: Context): PrayerRefreshOutcome = coroutineScope {
        val store = PrayerStore(context)
        val current = store.timetable().zones
        val gate = Semaphore(4)
        val results = current.keys.map { zone ->
            async { gate.withPermit { zone to runCatching { fetchZone(zone) } } }
        }.awaitAll()
        val error = results.firstNotNullOfOrNull { (_, r) -> r.exceptionOrNull() }
            ?.let { (it as? AwqafException)?.message ?: "Couldn't refresh from Awqaf." }
        val fresh = results.mapNotNull { (zone, r) -> r.getOrNull()?.let { zone to it } }.toMap()
        if (error != null) return@coroutineScope PrayerRefreshOutcome(fresh.size, 0, error)
        val changed = fresh.count { (zone, times) -> current[zone] != times }
        withContext(Dispatchers.IO) { store.saveRefreshed(fresh, System.currentTimeMillis()) }
        PrayerRefreshOutcome(fresh.size, changed, null)
    }
}
