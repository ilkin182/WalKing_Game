package com.example.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AchievementCard
import com.example.ui.map.GameViewModel

/**
 * The achievements and badges tab.
 *
 * Split out of the profile screen, which had grown into one long scroll of identity, statistics and
 * achievements together - the badges are what a player checks repeatedly as they walk, so they get
 * their own destination in the bottom bar rather than a section three screens down someone else's.
 */
@Composable
private fun CategoryHeader(section: AchievementSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_${section.category.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = Color(0xFF5DF2D6),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = section.category.title.uppercase(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${section.unlockedCount}/${section.items.size}",
            color = Color(0xFF98BCB6),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AchievementsScreen(
    viewModel: GameViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.playerStats.collectAsState()

    val sections = achievementSections(stats)
    val achievements = sections.flatMap { it.items }
    val unlocked = achievements.count { it.isUnlocked }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1A1B))
            .testTag("achievements_screen_container")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1F5DF2D6), Color.Transparent),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UĞURLAR VƏ NİŞANLAR",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 8.dp)
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(48.dp)
                        .testTag("close_achievements_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Uğurlar pəncərəsini bağla",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Overall progress: on a dedicated screen the first thing worth knowing is how far
                // through the set the player is, before the individual cards.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x335DF2D6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFF5DF2D6),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$unlocked / ${achievements.size} nişan açıldı",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { unlocked.toFloat() / achievements.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(100)),
                            color = Color(0xFF5DF2D6),
                            trackColor = Color(0xFF0C1918)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Grouped by category rather than one flat run of ninety cards: a player looking for
                // "how far off is the marathon" should not have to scroll past the weather badges.
                sections.forEach { section ->
                    CategoryHeader(section)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        section.items.forEach { achievement ->
                            AchievementCard(achievement = achievement)
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
