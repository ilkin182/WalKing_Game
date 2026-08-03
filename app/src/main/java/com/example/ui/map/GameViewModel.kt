package com.example.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.achievement.PlayerStats
import com.example.domain.achievement.PlayerStatsCalculator
import com.example.domain.engine.DwellTracker
import com.example.domain.engine.ExplorationRules
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.model.CellContext
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GeoBounds
import com.example.domain.model.GeoLocation
import com.example.domain.model.GridCell
import com.example.domain.model.PlaceInfo
import com.example.domain.model.PointOfInterest
import com.example.domain.model.RegionStat
import com.example.domain.model.WalkRoute
import com.example.domain.model.WalkSession
import com.example.domain.model.Weather
import com.example.ui.map.fog.ExploredCellGeometry
import com.example.ui.map.fog.ExploredCellIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    // The full persisted exploration history, cell by cell, with how far the fog is lifted on each.
    val exploredCells: StateFlow<List<ExploredCell>> = useCases.observeExploredCells()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // Stomped hexagons set, derived from the same flow so the grid and the fog can never disagree
    // about which cells are claimed.
    val stompedHexes: StateFlow<Set<String>> = exploredCells
        .map { cells -> cells.mapTo(HashSet(cells.size)) { it.cellId } }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptySet())

    /**
     * The explored set indexed for the fog layer, rebuilt off the main thread on every change.
     *
     * Resolving a thousand cell outlines is CPU-bound and happens on whichever fix finally lands, so
     * it is kept well away from the frame the map is drawing.
     */
    val exploredIndex: StateFlow<ExploredCellIndex> = exploredCells
        .map { cells -> buildIndex(cells) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExploredCellIndex.EMPTY
        )

    /**
     * Cells that have just been revealed, for the map's 300 ms reveal animation.
     *
     * Emitted from diffing successive explored sets rather than from the stomp call's return value,
     * so cells revealed indirectly - the trail between two fixes, an enclosed area getting filled
     * in, a dwell's vision ring - animate the same way as the one the player is standing in.
     */
    private val _newlyExplored = MutableSharedFlow<List<ExploredCellGeometry>>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newlyExplored: SharedFlow<List<ExploredCellGeometry>> = _newlyExplored.asSharedFlow()

    private var indexVersion = 0L
    private var knownCellIds: Set<String> = emptySet()

    private fun buildIndex(cells: List<ExploredCell>): ExploredCellIndex {
        val index = ExploredCellIndex.build(cells, ++indexVersion, useCases.gridCellLookup::cornersOf)

        // The very first emission is the history restored from Room, not something that just
        // happened - replaying a reveal animation for every cell the player has ever walked would
        // be a light show on launch.
        val previous = knownCellIds
        knownCellIds = cells.mapTo(HashSet(cells.size)) { it.cellId }

        if (previous.isNotEmpty()) {
            val revealed = cells.filter { it.cellId !in previous }.mapNotNull(::toGeometry)
            if (revealed.isNotEmpty()) _newlyExplored.tryEmit(revealed)
        }
        return index
    }

    private fun toGeometry(cell: ExploredCell): ExploredCellGeometry? {
        val corners = useCases.gridCellLookup.cornersOf(cell.cellId)
        val bounds = GeoBounds.of(corners) ?: return null
        return ExploredCellGeometry(cell.cellId, corners, cell.explorationLevel, bounds)
    }

    /**
     * The weather where the player is, for the profile screen.
     *
     * Only fetched when something asks ([refreshWeather]) rather than followed continuously: the
     * card is on one screen, and the reading barely changes over a walk. The repository holds a
     * short-lived cache, so opening the profile repeatedly costs one request, not one per open.
     */
    private val _weather = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val weather: StateFlow<WeatherUiState> = _weather.asStateFlow()

    private var weatherJob: Job? = null

    fun refreshWeather() {
        val location = _currentLocation.value
        if (location == null) {
            // Without a fix there is nowhere to ask about. Say so rather than spinning forever -
            // this is the state on a cold start before the first GPS lock.
            _weather.value = WeatherUiState.NoLocation
            return
        }
        if (weatherJob?.isActive == true) return

        weatherJob = viewModelScope.launch {
            // A previous reading stays on screen while a new one loads, so a refresh does not blank
            // the card - only the very first load shows the spinner.
            if (_weather.value !is WeatherUiState.Loaded) _weather.value = WeatherUiState.Loading
            val fetched = useCases.getWeather(location.latitude, location.longitude)
            _weather.value = if (fetched != null) {
                WeatherUiState.Loaded(fetched)
            } else {
                WeatherUiState.Unavailable
            }
        }
    }

    val regionStats: StateFlow<List<RegionStat>> = useCases.observeRegionStats()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    /**
     * The single measured snapshot every achievement is judged against.
     *
     * Recomputed off the main thread whenever the history changes: it walks every cell the player
     * has ever claimed, and the achievements screen would otherwise redo that work on each of its
     * ninety rules, on every recomposition.
     */
    val walkSessions: StateFlow<List<WalkSession>> = useCases.observeWalkSessions()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    /** The line each walk traced, for the achievements that ask what shape the player drew. */
    val walkRoutes: StateFlow<List<WalkRoute>> = useCases.observeWalkRoutes()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val closedLoops: StateFlow<Int> = useCases.observeClosedLoops()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0)

    /** The parks, monuments, bridges and coastline cached so far, for the geography achievements. */
    private val pointsOfInterest: StateFlow<List<PointOfInterest>> = useCases.observePois()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val cityBounds: StateFlow<List<CityBounds>> = useCases.observeCityBounds()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // Three groups rather than one flat combine: `combine` is only typed up to five sources, and the
    // nine here fall naturally into what was claimed, how it was walked, and what was there already.
    private val exploration = combine(
        exploredCells,
        totalDistanceWalked,
        regionStats,
        statsStartTimestamp,
        ::Exploration
    )

    private val walking = combine(walkSessions, walkRoutes, closedLoops, ::Walking)

    private val world = combine(pointsOfInterest, cityBounds, ::World)

    val playerStats: StateFlow<PlayerStats> = combine(
        exploration,
        walking,
        world
    ) { claimed, walked, around ->
        PlayerStatsCalculator.calculate(
            cells = claimed.cells,
            totalDistanceMeters = claimed.distanceMeters,
            regionStats = claimed.regions,
            statsStartMillis = claimed.startedAt,
            sessions = walked.sessions,
            routes = walked.routes,
            closedLoops = walked.closedLoops,
            pois = around.pois,
            cityBounds = around.cities,
            resolveCenter = { cellId -> useCases.gridCellLookup.centerOf(cellId) },
            neighborsOf = { cellId -> useCases.gridCellLookup.neighborsOf(cellId) }
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerStats.EMPTY
        )

    private data class Exploration(
        val cells: List<ExploredCell>,
        val distanceMeters: Double,
        val regions: List<RegionStat>,
        val startedAt: Long
    )

    private data class Walking(
        val sessions: List<WalkSession>,
        val routes: List<WalkRoute>,
        val closedLoops: Int
    )

    /** What was on the ground before the player got there. */
    private data class World(
        val pois: List<PointOfInterest>,
        val cities: List<CityBounds>
    )


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

    init {
        // Fills in the elevation and the place of cells that have neither, oldest first - which is
        // also what backfills everything claimed before those columns existed.
        //
        // Started with the ViewModel rather than with tracking: a player who opens the app to look
        // at their badges without going for a walk should still see their history fill in. Separate
        // jobs so a stalled geocoder cannot hold up the elevations.
        viewModelScope.launch { drainBacklog { useCases.enrichCellElevations() } }
        viewModelScope.launch { drainBacklog { useCases.enrichCellPlaces() } }

        // And what was on the ground: the parks, monuments and coastline the geography achievements
        // ask about, plus the extent of the towns walked in. Their own jobs, and far slower than the
        // two above - the repository holds these to one request every fifteen seconds because
        // Overpass and Nominatim are donated infrastructure, not a paid API. See PoiRepositoryImpl.
        viewModelScope.launch { drainBacklog { useCases.enrichPoiTiles() } }
        viewModelScope.launch { drainBacklog { useCases.enrichCityBounds() } }
    }

    private var trackingJob: Job? = null
    private var geocodeJob: Job? = null

    /** The last place reverse geocoding resolved, recorded with each cell claimed after it. */
    private var lastKnownPlace: PlaceInfo? = null

    // The fix the next stomp draws its trail from. Successive fixes are metres apart, so each new
    // one claims the whole segment back to this point rather than only the cell it landed in -
    // otherwise the claimed area comes out as a dotted line with gaps between the fixes. Cleared
    // whenever tracking stops, so resuming somewhere else doesn't paint a trail across the gap.
    private var lastStompedFrom: Coordinate? = null

    /**
     * Watches for the player standing still long enough to have looked around them. Reset alongside
     * [lastStompedFrom] so a pause in tracking does not count towards a dwell.
     */
    private val dwellTracker = DwellTracker()

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
            useCases.startWalkSession()

            // Məkan xətalarını dinləyirik və UI-a ötürürük
            launch {
                useCases.observeLocationErrors().collect { error ->
                    _errorMessage.value = error
                }
            }

            // Keeps the weather reading warm so cells claimed while walking carry one. The fetch is
            // cached, so this is one request per interval however long the walk is.
            launch {
                while (true) {
                    refreshWeather()
                    delay(WEATHER_REFRESH_INTERVAL_MS)
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
        lastStompedFrom = null
        dwellTracker.reset()

        // Closing the walk runs outside the cancelled tracking job, or it would be cancelled itself
        // before it could write.
        viewModelScope.launch { useCases.endWalkSession() }
    }

    /**
     * Works through an enrichment backlog a batch at a time, pausing between batches.
     *
     * Deliberately unhurried: it is filling in statistics nobody is waiting for, against free
     * services, so it takes a batch at a time and stops as soon as a batch fills nothing - which is
     * both "the backlog is done" and "the service is unavailable right now". Either way the next
     * walk picks it up again.
     */
    private suspend fun drainBacklog(fillOneBatch: suspend () -> Int) {
        while (true) {
            val filled = runCatching { fillOneBatch() }.getOrDefault(0)
            if (filled == 0) return
            delay(ENRICHMENT_BATCH_DELAY_MS)
        }
    }

    fun onStepPermissionGranted() {
        useCases.startStepCounter()
    }

    /**
     * Feeds a fix in as though it came from the location provider, for simulation and tests.
     *
     * Accuracy and timestamp are parameters because they are what the exploration rules key off:
     * whether a fix is trusted enough to clear fog, and how long the player has stood in one cell.
     */
    fun simulateLocationUpdate(
        lat: Double,
        lng: Double,
        accuracyMeters: Float = 5f,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        val mockLocation = GeoLocation(
            latitude = lat,
            longitude = lng,
            accuracyMeters = accuracyMeters,
            timestampMillis = timestampMillis
        )
        _currentLocation.value = mockLocation
        handleNewLocation(mockLocation)
    }

    private fun handleNewLocation(location: GeoLocation) {
        val walkedMeters = useCases.recordWalkedDistance(location)
        if (walkedMeters > 0.0) {
            viewModelScope.launch { useCases.addWalkDistance(walkedMeters, location.timestampMillis) }
        }

        // A vague fix still moves the blue dot and still counts towards distance, but it must not
        // clear fog: revealing a cell is permanent, and a 100 m-accurate fix in a street canyon
        // would carve out ground the player never walked on with no way to take it back.
        if (!ExplorationRules.isAccurateEnough(location)) {
            // Break the trail too - the next good fix must not draw a corridor back through
            // wherever the noisy one thought the player was.
            lastStompedFrom = null
            updateActiveNeighborhood(location.latitude, location.longitude)
            return
        }

        val previousFix = lastStompedFrom
        lastStompedFrom = Coordinate(location.latitude, location.longitude)

        viewModelScope.launch {
            // Part of the walk's traced line. Only accurate fixes are recorded, for the same reason
            // they are the only ones that clear fog: a route drawn through a scattering of bad fixes
            // would read as a walk full of sharp turns nobody made.
            useCases.recordRoutePoint(location.latitude, location.longitude, location.timestampMillis)

            // Stomp the current cell plus every cell walked through since the previous fix
            try {
                useCases.stompCell(
                    lat = location.latitude,
                    lng = location.longitude,
                    neighborhood = _activeNeighborhood.value?.name,
                    alreadyStompedAddresses = stompedHexes.value,
                    from = previousFix,
                    context = currentCellContext()
                )
            } catch (e: Exception) {
                // Ignore conversion errors
            }

            revealVisionRingIfDwelling(location)

            // Reverse geocode & update active neighborhood boundary
            updateActiveNeighborhood(location.latitude, location.longitude)
        }
    }

    /**
     * If the player has now been in the same cell for [ExplorationRules.DWELL_THRESHOLD_MS], reveals
     * the ring around it - they have had time to look about them.
     */
    private suspend fun revealVisionRingIfDwelling(location: GeoLocation) {
        val cellId = useCases.gridCellLookup.cellIdAt(location.latitude, location.longitude) ?: return
        val dwelledIn = dwellTracker.onFix(cellId, location.timestampMillis) ?: return

        try {
            useCases.markVisionRing(
                centerCellId = dwelledIn,
                neighborhood = _activeNeighborhood.value?.name,
                knownLevels = exploredCells.value.associate { it.cellId to it.explorationLevel }
            )
        } catch (e: Exception) {
            // A failed vision-ring write is cosmetic; the cell the player is in is already claimed.
        }
    }

    /**
     * The conditions to record with cells claimed right now.
     *
     * Assembled from what is already in hand - the last weather reading and the last resolved place -
     * so it never blocks the stomping path on a request. Anything not known yet is simply left out;
     * see [com.example.domain.model.CellContext]. Elevation is not here at all: it is filled in
     * afterwards in batches, because one lookup covers a hundred cells.
     */
    private fun currentCellContext(): CellContext? {
        val weather = useCases.weatherSnapshot()
        val place = lastKnownPlace

        val context = CellContext(
            temperatureCelsius = weather?.temperatureCelsius,
            weatherCode = weather?.weatherCode,
            windSpeedKmh = weather?.windSpeedKmh,
            sunriseMinuteOfDay = weather?.sunriseMinuteOfDay,
            sunsetMinuteOfDay = weather?.sunsetMinuteOfDay,
            city = place?.city,
            countryCode = place?.countryCode
        )
        return if (context.isEmpty) null else context
    }

    private fun updateActiveNeighborhood(lat: Double, lng: Double) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch(Dispatchers.IO) {
            val place = useCases.resolvePlace(lat, lng)
            if (place != null) lastKnownPlace = place

            val updated = useCases.updateActiveNeighborhood(lat, lng, _activeNeighborhood.value, place)
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
     *
     * The area is clipped to the 100 km coverage region around the player's last known position,
     * so the entire territory within that radius is divided into cells while panning beyond it
     * (where the player can't walk to anyway) doesn't keep generating grid.
     */
    fun getGridCellsInBounds(bounds: List<Coordinate>): List<GridCell> {
        val location = _currentLocation.value
        val coverageCenter = location?.let { Coordinate(it.latitude, it.longitude) }
        return useCases.getGridCellsInBounds(bounds, stompedHexes.value, coverageCenter)
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
                lastStompedFrom = null
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't clear progress. Please try again."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }

    private companion object {
        /** Often enough that a cell's recorded weather is current, rarely enough to be one request. */
        const val WEATHER_REFRESH_INTERVAL_MS = 10 * 60 * 1000L

        /** Breathing room between enrichment batches - nothing is waiting on them. */
        const val ENRICHMENT_BATCH_DELAY_MS = 5_000L
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
