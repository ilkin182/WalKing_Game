package com.example.domain.repository

/**
 * Who this player is to other players.
 *
 * The solo game never needed one: there is a single player on a device, and the country board
 * publishes them under a fixed local id because nothing was ever going to collide with it. A race
 * does need one - two phones in the same race both calling themselves "local-player" would be one
 * runner with two sets of legs - so this is the id that goes into a roster and into a share link.
 *
 * Its own port rather than another field on [UserStatsRepository]: identity is not progress, it
 * survives [UserStatsRepository.resetStats], and an account system would hand this over from the
 * signed-in user instead of minting it locally.
 */
interface PlayerIdentityRepository {
    /**
     * Stable for the life of the install. Not a secret and not personal - a random string, so a race
     * roster carries no more about a player than the nickname they chose.
     */
    val playerId: String
}
