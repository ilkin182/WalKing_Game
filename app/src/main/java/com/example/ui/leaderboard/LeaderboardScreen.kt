package com.example.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Countries
import com.example.domain.model.Leaderboard
import com.example.domain.model.LeaderboardCategory
import com.example.domain.model.LeaderboardRank
import com.example.ui.components.CountryChip
import com.example.ui.components.CountryPickerDialog
import com.example.ui.map.GameViewModel

private val Background = Color(0xFF0F1A1B)
private val CardSurface = Color(0xFF0F2624)
private val CardBorder = Color(0xFF1B3D3A)
private val Accent = Color(0xFF5DF2D6)
private val Muted = Color(0xFF98BCB6)
private val Gold = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFE27D60)

/**
 * The country ranking tab.
 *
 * Two boards over the same field of players - explored cells and unlocked badges - because they
 * reward different things: one is how much ground a player has covered, the other how varied their
 * walking has been, and a player who is nowhere on the first is often well up the second.
 */
@Composable
fun LeaderboardScreen(
    viewModel: GameViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.leaderboard.collectAsState()
    val category by viewModel.leaderboardCategory.collectAsState()
    val countryCode by viewModel.country.collectAsState()
    val country = Countries.byCode(countryCode)

    var pickerOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .testTag("leaderboard_screen_container")
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
                    text = "ÖLKƏ REYTİNQİ",
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
                        .testTag("close_leaderboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reytinq pəncərəsini bağla",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when (val current = state) {
                LeaderboardUiState.Loading -> LoadingBody()

                LeaderboardUiState.CountryMissing -> CountryMissingBody(
                    onChooseCountry = { pickerOpen = true }
                )

                is LeaderboardUiState.Ready -> BoardBody(
                    board = current.board,
                    category = category,
                    countryLabel = { CountryChip(country = country, onClick = { pickerOpen = true }) },
                    onSelectCategory = viewModel::selectLeaderboardCategory
                )
            }
        }
    }

    if (pickerOpen) {
        CountryPickerDialog(
            selected = country,
            onDismiss = { pickerOpen = false },
            onSelect = {
                viewModel.selectCountry(it.code)
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

@Composable
private fun CountryMissingBody(onChooseCountry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Leaderboard,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Reytinqə qoşulmaq üçün ölkəni seç",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nəticələr ölkə üzrə müqayisə olunur. Hesabın qeydiyyatdan keçəndə ölkə seçilməyibsə, buradan seçə bilərsən.",
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onChooseCountry,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0A1F1C)),
            modifier = Modifier.testTag("choose_country_button")
        ) {
            Text("ÖLKƏ SEÇ", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoardBody(
    board: Leaderboard,
    category: LeaderboardCategory,
    countryLabel: @Composable () -> Unit,
    onSelectCategory: (LeaderboardCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { countryLabel() }

        item {
            CategorySwitch(selected = category, onSelect = onSelectCategory)
        }

        item {
            PlayerStandingCard(board = board, category = category)
        }

        item {
            Text(
                text = category.title.uppercase(),
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(items = board.rows, key = { it.entry.playerId }) { row ->
            LeaderboardRow(row = row, category = category)
        }

        item {
            // Said plainly rather than left to be discovered: the player's own number is real, the
            // field it is measured against is not, and a ranking that hides that is a lie.
            Text(
                text = "Qeyd: hazırda cihazda server yoxdur - öz nəticən realdır, digər oyunçular isə " +
                    "müqayisə üçün sabit nümunə siyahısıdır. Server qoşulanda real oyuncularla əvəzlənəcək.",
                color = Color(0xFF5A7C77),
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

/** The two boards, as a pair of buttons rather than a tab row - there are only ever two. */
@Composable
private fun CategorySwitch(
    selected: LeaderboardCategory,
    onSelect: (LeaderboardCategory) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CategoryButton(
            category = LeaderboardCategory.CELLS,
            isSelected = selected == LeaderboardCategory.CELLS,
            onClick = { onSelect(LeaderboardCategory.CELLS) },
            modifier = Modifier.weight(1f)
        )
        CategoryButton(
            category = LeaderboardCategory.ACHIEVEMENTS,
            isSelected = selected == LeaderboardCategory.ACHIEVEMENTS,
            onClick = { onSelect(LeaderboardCategory.ACHIEVEMENTS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CategoryButton(
    category: LeaderboardCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(if (isSelected) Accent else CardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, if (isSelected) Accent else CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag("leaderboard_category_${category.name}")
    ) {
        Icon(
            imageVector = if (category == LeaderboardCategory.CELLS) {
                Icons.Default.Hexagon
            } else {
                Icons.Default.EmojiEvents
            },
            contentDescription = null,
            tint = if (isSelected) Color(0xFF0A1F1C) else Muted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.shortLabel,
            color = if (isSelected) Color(0xFF0A1F1C) else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Where the player stands, called out above the list so they do not have to hunt for their row. */
@Composable
private fun PlayerStandingCard(board: Leaderboard, category: LeaderboardCategory) {
    val row = board.playerRow

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_standing_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SƏNİN YERİN",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (row != null) "#${row.position}" else "-",
                    color = Accent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${board.playerCount} oyuncu arasında",
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = category.shortLabel,
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${row?.score ?: 0}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.unitLabel,
                    color = Color(0xFF5A7C77),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(row: LeaderboardRank, category: LeaderboardCategory) {
    val positionColor = when (row.position) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> Muted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (row.isCurrentPlayer) Color(0x1A5DF2D6) else CardSurface,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (row.isCurrentPlayer) Accent else CardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(if (row.isCurrentPlayer) "leaderboard_row_player" else "leaderboard_row_${row.position}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(positionColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Text(
                text = "${row.position}",
                color = positionColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = if (row.isCurrentPlayer) "${row.entry.nickname} (sən)" else row.entry.nickname,
            color = if (row.isCurrentPlayer) Accent else Color.White,
            fontSize = 15.sp,
            fontWeight = if (row.isCurrentPlayer) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${row.score} ${category.unitLabel}",
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
