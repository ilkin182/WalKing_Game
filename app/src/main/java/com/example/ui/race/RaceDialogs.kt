package com.example.ui.race

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import com.example.domain.race.RaceCode
import java.util.Calendar
import java.util.TimeZone

/**
 * Setting up a race: who it is played against, what is counted, how many can be in it, when it ends,
 * and the code that keeps everyone else out.
 *
 * All of it on one sheet rather than a wizard. These are not independent decisions - a two-day
 * sprint between three people and a ten-day shared goal for ten are different things - and a host
 * choosing them wants to see them together.
 *
 * The format comes first because it changes what everything under it means: picking "Komanda" adds
 * the shared goal, and re-words every mode below it.
 */
@Composable
internal fun CreateRaceDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        mode: RaceMode,
        format: RaceFormat,
        target: Double?,
        maxParticipants: Int,
        endsAt: Long,
        code: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RaceMode.CELLS) }
    var format by remember { mutableStateOf(RaceFormat.VERSUS) }
    var target by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf(Race.DEFAULT_PARTICIPANTS) }
    var endsAt by remember { mutableStateOf(defaultDeadline()) }
    var code by remember { mutableStateOf(RaceCode.generate()) }
    var datePickerOpen by remember { mutableStateOf(false) }

    // The goal is in the mode's own unit, and 5000 xana and 5000 km are not the same ambition - so
    // switching what is counted starts the goal again at something sensible for it, rather than
    // leaving a number behind that now means something wildly different.
    LaunchedEffect(format, mode) {
        if (format == RaceFormat.COOP) target = defaultTargetFor(mode)
    }

    val parsedTarget = target.replace(',', '.').toDoubleOrNull()
    val targetIsUsable = format == RaceFormat.VERSUS || (parsedTarget != null && parsedTarget > 0.0)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(RaceBackground, RoundedCornerShape(20.dp))
                .border(1.dp, RaceCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("create_race_dialog")
        ) {
            DialogTitle("YENİ YARIŞ")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= Race.MAX_NAME_LENGTH) name = it },
                label = { Text("Yarışın adı") },
                singleLine = true,
                colors = raceTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("race_name_field")
            )

            SectionLabel("FORMAT")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RaceFormat.entries.forEach { option ->
                    FormatOption(
                        format = option,
                        isSelected = option == format,
                        onClick = { format = option },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = format.explanation,
                color = RaceDim,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            SectionLabel("REJİM")
            RaceMode.entries.forEach { option ->
                ModeOption(
                    mode = option,
                    format = format,
                    isSelected = option == mode,
                    onClick = { mode = option }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (format == RaceFormat.COOP) {
                SectionLabel("ORTAQ HƏDƏF")
                OutlinedTextField(
                    value = target,
                    onValueChange = { typed ->
                        target = typed.filter { it.isDigit() || it == '.' || it == ',' }.take(9)
                    },
                    singleLine = true,
                    suffix = { Text(mode.unitLabel, color = RaceMuted, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (mode == RaceMode.CELLS) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Decimal
                        }
                    ),
                    colors = raceTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("race_target_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    targetPresetsFor(mode).forEach { preset ->
                        PresetChip(
                            label = mode.format(preset),
                            isSelected = parsedTarget == preset,
                            onClick = { target = mode.format(preset) },
                            modifier = Modifier.weight(1f),
                            testTag = "target_preset_" + mode.format(preset)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hər kəsin nəticəsi bu rəqəmə doğru toplanır. Kimin harada gəzdiyinin " +
                        "fərqi yoxdur - hamısı komandanın hesabına yazılır.",
                    color = RaceDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            SectionLabel("İŞTİRAKÇI SAYI")
            ParticipantStepper(
                value = participants,
                onChange = { participants = it.coerceIn(Race.MIN_PARTICIPANTS, Race.MAX_PARTICIPANTS) }
            )

            SectionLabel("NƏ VAXTADƏK")
            DurationPresets(
                selectedEndsAt = endsAt,
                onSelect = { endsAt = it },
                onPickDate = { datePickerOpen = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bitir: " + formatDeadline(endsAt),
                color = RaceAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("race_deadline_text")
            )

            SectionLabel("QOŞULMA KODU")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { typed ->
                        code = typed.uppercase().filter { it.isLetterOrDigit() }.take(RaceCode.LENGTH)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = raceTextFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("race_code_field")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0x1AFFFFFF), CircleShape)
                        .clickable { code = RaceCode.generate() }
                        .testTag("regenerate_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "Başqa kod yarat",
                        tint = RaceMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Yalnız bu kodu və ya dəvət linkini alanlar yarışa qoşula bilər. " +
                    "Qarışdırmamaq üçün O, 0, I və 1 istifadə olunmur.",
                color = RaceDim,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            DialogButtons(
                confirmLabel = "YARAT",
                confirmEnabled = name.isNotBlank() && targetIsUsable && !isWorking,
                isWorking = isWorking,
                onCancel = onDismiss,
                onConfirm = {
                    onCreate(
                        name,
                        mode,
                        format,
                        parsedTarget.takeIf { format == RaceFormat.COOP },
                        participants,
                        endsAt,
                        code.ifBlank { null }
                    )
                },
                confirmTestTag = "confirm_create_race_button"
            )
        }
    }

    if (datePickerOpen) {
        DeadlineDatePicker(
            initial = endsAt,
            onDismiss = { datePickerOpen = false },
            onPicked = {
                endsAt = it
                datePickerOpen = false
            }
        )
    }
}

/** Typing a code or pasting a link - the same box, because a player pastes whatever they were sent. */
@Composable
internal fun JoinRaceDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var typed by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RaceBackground, RoundedCornerShape(20.dp))
                .border(1.dp, RaceCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .testTag("join_race_dialog")
        ) {
            DialogTitle("YARIŞA QOŞUL")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Təşkilatçıdan aldığın kodu yaz və ya dəvət linkini yapışdır.",
                color = RaceMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Kod və ya link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = raceTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("join_code_field")
            )

            Spacer(modifier = Modifier.height(20.dp))

            DialogButtons(
                confirmLabel = "DAVAM ET",
                confirmEnabled = typed.isNotBlank(),
                isWorking = false,
                onCancel = onDismiss,
                onConfirm = { onSubmit(typed) },
                confirmTestTag = "confirm_join_race_button"
            )
        }
    }
}

/**
 * The invitation itself: what the player is about to join, before they join it.
 *
 * Always shown, whether the link was tapped or the code typed. A race is an arrangement with other
 * people - opening a link should never be the same thing as accepting one.
 */
@Composable
internal fun InviteDialog(
    invite: PendingInvite,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RaceBackground, RoundedCornerShape(20.dp))
                .border(1.dp, RaceAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .testTag("race_invite_dialog")
        ) {
            DialogTitle("DƏVƏT")
            Spacer(modifier = Modifier.height(14.dp))

            val race = invite.race
            if (race != null) {
                Text(
                    text = race.name,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                InviteLine("Format", race.format.title)
                InviteLine("Rejim", race.mode.title)
                race.teamTarget?.let {
                    InviteLine(
                        "Ortaq hədəf",
                        race.mode.format(it) + " " + race.mode.unitLabel
                    )
                }
                InviteLine("Təşkilatçı", race.hostName)
                InviteLine("İştirakçı limiti", "${race.maxParticipants}")
                InviteLine("Bitir", formatDeadline(race.endsAt))

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = race.mode.explanationFor(race.format),
                    color = RaceDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            } else {
                Text(
                    text = "Kod: ${invite.code}",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bu kod haqqında əlavə məlumat yoxdur. Yarış bu telefonda varsa, " +
                        "qoşula bilərsən.",
                    color = RaceMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            DialogButtons(
                confirmLabel = "QOŞUL",
                confirmEnabled = !isWorking,
                isWorking = isWorking,
                onCancel = onDismiss,
                onConfirm = onAccept,
                confirmTestTag = "accept_invite_button"
            )
        }
    }
}

@Composable
private fun InviteLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = RaceDim, fontSize = 12.sp, modifier = Modifier.width(120.dp))
        Text(value, color = RaceMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDatePicker(initial: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(endOfLocalDay(it)) } }) {
                Text("SEÇ", color = RaceAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İMTİNA", color = RaceMuted)
            }
        }
    ) {
        DatePicker(state = state)
    }
}

/** The one decision that changes what the rest of the form means, so it is drawn as a pair. */
@Composable
private fun FormatOption(
    format: RaceFormat,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(if (isSelected) RaceAccent else RaceCardSurface, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isSelected) RaceAccent else RaceCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
            .testTag("race_format_" + format.name)
    ) {
        Icon(
            imageVector = if (format == RaceFormat.COOP) Icons.Default.Groups else Icons.Default.Bolt,
            contentDescription = null,
            tint = if (isSelected) RaceOnAccent else RaceMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = format.title,
            color = if (isSelected) RaceOnAccent else RaceMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ModeOption(
    mode: RaceMode,
    format: RaceFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) RaceAccent.copy(alpha = 0.12f) else RaceCardSurface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isSelected) RaceAccent else RaceCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("race_mode_${mode.name}")
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.title,
                color = if (isSelected) RaceAccent else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = mode.explanationFor(format),
                color = RaceDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = RaceAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ParticipantStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(RaceCardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, RaceCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        StepperButton(label = "−", onClick = { onChange(value - 1) }, testTag = "participants_minus")
        Text(
            text = "$value",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .testTag("participants_value")
        )
        StepperButton(label = "+", onClick = { onChange(value + 1) }, testTag = "participants_plus")
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit, testTag: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(Color(0x1AFFFFFF), CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(label, color = RaceAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * The common lengths as one tap each, with the calendar behind the last button.
 *
 * Almost every race is "until Sunday" or "for a week"; the picker is there for the one that is not.
 */
@Composable
private fun DurationPresets(
    selectedEndsAt: Long,
    onSelect: (Long) -> Unit,
    onPickDate: () -> Unit
) {
    val now = System.currentTimeMillis()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DURATION_PRESETS.forEach { (label, days) ->
            val endsAt = now + days * DAY_MILLIS
            // Within an hour of the same instant counts as "this preset is the one selected" - the
            // clock has moved on since the preset was tapped, and the button must stay lit.
            val isSelected = kotlin.math.abs(selectedEndsAt - endsAt) < 60 * 60 * 1000L
            PresetChip(
                label = label,
                isSelected = isSelected,
                onClick = { onSelect(endsAt) },
                modifier = Modifier.weight(1f),
                testTag = "duration_preset_$days"
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(RaceCardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, RaceCardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onPickDate)
                .testTag("pick_deadline_button")
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Tarix seç",
                tint = RaceMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Text(
        text = label,
        color = if (isSelected) RaceOnAccent else RaceMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                if (isSelected) RaceAccent else RaceCardSurface,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (isSelected) RaceAccent else RaceCardBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
            .testTag(testTag)
    )
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = text,
        color = RaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DialogButtons(
    confirmLabel: String,
    confirmEnabled: Boolean,
    isWorking: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmTestTag: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "İMTİNA",
            color = RaceMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .background(
                    if (confirmEnabled) RaceAccent else RaceAccent.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = confirmEnabled, onClick = onConfirm)
                .padding(vertical = 13.dp)
                .testTag(confirmTestTag)
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    color = RaceOnAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = confirmLabel,
                    color = RaceOnAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun raceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RaceAccent,
    unfocusedBorderColor = Color(0xFF26524D),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = RaceAccent,
    unfocusedLabelColor = RaceMuted,
    cursorColor = RaceAccent
)

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** A week, the length most races end up being. */
private fun defaultDeadline(): Long =
    System.currentTimeMillis() + Race.DEFAULT_DURATION_DAYS * DAY_MILLIS

/**
 * The goal a co-op race opens on in each mode - the middle of [targetPresetsFor].
 *
 * A number in the box beats an empty box: the host can see what the unit is and what an ordinary
 * ambition looks like in it, instead of having to invent one before the form will let them on.
 */
private fun defaultTargetFor(mode: RaceMode): String = mode.format(targetPresetsFor(mode)[1])

/**
 * Three goals per mode, spaced roughly by a factor of three: a weekend for a few friends, a week or
 * two for a group, and something a group of ten has to mean it to reach.
 */
private fun targetPresetsFor(mode: RaceMode): List<Double> = when (mode) {
    RaceMode.CELLS -> listOf(500.0, 2_000.0, 5_000.0)
    RaceMode.DISTANCE -> listOf(50.0, 150.0, 500.0)
    RaceMode.LONGEST_WALK -> listOf(20.0, 60.0, 150.0)
}

private val DURATION_PRESETS = listOf(
    "1 gün" to 1L,
    "3 gün" to 3L,
    "1 həftə" to 7L,
    "1 ay" to 30L
)

/**
 * A date chosen in the picker, turned into the end of that day where the player is standing.
 *
 * The picker hands back midnight UTC for the date that was tapped, which is the day before in every
 * timezone west of Greenwich and the right day at the wrong hour everywhere else. A race that ends
 * "on Sunday" ends when Sunday is over for the person racing, so the date parts are read back in UTC
 * and rebuilt at 23:59 local.
 */
private fun endOfLocalDay(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance().apply {
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            23,
            59,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
