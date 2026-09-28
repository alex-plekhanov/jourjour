package app.jourjour.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** How an attempt to find the current position ended. */
sealed interface LocationResult {
    data class Success(val coordinates: Coordinates) : LocationResult

    /** No permission. Asking for it is a screen's job — a model cannot show a dialog. */
    data object PermissionDenied : LocationResult

    /** Location is switched off system-wide, or there are no providers at all. */
    data object Unavailable : LocationResult

    /** No fix arrived in time, which indoors is the normal outcome. */
    data object Timeout : LocationResult
}

@Singleton
class LocationSource internal constructor(
    private val context: Context,
    /**
     * How stale a known position may be and still be good enough to tag a receipt.
     * A parameter rather than a constant so that tests can widen it: an emulator's
     * known position is often hours old.
     */
    private val maxAgeNanos: Long,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, DEFAULT_MAX_AGE_NANOS)

    fun hasPermission(): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied

        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: return LocationResult.Unavailable
        val provider = availableProvider(locationManager) ?: return LocationResult.Unavailable

        val location = withTimeoutOrNull(TIMEOUT_MS) {
            requestLocation(locationManager, provider)
        }
            // A fresh fix may never arrive indoors. A recent known position tags a receipt
            // just as well — in a couple of minutes nobody leaves the block. Anything older
            // is refused: it could be from another city.
            ?: lastKnownLocation(locationManager)
            ?: return LocationResult.Timeout

        return LocationResult.Success(Coordinates(location.first, location.second))
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(locationManager: LocationManager): Pair<Double, Double>? {
        val now = SystemClock.elapsedRealtimeNanos()
        return allProviders()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { now - it.elapsedRealtimeNanos <= maxAgeNanos }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { it.latitude to it.longitude }
    }

    @Suppress("MissingPermission")
    private suspend fun requestLocation(
        locationManager: LocationManager,
        provider: String,
    ): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }

        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            signal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (continuation.isActive) {
                continuation.resume(location?.let { it.latitude to it.longitude })
            }
        }
    }

    /**
     * Satellites are the most precise but say nothing indoors, so the first enabled one
     * of fused, GPS and network is taken.
     */
    private fun availableProvider(locationManager: LocationManager): String? =
        allProviders().firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

    private fun allProviders(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        private const val TIMEOUT_MS = 15_000L
        private val DEFAULT_MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(5)
    }
}
