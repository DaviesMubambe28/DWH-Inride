package zm.co.dhwinride.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult as AndroidLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LatLng(val latitude: Double, val longitude: Double)

data class Address(
    val formatted: String,
    val locality: String? = null,
    val country: String? = null,
)

sealed class LocationResult {
    data class Success(val latLng: LatLng, val address: Address) : LocationResult()
    data object PermissionDenied : LocationResult()
    data object LocationDisabled : LocationResult()
    data class Error(
        val throwable: Throwable,
        val message: String = throwable.message ?: "Unable to determine your location.",
    ) : LocationResult()
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
)

class LocationTracker(context: Context) {
    private val applicationContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = applicationContext.getSystemService(LocationManager::class.java)
            ?: return false
        return runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied
        if (!isLocationEnabled()) return LocationResult.LocationDisabled

        return try {
            val cachedLocation = runCatching {
                fusedLocationClient.lastLocation.awaitTask()
            }.getOrNull()
            val location = cachedLocation?.takeIf(::isUsableLocation) ?: requestFreshLocation()

            if (location == null || !isUsableLocation(location)) {
                LocationResult.Error(
                    IllegalStateException("The device did not return a usable location."),
                    "The device did not return a usable location. Check GPS reception and try again.",
                )
            } else {
                val latLng = LatLng(location.latitude, location.longitude)
                LocationResult.Success(latLng, reverseGeocode(latLng))
            }
        } catch (securityException: SecurityException) {
            LocationResult.PermissionDenied
        } catch (exception: Exception) {
            LocationResult.Error(
                exception,
                exception.message ?: "Unable to determine your current location.",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        val cancellationTokenSource = CancellationTokenSource()
        return try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token,
            ).awaitTask {
                cancellationTokenSource.cancel()
            }
        } finally {
            cancellationTokenSource.cancel()
        }
    }

    private fun isUsableLocation(location: Location): Boolean =
        location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0

    private suspend fun reverseGeocode(latLng: LatLng): Address = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(applicationContext, Locale.getDefault())
        val androidAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    latLng.latitude,
                    latLng.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)?.firstOrNull()
        }

        Address(
            formatted = androidAddress?.getAddressLine(0)?.takeIf { it.isNotBlank() }
                ?: "Current location (${latLng.latitude.formatCoordinate()}, ${latLng.longitude.formatCoordinate()})",
            locality = androidAddress?.locality,
            country = androidAddress?.countryName,
        )
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 10_000L): Flow<LocationData> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission is required."))
            return@callbackFlow
        }
        if (!isLocationEnabled()) {
            close(IllegalStateException("Location services are disabled."))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: AndroidLocationResult) {
                result.lastLocation?.let { location ->
                    trySend(LocationData(location.latitude, location.longitude, location.accuracy))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private suspend fun <T> Task<T>.awaitTask(onCancellation: () -> Unit = {}): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { onCancellation() }
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }

    private fun Double.formatCoordinate(): String =
        String.format(Locale.US, "%.5f", this)
}
