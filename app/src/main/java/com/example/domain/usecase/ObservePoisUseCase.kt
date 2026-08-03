package com.example.domain.usecase

import com.example.domain.model.CityBounds
import com.example.domain.model.PointOfInterest
import com.example.domain.repository.PoiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * The cached places, for the geography achievements.
 *
 * A flow rather than a one-off read so the achievements screen fills in as the background pass
 * brings tiles home, instead of showing a player who has walked past ten monuments nothing until
 * they next restart the app.
 */
class ObservePoisUseCase(private val repository: PoiRepository) {
    operator fun invoke(): Flow<List<PointOfInterest>> =
        repository.pois.catch { emit(emptyList()) }
}

/** The cached town extents, for the two city achievements. */
class ObserveCityBoundsUseCase(private val repository: PoiRepository) {
    operator fun invoke(): Flow<List<CityBounds>> =
        repository.cityBounds.catch { emit(emptyList()) }
}
