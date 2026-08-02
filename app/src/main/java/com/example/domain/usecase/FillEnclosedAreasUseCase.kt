package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.StompedHexRepository

/**
 * Flood-fills any pockets of unstomped cells that are fully surrounded by stomped cells,
 * so a player who walks a closed loop automatically claims the interior.
 *
 * @param onAreasEnclosed told how many separate pockets a fill closed, so the "walked a closed loop"
 * achievement can count them. Reported from here rather than inferred later because the event leaves
 * no trace afterwards: once the interior is claimed it is indistinguishable from ground that was
 * walked over. A lambda rather than a repository so the flood fill stays pure enough to unit-test.
 */
class FillEnclosedAreasUseCase(
    private val repository: StompedHexRepository,
    private val hexGridEngine: HexGridEngine,
    private val onAreasEnclosed: suspend (Int) -> Unit = {}
) {
    /** @return how many separate enclosed pockets this call claimed - one per loop closed. */
    suspend operator fun invoke(
        newCell: String,
        neighborhood: String?,
        stompedAddresses: Set<String>
    ): Int {
        val stomped = stompedAddresses.toMutableSet()
        stomped.add(newCell)

        val neighbors = try {
            hexGridEngine.gridDisk(newCell, 1).filter { it != newCell }
        } catch (e: Exception) {
            emptyList()
        }

        val enclosedCellsToStomp = mutableSetOf<String>()
        var pocketsClosed = 0

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
                pocketsClosed++
            }
        }

        if (enclosedCellsToStomp.isNotEmpty()) {
            repository.stompAll(enclosedCellsToStomp.toList(), neighborhood)
            onAreasEnclosed(pocketsClosed)
        }
        return pocketsClosed
    }

    private companion object {
        const val MAX_FLOOD_FILL_SIZE = 200
    }
}
