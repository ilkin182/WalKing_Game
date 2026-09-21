package com.example.ui.race

import com.example.domain.usecase.CreateRaceUseCase
import com.example.domain.usecase.DeleteRaceUseCase
import com.example.domain.usecase.GetPlayerIdUseCase
import com.example.domain.usecase.JoinRaceUseCase
import com.example.domain.usecase.LeaveRaceUseCase
import com.example.domain.usecase.ObserveCountryUseCase
import com.example.domain.usecase.ObserveExploredCellsUseCase
import com.example.domain.usecase.ObserveMyRacesUseCase
import com.example.domain.usecase.ObserveNicknameUseCase
import com.example.domain.usecase.ObserveWalkSessionsUseCase
import com.example.domain.usecase.PublishRaceScoreUseCase

/**
 * What [RaceViewModel] depends on, bundled the same way [com.example.ui.map.GameUseCases] is.
 *
 * The last four are not about races at all - they are the player's name, their country, and the two
 * histories a race score is measured from. A race needs them because it has to answer "how much of
 * this did *you* do since Tuesday", and only the walking history can say.
 */
data class RaceUseCases(
    val observeMyRaces: ObserveMyRacesUseCase,
    val createRace: CreateRaceUseCase,
    val joinRace: JoinRaceUseCase,
    val leaveRace: LeaveRaceUseCase,
    val deleteRace: DeleteRaceUseCase,
    val publishRaceScore: PublishRaceScoreUseCase,
    val playerId: GetPlayerIdUseCase,
    val observeNickname: ObserveNicknameUseCase,
    val observeCountry: ObserveCountryUseCase,
    val observeExploredCells: ObserveExploredCellsUseCase,
    val observeWalkSessions: ObserveWalkSessionsUseCase
)
