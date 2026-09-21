package com.example.ui.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ExploredCell
import com.example.domain.model.Race
import com.example.domain.model.RaceBoard
import com.example.domain.model.RaceCreationResult
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceJoinResult
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceRoster
import com.example.domain.model.WalkSession
import com.example.domain.race.RaceInvite
import com.example.domain.race.RaceScoring
import com.example.domain.race.RaceStandings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The races tab: which races the player is in, where everyone stands, and the three things a player
 * does about that - set one up, join one, share the link to one.
 *
 * ## Why this is not part of GameViewModel
 *
 * The map's ViewModel is the game loop: it owns tracking, claims ground and is alive for as long as
 * the app is. A race is a side room off that - it needs the same walking history but none of the
 * machinery, and it is only ever looked at. Keeping it separate means the races tab costs nothing
 * while it is closed, and that [GameViewModel][com.example.ui.map.GameViewModel] does not grow a
 * second job.
 *
 * ## Where a score comes from
 *
 * The player's own figure is measured here, from their own history, every time that history changes
 * ([RaceScoring]) - and then written to their row in the race, which is what the board ranks. That
 * loop is deliberately one-directional: measure, publish, read back, rank. [lastPublished] is what
 * stops it spinning - Room re-emits on every write whether or not anything changed, so publishing
 * unconditionally would be a write that triggers the read that triggers the write.
 */
class RaceViewModel(
    private val useCases: RaceUseCases,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    /** Who this device is in a roster. Fixed for the life of the install. */
    val playerId: String = useCases.playerId()

    /**
     * The name and country a new race or a join is stamped with.
     *
     * Eagerly shared, not `WhileSubscribed`: nothing on screen renders these, they are only ever
     * read as `.value` at the moment the player taps "yarat" or "qoşul" - and a `WhileSubscribed`
     * flow with no collector still holds its initial value, so every race would have been created by
     * a player with no name. Both are one field of a SharedPreferences-backed StateFlow, so keeping
     * them warm costs nothing.
     */
    private val nickname: StateFlow<String> = useCases.observeNickname()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val countryCode: StateFlow<String?> = useCases.observeCountry()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Re-ranks the boards on a timer so a race that runs out while the tab is open actually finishes.
     *
     * Half a minute, not a second: nothing on the list is measured finer than a minute, and the
     * countdown on the detail screen ticks on its own. Only alive while the tab is being watched.
     */
    private val statusTick = flow {
        while (true) {
            emit(now())
            delay(STATUS_TICK_MS)
        }
    }

    /** The last figure written for each race, so an unchanged score is not written again. */
    private val lastPublished = mutableMapOf<String, Double>()

    val races: StateFlow<RacesUiState> = combine(
        useCases.observeMyRaces(),
        useCases.observeExploredCells(),
        useCases.observeWalkSessions(),
        statusTick
    ) { rosters, cells, sessions, moment ->
        publishOwnScores(rosters, cells, sessions, moment)

        if (rosters.isEmpty()) {
            RacesUiState.Empty
        } else {
            RacesUiState.Ready(rosters.map { RaceStandings.board(it, playerId, moment) })
        }
    }
        // Scoring walks the whole exploration history - the app's largest table - so it is kept off
        // the frame the map is drawing, exactly as GameViewModel keeps its own statistics off it.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RacesUiState.Loading)

    private val _openRaceId = MutableStateFlow<String?>(null)

    /**
     * The race whose detail screen is open, taken from the same list the tab is already showing
     * rather than followed separately - one subscription, and a board that can never disagree with
     * its own row in the list behind it. Falls back to null when the race is left or deleted, which
     * is what closes the screen.
     */
    val openRace: StateFlow<RaceBoard?> = combine(races, _openRaceId) { state, id ->
        if (id == null) return@combine null
        (state as? RacesUiState.Ready)?.boards?.firstOrNull { it.race.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pendingInvite = MutableStateFlow<PendingInvite?>(null)

    /** An invitation waiting for a yes or no - from a tapped link, or a code typed into the box. */
    val pendingInvite: StateFlow<PendingInvite?> = _pendingInvite.asStateFlow()

    private val _message = MutableStateFlow<RaceMessage?>(null)
    val message: StateFlow<RaceMessage?> = _message.asStateFlow()

    private val _isWorking = MutableStateFlow(false)

    /** True while a create or join is in flight, so the buttons cannot be tapped twice. */
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    fun openRace(raceId: String) {
        _openRaceId.value = raceId
    }

    fun closeRace() {
        _openRaceId.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun dismissInvite() {
        _pendingInvite.value = null
    }

    /**
     * Takes in a link the system handed the app, from `MainActivity`.
     *
     * It only ever *offers* the race - joining is a tap on the sheet that comes up. Somebody who
     * follows a link out of curiosity, or who taps the same link twice, has not agreed to anything
     * yet, and a race is an arrangement with other people.
     */
    fun onInviteLink(raw: String?) {
        val parsed = RaceInvite.parse(raw)
        if (parsed == null) {
            if (!raw.isNullOrBlank()) {
                _message.value = RaceMessage("Bu dəvət linki tanınmadı.", isError = true)
            }
            return
        }
        _pendingInvite.value = PendingInvite(raw = raw.orEmpty(), code = parsed.code, race = parsed.race)
    }

    /** Puts a typed code up as an invitation, so typing and tapping a link end at the same sheet. */
    fun previewCode(typed: String) {
        onInviteLink(typed)
    }

    fun acceptPendingInvite() {
        val invite = _pendingInvite.value ?: return
        join(invite.raw.ifBlank { invite.code })
    }

    fun createRace(
        name: String,
        mode: RaceMode,
        format: RaceFormat,
        targetScore: Double?,
        maxParticipants: Int,
        endsAt: Long,
        requestedCode: String?
    ) {
        if (_isWorking.value) return
        _isWorking.value = true

        viewModelScope.launch {
            try {
                val result = useCases.createRace(
                    name = name,
                    mode = mode,
                    maxParticipants = maxParticipants,
                    endsAt = endsAt,
                    hostNickname = nickname.value,
                    hostCountryCode = countryCode.value,
                    format = format,
                    targetScore = targetScore,
                    requestedCode = requestedCode
                )
                when (result) {
                    is RaceCreationResult.Created -> {
                        _openRaceId.value = result.roster.race.id
                        _message.value = RaceMessage(
                            "Yarış yaradıldı. Kod: ${result.roster.race.code}"
                        )
                    }

                    is RaceCreationResult.Rejected ->
                        _message.value = RaceMessage(result.reason.message, isError = true)
                }
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun join(codeOrLink: String) {
        if (_isWorking.value) return
        _isWorking.value = true

        viewModelScope.launch {
            try {
                val result = useCases.joinRace(
                    codeOrLink = codeOrLink,
                    nickname = nickname.value,
                    countryCode = countryCode.value
                )
                applyJoinResult(result)
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun leaveRace(raceId: String) {
        viewModelScope.launch {
            useCases.leaveRace(raceId)
            lastPublished.remove(raceId)
            if (_openRaceId.value == raceId) _openRaceId.value = null
            _message.value = RaceMessage("Yarışdan çıxdın.")
        }
    }

    fun deleteRace(raceId: String) {
        viewModelScope.launch {
            useCases.deleteRace(raceId)
            lastPublished.remove(raceId)
            if (_openRaceId.value == raceId) _openRaceId.value = null
            _message.value = RaceMessage("Yarış ləğv edildi.")
        }
    }

    /** The text to hand to the system share sheet. */
    fun shareTextFor(race: Race): String = RaceInvite.shareText(race)

    /** The link on its own, for the copy button. */
    fun linkFor(race: Race): String = RaceInvite.linkFor(race)

    private fun applyJoinResult(result: RaceJoinResult) {
        when (result) {
            is RaceJoinResult.Joined -> {
                _pendingInvite.value = null
                _openRaceId.value = result.roster.race.id
                _message.value = RaceMessage("\"${result.roster.race.name}\" yarışına qoşuldun!")
            }

            is RaceJoinResult.AlreadyJoined -> {
                _pendingInvite.value = null
                _openRaceId.value = result.roster.race.id
                _message.value = RaceMessage("Sən artıq bu yarışdasan.")
            }

            is RaceJoinResult.Full -> _message.value = RaceMessage(
                "Bu yarış doludur (${result.roster.race.maxParticipants} iştirakçı).",
                isError = true
            )

            is RaceJoinResult.Finished -> _message.value = RaceMessage(
                "Bu yarış artıq bitib.",
                isError = true
            )

            RaceJoinResult.Unknown -> _message.value = RaceMessage(
                // Precisely what has gone wrong, because the fix is specific: a code alone can only
                // find a race this device already has, and a link carries the race with it.
                "Bu kodla yarış tapılmadı. Təşkilatçıdan dəvət linkini istə.",
                isError = true
            )

            RaceJoinResult.BadCode -> _message.value = RaceMessage(
                "Kod düzgün deyil. 6 simvoldan ibarət olmalıdır.",
                isError = true
            )
        }
    }

    /**
     * Measures the player's figure in each of their races and writes back the ones that moved.
     *
     * Runs inside the boards flow rather than in its own collector so it is alive exactly while the
     * tab is - the exploration history is the app's biggest table, and nothing should be holding it
     * hot for a screen nobody is looking at.
     */
    private suspend fun publishOwnScores(
        rosters: List<RaceRoster>,
        cells: List<ExploredCell>,
        sessions: List<WalkSession>,
        moment: Long
    ) {
        rosters.forEach { roster ->
            val race = roster.race
            val measured = RaceScoring.score(
                mode = race.mode,
                startsAt = race.startsAt,
                endsAt = race.endsAt,
                now = moment,
                cells = cells,
                sessions = sessions
            )

            if (lastPublished[race.id] == measured) return@forEach
            lastPublished[race.id] = measured
            useCases.publishRaceScore(race.id, measured)
        }
    }

    private companion object {
        /** Coarse on purpose - see [statusTick]. */
        const val STATUS_TICK_MS = 30_000L
    }
}

class RaceViewModelFactory(private val useCases: RaceUseCases) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RaceViewModel(useCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
