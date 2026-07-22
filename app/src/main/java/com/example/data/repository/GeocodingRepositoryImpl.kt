package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.domain.repository.GeocodingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class GeocodingRepositoryImpl(private val context: Context) : GeocodingRepository {
    override suspend fun reverseGeocodePlaceName(lat: Double, lng: Double): String? =
        try {
            getAddresses(lat, lng)?.firstOrNull()?.let { addr ->
                addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea
            }
        } catch (e: Exception) {
            null
        }

    private suspend fun getAddresses(lat: Double, lng: Double): List<Address>? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: non-deprecated async overload.
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }
        }
    }
}
