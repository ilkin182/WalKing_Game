package com.example.ui.race

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.RaceBoard
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceStatus
import com.example.domain.model.RaceTeamProgress
import kotlinx.coroutines.delay

/**
 * The races tab: everything the player is racing in, and the three ways into one.
 *
 * The list and the detail share this screen rather than living on separate navigation
 * destinations - the whole tab is already an overlay over the map (see
 * [com.example.ui.navigation.MainShell]), and pushing a second destination on top of an overlay
 * would give the player two different back gestures to learn for the same "go back".
 */
@Composable
fun RacesScreen(
    viewModel: RaceViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.races.collectAsState()
    val openRace by viewModel.openRace.collectAsState()
    val invite by viewModel.pendingInvite.collectAsState()
    val message by viewModel.message.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    var createOpen by rememberSaveable { mutableStateOf(false) }
    var joinOpen by rememberSaveable { mutableStateOf(false) }

    // The banner says what just happened; it is about the tap, not about the screen, so it goes
    // away on its own rather than waiting to be dismissed.
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_VISIBLE_MS)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RaceBackground)
            .testTag("races_screen_container")
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
            RacesHeader(
                title = if (openRace != null) "YARIŞ" else "YARIŞLAR",
                isDetail = openRace != null,
                onBack = viewModel::closeRace,
                onClose = onClose
            )

            AnimatedVisibility(visible = message != null) {
                message?.let { MessageBanner(it) }
            }

            val board = openRace
            if (board != null) {
                RaceDetailBody(
                    board = board,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            } else {
                RacesListBody(
                    state = state,
                    onOpenRace = viewModel::openRace,
                    onCreate = { createOpen = true },
                    onJoin = { joinOpen = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (createOpen) {
        CreateRaceDialog(
            isWorking = isWorking,
            onDismiss = { createOpen = false },
            onCreate = { name, mode, format, target, participants, endsAt, code ->
                viewModel.createRace(name, mode, format, target, participants, endsAt, code)
                createOpen = false
            }
        )
    }

    if (joinOpen) {
        JoinRaceDialog(
            onDismiss = { joinOpen = false },
            onSubmit = { typed ->
                joinOpen = false
                viewModel.previewCode(typed)
            }
        )
    }

    invite?.let { pending ->
        InviteDialog(
            invite = pending,
            isWorking = isWorking,
            onDismiss = viewModel::dismissInvite,
            onAccept = viewModel::acceptPendingInvite
        )
    }
}

@Composable
private fun RacesHeader(
    title: String,
    isDetail: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDetail) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(40.dp)
                        .testTag("race_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Yarışlar siyahısına qayıt",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = if (isDetail) 0.dp else 8.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(Color(0x33FFFFFF), CircleShape)
                .size(48.dp)
                .testTag("close_races_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Yarış pəncərəsini bağla",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MessageBanner(message: RaceMessage) {
    val tint = if (message.isError) RaceDanger else RaceAccent
    Text(
        text = message.text,
        color = tint,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("race_message")
    )
}

@Composable
private fun RacesListBody(
    state: RacesUiState,
    onOpenRace: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PrimaryAction(
                    label = "YENİ YARIŞ",
                    icon = Icons.Default.Add,
                    onClick = onCreate,
                    testTag = "create_race_button",
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    label = "KODLA QOŞUL",
                    icon = Icons.AutoMirrored.Filled.Login,
                    onClick = onJoin,
                    testTag = "join_race_button",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (state) {
            RacesUiState.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RaceAccent)
                }
            }

            RacesUiState.Empty -> item { EmptyRaces() }

            is RacesUiState.Ready -> {
                items(items = state.boards, key = { it.race.id }) { board ->
                    RaceCard(board = board, onClick = { onOpenRace(board.race.id) })
                }

                item { ServerNote() }
            }
        }
    }
}

@Composable
private fun EmptyRaces() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 12.dp, end = 12.dp)
            .testTag("races_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = RaceAccent,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hələ yarışın yoxdur",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "İki cür oynaya bilərsən: Rəqabətdə hər kəs təkbaşına yarışır, " +
                "Komandada isə hamının gəzdiyi bir yerə toplanır və ortaq hədəfə birlikdə " +
                "çatırsınız. Sən yarışı yaradırsan, iştirakçı sayını və bitmə tarixini təyin " +
                "edirsən, sonra linki paylaşırsan. Linkə toxunan dostun birbaşa həmin yarışa " +
                "qoşulur.",
            color = RaceMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        ServerNote()
    }
}

/**
 * Said plainly rather than left to be discovered, exactly as the leaderboard does it: the part of
 * this that is real is real, and the part that needs a server is named.
 */
@Composable
private fun ServerNote() {
    Text(
        text = "Qeyd: yarışın özü, kodu, linki və sənin nəticən realdır. Serverə qoşulanadək " +
            "başqa iştirakçıların nəticəsi yalnız onlarla görüşəndə yenilənir - hər telefon " +
            "yalnız öz addımlarını ölçə bilir.",
        color = RaceDim,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun PrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(RaceAccent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, tint = RaceOnAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = RaceOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(RaceCardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, RaceCardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, tint = RaceMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = RaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** One race in the list: what it is, how long is left, and where the player stands in it. */
@Composable
private fun RaceCard(board: RaceBoard, onClick: () -> Unit) {
    val race = board.race
    val row = board.playerRow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, RaceCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("race_card_${race.code}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = race.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            StatusChip(board.status)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = race.format.shortLabel + " · " + race.mode.title,
            color = RaceAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaItem(
                icon = Icons.Default.Groups,
                text = "${board.participantCount}/${race.maxParticipants}"
            )
            Spacer(modifier = Modifier.width(16.dp))
            MetaItem(
                icon = Icons.Default.Timer,
                text = if (board.status == RaceStatus.FINISHED) {
                    formatShortDate(race.endsAt)
                } else {
                    formatRemaining(race.remainingMillisAt(System.currentTimeMillis()))
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // A co-op card answers "how far along are we", a versus card "where am I". Showing a
            // position on a co-op race would be the list quietly keeping score of a game nobody in
            // it is playing.
            if (board.team == null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (row != null) "#${row.position}" else "-",
                        color = RaceAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${race.mode.format(row?.score ?: 0.0)} ${race.mode.unitLabel}",
                        color = RaceMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        board.team?.let { team ->
            Spacer(modifier = Modifier.height(12.dp))
            TeamProgressBar(team = team, mode = race.mode, compact = true)
        }
    }
}

/**
 * How far the team has got, as a bar and the two numbers behind it.
 *
 * Shared by the list card and the race screen so a glance at either one means the same thing; the
 * only difference is how much room the numbers get.
 */
@Composable
internal fun TeamProgressBar(
    team: RaceTeamProgress,
    mode: RaceMode,
    compact: Boolean
) {
    val tint = if (team.isReached) RaceGold else RaceAccent

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { team.fraction },
            color = tint,
            trackColor = Color(0x1AFFFFFF),
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 10.dp)
                .testTag("team_progress_bar")
        )

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = mode.format(team.total) + " / " + mode.format(team.target) + " " +
                    mode.unitLabel,
                color = Color.White,
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .testTag("team_total_text")
            )
            Text(
                text = if (team.isReached) {
                    "Hədəfə çatdınız!"
                } else {
                    mode.format(team.remaining) + " " + mode.unitLabel + " qalıb"
                },
                color = if (team.isReached) RaceGold else RaceMuted,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = RaceDim, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, color = RaceMuted, fontSize = 12.sp)
    }
}

@Composable
internal fun StatusChip(status: RaceStatus) {
    val tint = when (status) {
        RaceStatus.RUNNING -> RaceAccent
        RaceStatus.PENDING -> RaceMuted
        RaceStatus.FINISHED -> RaceBronze
    }
    Text(
        text = status.label.uppercase(),
        color = tint,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Long enough to read a sentence, short enough not to sit over the list. */
private const val MESSAGE_VISIBLE_MS = 4000L
