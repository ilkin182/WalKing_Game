package com.example.ui.map

import com.example.domain.model.Weather

/**
 * What the profile screen's weather card should be showing.
 *
 * The failure cases are named apart because they need different words on screen: "we haven't found
 * you yet" is a wait, "we couldn't reach the service" is a retry, and they arrive from different
 * places - one from the GPS, one from the network.
 */
sealed interface WeatherUiState {
    /** Nothing asked for yet. */
    data object Idle : WeatherUiState

    /** A request is in flight and there is no earlier reading to keep showing. */
    data object Loading : WeatherUiState

    /** No GPS fix yet, so there is nowhere to ask about. */
    data object NoLocation : WeatherUiState

    /** The forecast could not be reached. */
    data object Unavailable : WeatherUiState

    data class Loaded(val weather: Weather) : WeatherUiState
}
