package com.example.data.local

import com.example.domain.model.GeoLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-component holder for the live GPS location. LocationTrackingService is the only writer;
 * LocationRepositoryImpl only reads from [location]/[error].
 */
object LocationTrackingState {
    private val _location = MutableStateFlow<GeoLocation?>(null)
    val location: StateFlow<GeoLocation?> = _location.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun updateLocation(location: GeoLocation) {
        _location.value = location
    }

    fun updateError(message: String?) {
        _error.value = message
    }
}