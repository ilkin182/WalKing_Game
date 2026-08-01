package com.example.domain.model

data class StompedHex(
    val hexAddress: String,
    val neighborhood: String?,
    val timestamp: Long,
    /** How completely the fog is lifted here. See [ExploredCell.explorationLevel]. */
    val explorationLevel: Float = ExploredCell.LEVEL_WALKED
) {
    fun toExploredCell(): ExploredCell = ExploredCell(
        cellId = hexAddress,
        exploredAt = timestamp,
        explorationLevel = explorationLevel
    )
}
