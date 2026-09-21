package com.example.ui.race

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Countries
import com.example.domain.model.RaceBoard
import com.example.domain.model.RaceStanding
import com.example.domain.model.RaceStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * One race in full: the clock, the code, the invitation, and where everybody stands.
 *
 * The code and the share button sit above the standings rather than under them. While a race is
 * filling up, handing the invitation to somebody is the thing the host came here to do - and for
 * everyone else it is the answer to "how do I get my friend in as well".
 */
@Composable
internal fun RaceDetailBody(
    board: RaceBoard,
    viewModel: RaceViewModel,
    modifier: Modifier = Modifier
) {
    val race = board.race
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var confirmingExit by remember(race.id) { mutableStateOf(false) }

    // The only thing on the screen that needs to be current to the second.
    var nowMillis by remember(race.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(race.id, board.status) {
        while (board.status != RaceStatus.FINISHED) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("race_detail_body"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { RaceSummaryCard(board = board, now = nowMillis) }

        item {
            InviteCard(
                code = race.code,
                freeSlots = board.freeSlots,
                onCopy = {
                    clipboard.setText(AnnotatedString(viewModel.linkFor(race)))
                },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, race.name)
                        putExtra(Intent.EXTRA_TEXT, viewModel.shareTextFor(race))
                    }
                    context.startActivity(Intent.createChooser(send, "Dəvəti paylaş"))
                }
            )
        }

        item {
            Text(
                text = if (board.isCoop) {
                    "KOMANDA (${board.participantCount}/${race.maxParticipants})"
                } else {
                    "İŞTİRAKÇILAR (${board.participantCount}/${race.maxParticipants})"
                },
                color = RaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(items = board.rows, key = { it.participant.playerId }) { row ->
            StandingRow(
                row = row,
                board = board,
                now = nowMillis
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            if (confirmingExit) {
                ConfirmExit(
                    isHost = board.isHost,
                    onCancel = { confirmingExit = false },
                    onConfirm = {
                        confirmingExit = false
                        if (board.isHost) {
                            viewModel.deleteRace(race.id)
                        } else {
                            viewModel.leaveRace(race.id)
                        }
                    }
                )
            } else {
                DangerAction(
                    label = if (board.isHost) "YARIŞI LƏĞV ET" else "YARIŞDAN ÇIX",
                    icon = if (board.isHost) Icons.Default.DeleteForever else Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = { confirmingExit = true }
                )
            }
        }

        item {
            Text(
                text = buildString {
                    append("Yarış \"" + race.hostName + "\" tərəfindən yaradılıb. Bitir: ")
                    append(formatDeadline(race.endsAt) + ".")
                    // The caveat belongs loudest here: in a co-op race the number everybody is
                    // looking at is the sum of figures that arrived at different times, so a team
                    // total that looks low is usually a teammate who has not synced, not a
                    // teammate who has not walked.
                    if (board.isCoop) {
                        append(
                            "\n\nÜmumi rəqəm hər iştirakçının son məlum nəticəsini toplayır - " +
                                "server qoşulanadək dostunun bu gün gəzdiyi yalnız onunla " +
                                "görüşəndə buraya düşür."
                        )
                    }
                },
                color = RaceDim,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun RaceSummaryCard(board: RaceBoard, now: Long) {
    val race = board.race

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, RaceAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("race_summary_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = race.name,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            StatusChip(board.status)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = race.format.title + " · " + race.mode.title,
            color = RaceAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = race.mode.explanationFor(race.format),
            color = RaceMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        board.team?.let { team ->
            Spacer(modifier = Modifier.height(16.dp))
            TeamProgressBar(team = team, mode = race.mode, compact = false)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (board.status) {
                        RaceStatus.PENDING -> "BAŞLAYIR"
                        RaceStatus.RUNNING -> "QALIB"
                        RaceStatus.FINISHED -> "NƏTİCƏ"
                    },
                    color = RaceMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (board.status) {
                        RaceStatus.PENDING -> formatRemaining(race.startsAt - now)
                        RaceStatus.RUNNING -> formatRemaining(race.remainingMillisAt(now))
                        // A finished co-op race has a verdict, not a winner: the team either got
                        // there together or ran out of days together.
                        RaceStatus.FINISHED -> board.team?.let {
                            if (it.isReached) "Hədəf alındı!" else "Hədəfə çatmadı"
                        } ?: board.winner?.participant?.nickname ?: "Qalib yoxdur"
                    },
                    color = if (board.status == RaceStatus.FINISHED) RaceGold else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("race_countdown")
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (board.isCoop) "SƏNİN TÖHFƏN" else "SƏNİN YERİN",
                    color = RaceMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (board.isCoop) {
                        race.mode.format(board.playerRow?.score ?: 0.0)
                    } else {
                        board.playerRow?.let { "#${it.position}" } ?: "-"
                    },
                    color = RaceAccent,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (board.isCoop) {
                        race.mode.unitLabel
                    } else {
                        race.mode.format(board.playerRow?.score ?: 0.0) + " " + race.mode.unitLabel
                    },
                    color = RaceMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * The code, big enough to read out, with the two ways of passing it on.
 *
 * Copy puts the *link* on the clipboard rather than the code: a pasted link carries the whole race
 * with it, while a pasted code only works for somebody whose phone already knows the race - which is
 * exactly the case where they did not need the code either.
 */
@Composable
private fun InviteCard(
    code: String,
    freeSlots: Int,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, RaceCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("race_invite_card")
    ) {
        Text(
            text = "QOŞULMA KODU",
            color = RaceMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .testTag("race_code_text")
            )

            IconAction(
                icon = Icons.Default.ContentCopy,
                description = "Dəvət linkini kopyala",
                onClick = onCopy,
                testTag = "copy_race_link_button"
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconAction(
                icon = Icons.Default.Share,
                description = "Dəvət linkini paylaş",
                onClick = onShare,
                testTag = "share_race_link_button",
                emphasised = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (freeSlots > 0) {
                "Linki paylaş - yalnız bu kodu və ya linki alanlar qoşula bilər. " +
                    "$freeSlots boş yer qalıb."
            } else {
                "Bütün yerlər doludur - yeni iştirakçı qoşula bilməz."
            },
            color = RaceDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    testTag: String,
    emphasised: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .background(if (emphasised) RaceAccent else Color(0x1AFFFFFF), CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (emphasised) RaceOnAccent else RaceMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StandingRow(row: RaceStanding, board: RaceBoard, now: Long) {
    // Medal colours are for a race somebody is winning. In a co-op race every contribution is the
    // same kind of thing, so every row is drawn the same way.
    val positionColor = when {
        board.isCoop -> RaceAccent
        row.position == 1 -> RaceGold
        row.position == 2 -> RaceSilver
        row.position == 3 -> RaceBronze
        else -> RaceMuted
    }
    val isHostRow = row.participant.playerId == board.race.hostId

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (row.isCurrentPlayer) Color(0x1A5DF2D6) else RaceCardSurface,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (row.isCurrentPlayer) RaceAccent else RaceCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(
                if (row.isCurrentPlayer) "race_row_player" else "race_row_${row.position}"
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(positionColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Text(
                text = if (board.isCoop) {
                    (row.share * 100).roundToInt().toString() + "%"
                } else {
                    "${row.position}"
                },
                color = positionColor,
                fontSize = if (board.isCoop) 11.sp else 13.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    Countries.byCode(row.participant.countryCode)?.let { append(it.flag + " ") }
                    append(row.participant.nickname)
                    if (row.isCurrentPlayer) append(" (sən)")
                },
                color = if (row.isCurrentPlayer) RaceAccent else Color.White,
                fontSize = 15.sp,
                fontWeight = if (row.isCurrentPlayer) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = buildString {
                    if (isHostRow) append("təşkilatçı · ")
                    // Every figure that is not the player's own is as old as the last time that
                    // phone was in reach; saying so is the difference between a stale number and a
                    // misleading one.
                    append(
                        if (row.isCurrentPlayer) "canlı" else formatAgo(row.participant.updatedAt, now)
                    )
                },
                color = RaceDim,
                fontSize = 11.sp
            )
        }

        Text(
            text = board.race.mode.format(row.score) + " " + board.race.mode.unitLabel,
            color = RaceMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DangerAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceDanger.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, RaceDanger.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
            .testTag("race_exit_button")
    ) {
        Icon(icon, contentDescription = null, tint = RaceDanger, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = RaceDanger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The confirmation, inline rather than as a dialog.
 *
 * Cancelling a race takes it away from everyone who joined, and leaving one gives up a position -
 * neither is undoable, and neither should be one stray tap away.
 */
@Composable
private fun ConfirmExit(isHost: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceDanger.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, RaceDanger.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("race_exit_confirm")
    ) {
        Text(
            text = if (isHost) {
                "Yarışı ləğv etsən, bütün iştirakçılar üçün silinəcək. Davam edilsin?"
            } else {
                "Yarışdan çıxsan, nəticən silinəcək. Davam edilsin?"
            },
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "İMTİNA",
                color = RaceMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "BƏLİ",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .background(RaceDanger, RoundedCornerShape(10.dp))
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 11.dp)
                    .testTag("race_exit_confirm_button"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
