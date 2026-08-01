package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.domain.model.PlaceInfo
import com.example.domain.repository.GeocodingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class GeocodingRepositoryImpl(private val context: Context) : GeocodingRepository {

    override suspend fun reverseGeocode(lat: Double, lng: Double): PlaceInfo? =
        try {
            getAddresses(lat, lng)?.firstOrNull()?.let { address ->
                // The same Address the map's zone label already came from - the city and the
                // country were simply being discarded, and the travel achievements need them.
                // Blanks are normalised to null: the geocoder returns an empty string for a field
                // it has no value for as readily as it omits it, and "" would otherwise count as a
                // town of its own in the travel achievements.
                PlaceInfo(
                    neighborhood = firstNonBlank(
                        address.subLocality,
                        address.locality,
                        address.subAdminArea,
                        address.adminArea
                    ),
                    city = firstNonBlank(address.locality, address.subAdminArea, address.adminArea),
                    countryCode = address.countryCode?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            null
        }

    private fun firstNonBlank(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }

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
