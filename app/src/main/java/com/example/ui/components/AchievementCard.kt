package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AchievementItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isUnlocked: Boolean,
    val unlockProgress: Float,
    val progressText: String,
    /**
     * The achievement exists in the catalogue but the app does not yet record what it asks about.
     * Marked rather than hidden, so the list is honest about what is coming instead of looking
     * like a badge that simply refuses to unlock.
     */
    val isComingSoon: Boolean = false
)

@Composable
fun AchievementCard(
    achievement: AchievementItem,
    modifier: Modifier = Modifier
) {
    val cardBg = if (achievement.isUnlocked) Color(0xFF102B28) else Color(0xFF0E1F1E)
    val cardBorder = if (achievement.isUnlocked) Color(0xFF26524D) else Color(0xFF142D2A)
    val mainTint = if (achievement.isUnlocked) Color(0xFF5DF2D6) else Color(0x6698BCB6)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Locked / Unlocked Icon Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (achievement.isUnlocked) Color(0x335DF2D6) else Color(0x1A98BCB6),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (achievement.isUnlocked) achievement.icon else Icons.Default.Lock,
                    contentDescription = null,
                    tint = mainTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        color = if (achievement.isUnlocked) Color.White else Color(0x99FFFFFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = achievement.progressText,
                        color = if (achievement.isComingSoon) Color(0x8098BCB6) else mainTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = achievement.description,
                    color = Color(0xFF98BCB6).copy(alpha = if (achievement.isUnlocked) 1f else 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )

                // No progress bar for something that cannot make progress yet - an always-empty
                // track reads as a bug rather than as "not implemented".
                if (achievement.isComingSoon) return@Column

                LinearProgressIndicator(
                    progress = { achievement.unlockProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(100)),
                    color = if (achievement.isUnlocked) Color(0xFF5DF2D6) else Color(0xFF1B4E4A),
                    trackColor = Color(0xFF0C1918)
                )
            }
        }
    }
}
