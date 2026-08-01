package com.example.domain.model

data class StompedHex(
    val hexAddress: String,
    val neighborhood: String?,
    val timestamp: Long,
    /** How completely the fog is lifted here. See [ExploredCell.explorationLevel]. */
    val explorationLevel: Float = ExploredCell.LEVEL_WALKED,
    /** Conditions where and when this was claimed. See [CellContext]. */
    val context: CellContext? = null
) {
    fun toExploredCell(): ExploredCell = ExploredCell(
        cellId = hexAddress,
        exploredAt = timestamp,
        explorationLevel = explorationLevel,
        context = context
    )
}
