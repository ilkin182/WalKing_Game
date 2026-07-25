package com.example.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoLocation
import com.example.domain.model.GridCell
import com.example.domain.model.RegionStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameViewModel(private val useCases: GameUseCases) : ViewModel() {

    // Player Nickname
    val nickname: StateFlow<String> = useCases.observeNickname()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = "")

    fun updateNickname(newName: String) {
        useCases.updateNickname(newName)
    }

    // Total distance walked in meters
    val totalDistanceWalked: StateFlow<Double> = useCases.observeTotalDistance()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.0)

    // Start time of statistics
    val statsStartTimestamp: StateFlow<Long> = useCases.observeStatsStartTimestamp()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = System.currentTimeMillis()
        )

    // Foreground step-counter xidmətindən gələn canlı addım sayı
    val stepCount: StateFlow<Int> = useCases.observeStepCount()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0)

    // Error and logging state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Current location state
    private val _currentLocation = MutableStateFlow<GeoLocation?>(null)
    val currentLocation: StateFlow<GeoLocation?> = _currentLocation.asStateFlow()

    // Stomped hexagons set (retrieved reactively from Room via the domain layer)
    val stompedHexes: StateFlow<Set<String>> = useCases.observeStompedHexAddresses()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptySet())

    val regionStats: StateFlow<List<RegionStat>> = useCases.observeRegionStats()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // Active Neighborhood details
    private val _activeNeighborhood = MutableStateFlow<ActiveNeighborhood?>(null)
    val activeNeighborhood: StateFlow<ActiveNeighborhood?> = _activeNeighborhood.asStateFlow()

    // Active stomp percentage calculated reactively
    val stompPercentage: StateFlow<Double> = combine(stompedHexes, _activeNeighborhood) { stomped, active ->
        if (active == null || active.totalCells.isEmpty()) {
            0.0
        } else {
            val stompedInActive = active.totalCells.count { it in stomped }
            (stompedInActive.toDouble() / active.totalCells.size) * 100.0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private var trackingJob: Job? = null
    private var geocodeJob: Job? = null

    fun startTracking() {
        // Artıq izləmə gedirsə, yenidən başlatmırıq
        if (trackingJob != null) return
        _errorMessage.value = null
        trackingJob = viewModelScope.launch {
            // AppOps xətalarının (GPS) qarşısını almaq üçün tətbiqin tamamilə ön plana (foreground)
            // keçməsini gözləyirik. Bunun üçün 1 saniyəlik (1000ms) gecikmə əlavə edirik.
            delay(1000)

            // Məkan izlənməsini başlat
            useCases.startLocationTracking(3000L)

            // Məkan xətalarını dinləyirik və UI-a ötürürük
            launch {
                useCases.observeLocationErrors().collect { error ->
                    _errorMessage.value = error
                }
            }

            // Yeni məkan məlumatlarını dinləyirik və UI-ı yeniləyirik
            useCases.observeLocationUpdates().collect { location ->
                _currentLocation.value = location
                handleNewLocation(location)
            }
        }
    }

    fun stopTracking() {
        // Məkan izlənməsini dayandırırıq
        useCases.stopLocationTracking()
        // Coroutine işini ləğv edirik ki, yaddaş sızması (memory leak) olmasın
        trackingJob?.cancel()
        trackingJob = null
    }

    fun onStepPermissionGranted() {
        useCases.startStepCounter()
    }

    fun simulateLocationUpdate(lat: Double, lng: Double) {
        val mockLocation = GeoLocation(
            latitude = lat,
            longitude = lng,
            accuracyMeters = 5f,
            timestampMillis = System.currentTimeMillis()
        )
        _currentLocation.value = mockLocation
        handleNewLocation(mockLocation)
    }

    private fun handleNewLocation(location: GeoLocation) {
        useCases.recordWalkedDistance(location)

        viewModelScope.launch {
            // Stomp the current cell
            try {
                useCases.stompCell(
                    lat = location.latitude,
                    lng = location.longitude,
                    neighborhood = _activeNeighborhood.value?.name,
                    alreadyStompedAddresses = stompedHexes.value
                )
            } catch (e: Exception) {
                // Ignore conversion errors
            }

            // Reverse geocode & update active neighborhood boundary
            updateActiveNeighborhood(location.latitude, location.longitude)
        }
    }

    private fun updateActiveNeighborhood(lat: Double, lng: Double) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch(Dispatchers.IO) {
            val updated = useCases.updateActiveNeighborhood(lat, lng, _activeNeighborhood.value)
            if (updated != null) {
                _activeNeighborhood.value = updated
            }
        }
    }

    /**
     * Compute every hexagonal grid cell within the given map area (typically the visible
     * viewport) for rendering, so the grid covers the whole map rather than a radius around a
     * single point. Stomped state is looked up against the full persisted history, so previously
     * explored tiles stay marked as explored wherever the map is currently showing.
     */
    fun getGridCellsInBounds(bounds: List<Coordinate>): List<GridCell> {
        return useCases.getGridCellsInBounds(bounds, stompedHexes.value)
    }

    /**
     * Allow testing/simulation of stomping from map taps, or reset functionality
     */
    fun stompCellDirect(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                useCases.stompCell(
                    lat = lat,
                    lng = lng,
                    neighborhood = _activeNeighborhood.value?.name,
                    alreadyStompedAddresses = stompedHexes.value,
                    forceRestomp = true
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun clearAllStomps() {
        viewModelScope.launch {
            try {
                useCases.clearProgress()
                useCases.recordWalkedDistance.reset()
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't clear progress. Please try again."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}

class GameViewModelFactory(private val useCases: GameUseCases) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(useCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
