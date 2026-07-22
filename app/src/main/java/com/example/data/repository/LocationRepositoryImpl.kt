package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.mapper.toDomain
import com.example.domain.model.GeoLocation
import com.example.domain.repository.LocationRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class LocationRepositoryImpl(private val context: Context) : LocationRepository {
    // Məkan məlumatlarını almaq üçün FusedLocationProviderClient istifadə edirik
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    private val _locationFlow = MutableStateFlow<Location?>(null)
    override val locationUpdates: Flow<GeoLocation> =
        _locationFlow.filterNotNull().map { it.toDomain() }

    private val _errorFlow = MutableStateFlow<String?>(null)
    override val errors: Flow<String> = _errorFlow.filterNotNull()

    @SuppressLint("MissingPermission")
    override fun startUpdates(intervalMs: Long) {
        // Əgər artıq işləyirsə, yenidən başlatmırıq
        if (locationCallback != null) return

        // İcazələrin olub-olmadığını yoxlayırıq
        val hasFinePermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // İcazə yoxdursa, prosesi dayandırırıq
        if (!hasFinePermission && !hasCoarsePermission) {
            _errorFlow.value = "Məkan icazəsi verilmədi. GPS izlənməsi mümkün deyil."
            return
        }

        // AppOps xətalarının (GPS) qarşısını almaq üçün Balanced Power Accuracy istifadə edirik.
        // Bu, batareya ömrünü qoruyur və arxa fonda məkan icazəsi problemlərini azaldır.
        val priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Yeni məkan gəldikdə Flow vasitəsilə abunə olanlara xəbər veririk
                locationResult.lastLocation?.let { _locationFlow.value = it }
            }
        }

        try {
            // Məkan yenilənmələrini tələb edirik
            _errorFlow.value = null
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    _errorFlow.value = "Məkan xidmətinə qoşulmaq mümkün olmadı: ${e.message}"
                    locationCallback = null
                }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorFlow.value = "Məkan xidmətinə qoşulmaq mümkün olmadı: ${e.message}"
            locationCallback = null
        }
    }

    override fun stopUpdates() {
        // Məkan izlənməsini dayandırırıq ki, resurslar boşalsın və batareya sərfiyyatı azalsın
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
            }
            locationCallback = null
        }
    }
}
