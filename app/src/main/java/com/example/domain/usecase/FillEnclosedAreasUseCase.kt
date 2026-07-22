package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.StompedHexRepository

/**
 * Flood-fills any pockets of unstomped cells that are fully surrounded by stomped cells,
 * so a player who walks a closed loop automatically claims the interior.
 */
class FillEnclosedAreasUseCase(
    private val repository: StompedHexRepository,
    private val hexGridEngine: HexGridEngine
) {
    suspend operator fun invoke(newCell: String, neighborhood: String?, stompedAddresses: Set<String>) {
        val stomped = stompedAddresses.toMutableSet()
        stomped.add(newCell)

        val neighbors = try {
            hexGridEngine.gridDisk(newCell, 1).filter { it != newCell }
        } catch (e: Exception) {
            emptyList()
        }

        val enclosedCellsToStomp = mutableSetOf<String>()

        for (neighbor in neighbors) {
            if (stomped.contains(neighbor) || enclosedCellsToStomp.contains(neighbor)) continue

            val visited = mutableSetOf<String>()
            val queue = mutableListOf<String>()
            queue.add(neighbor)
            visited.add(neighbor)

            var isEnclosed = true

            while (queue.isNotEmpty()) {
                val current = queue.removeAt(0)

                if (visited.size > MAX_FLOOD_FILL_SIZE) {
                    isEnclosed = false
                    break
                }

                val currentNeighbors = try {
                    hexGridEngine.gridDisk(current, 1).filter { it != current }
                } catch (e: Exception) {
                    emptyList()
                }

                for (n in currentNeighbors) {
                    if (!stomped.contains(n) && !visited.contains(n)) {
                        visited.add(n)
                        queue.add(n)
                    }
                }
            }

            if (isEnclosed) {
                enclosedCellsToStomp.addAll(visited)
                stomped.addAll(visited)
            }
        }

        if (enclosedCellsToStomp.isNotEmpty()) {
            repository.stompAll(enclosedCellsToStomp.toList(), neighborhood)
        }
    }

    private companion object {
        const val MAX_FLOOD_FILL_SIZE = 200
    }
}
