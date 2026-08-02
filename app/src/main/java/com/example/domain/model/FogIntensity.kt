package com.example.domain.model

/**
 * How thick the fog over unexplored ground is, as a user-facing setting.
 *
 * The alpha is deliberately capped below fully opaque at every level: the map's coastlines and road
 * shapes have to stay faintly perceptible under the fog, otherwise unexplored areas read as a hole
 * in the app rather than as territory waiting to be walked.
 */
enum class FogIntensity(val alpha: Float, val label: String) {
    // Light enough that street names and junctions stay legible through it: over the daylight
    // basemap the veil only has to say "you have not been here", and anything heavier turns the
    // half of the map the player is about to walk into into a grey blank.
    LIGHT(0.42f, "Yüngül"),
    MEDIUM(0.70f, "Orta"),
    HEAVY(0.90f, "Sıx");

    /** The 0..255 alpha channel value this level maps to. */
    val alpha255: Int get() = Math.round(alpha * 255f).coerceIn(0, 255)

    companion object {
        /**
         * The lightest level is the default: over the light basemap the point of the fog is to mark
         * unexplored ground, not to hide the streets a player is about to walk down.
         */
        val DEFAULT = LIGHT

        fun fromName(name: String?): FogIntensity =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
