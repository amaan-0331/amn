package dev.amaan.amn.widgets.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Approximate location from the platform (no Play Services), and its city name. */
class LocationSource(context: Context) {
    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val provider = if (lm.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.NETWORK_PROVIDER
        val fresh = runCatching {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, appContext.mainExecutor) { cont.resume(it) }
                }
            }
        }.getOrNull()
        return fresh ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }

    /** City name for a point, or null when the phone has no geocoder or finds nothing. */
    suspend fun placeName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                Geocoder(appContext, Locale.ENGLISH).getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        val a = addresses.firstOrNull()
                        cont.resume(a?.locality ?: a?.subAdminArea ?: a?.adminArea)
                    }

                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        }
    }
}
