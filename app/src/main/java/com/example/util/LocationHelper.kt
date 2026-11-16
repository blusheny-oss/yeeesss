package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class GeoStamp(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val resolvedAddress: String = "",
)

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentGeoStamp(context: Context): GeoStamp? = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
                ?: return@withContext null

            val lat = location.latitude
            val lng = location.longitude
            val coordLabel = String.format(Locale.US, "%.5f, %.5f", lat, lng)
            val addressText = reverseGeocode(context, lat, lng)
            GeoStamp(
                latitude = lat,
                longitude = lng,
                label = if (addressText.isNotBlank()) "$coordLabel • $addressText" else coordLabel,
                resolvedAddress = addressText,
            )
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, lat: Double, lng: Double): String {
        return try {
            if (!Geocoder.isPresent()) return ""
            val geocoder = Geocoder(context, Locale("ru", "RU"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var resultAddr = ""
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    resultAddr = addresses.firstOrNull()?.let { addr ->
                        listOfNotNull(addr.locality, addr.thoroughfare, addr.subThoroughfare)
                            .joinToString(", ")
                    }.orEmpty()
                    latch.countDown()
                }
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                resultAddr
            } else {
                val list = geocoder.getFromLocation(lat, lng, 1)
                list?.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.locality, addr.thoroughfare, addr.subThoroughfare)
                        .joinToString(", ")
                }.orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
