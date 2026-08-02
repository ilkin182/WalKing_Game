package com.example.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.achievement.AchievementCatalog
import com.example.domain.achievement.AchievementCategory
import com.example.domain.achievement.AchievementProgress
import com.example.domain.achievement.PlayerStats
import com.example.domain.achievement.ProgressUnit
import com.example.ui.components.AchievementItem
import java.util.Locale

/** The whole catalogue, grouped for display, judged against one measured snapshot. */
fun achievementSections(stats: PlayerStats): List<AchievementSection> {
    val progressById = AchievementCatalog.evaluateAll(stats).associateBy { it.definition.id }

    return AchievementCategory.entries.mapNotNull { category ->
        val items = AchievementCatalog.byCategory[category]
            ?.mapNotNull { progressById[it.id] }
            ?.map { it.toItem() }
            ?: return@mapNotNull null
        if (items.isEmpty()) null else AchievementSection(category, items)
    }
}

data class AchievementSection(
    val category: AchievementCategory,
    val items: List<AchievementItem>
) {
    val icon: ImageVector get() = category.icon()
    val unlockedCount: Int get() = items.count { it.isUnlocked }
}

private fun AchievementProgress.toItem(): AchievementItem {
    val definition = this.definition
    // A secret's description is the reward for unlocking it, so it stays hidden until then.
    val description = if (definition.isSecret && !isUnlocked) {
        "Şərti gizlidir - açıldıqdan sonra görünəcək"
    } else {
        definition.description
    }

    return AchievementItem(
        id = definition.id,
        title = definition.title,
        description = description,
        icon = definition.category.icon(),
        isUnlocked = isUnlocked,
        unlockProgress = fraction,
        progressText = progressText(),
        isComingSoon = !definition.isTracked
    )
}

private fun AchievementProgress.progressText(): String {
    if (!definition.isTracked) return "Tezliklə"

    return when (definition.unit) {
        ProgressUnit.KILOMETRES -> {
            val currentKm = current / 1000.0
            val targetKm = definition.target / 1000.0
            String.format(Locale.US, "%.1f / %.0f km", currentKm.coerceAtMost(targetKm), targetKm)
        }

        ProgressUnit.PERCENT -> String.format(
            Locale.US,
            "%.0f%% / %.0f%%",
            current.coerceAtMost(definition.target),
            definition.target
        )

        // Everything else is a plain count; only the noun after it changes.
        else -> {
            val suffix = when (definition.unit) {
                ProgressUnit.CELLS -> " xana"
                ProgressUnit.DAYS -> " gün"
                ProgressUnit.ZONES -> " zona"
                else -> ""
            }
            val capped = current.coerceAtMost(definition.target)
            "${capped.toInt()} / ${definition.target.toInt()}$suffix"
        }
    }
}

private fun AchievementCategory.icon(): ImageVector = when (this) {
    AchievementCategory.DISTANCE -> Icons.Default.DirectionsWalk
    AchievementCategory.CELLS -> Icons.Default.Hexagon
    AchievementCategory.ZONES -> Icons.Default.Map
    AchievementCategory.STREAKS -> Icons.Default.LocalFireDepartment
    AchievementCategory.TIME -> Icons.Default.Schedule
    AchievementCategory.GEOGRAPHY -> Icons.Default.Terrain
    AchievementCategory.WEATHER -> Icons.Default.AcUnit
    AchievementCategory.ROUTES -> Icons.Default.Route
    AchievementCategory.TRAVEL -> Icons.Default.Flight
    AchievementCategory.SPECIAL -> Icons.Default.EmojiEvents
}
