package com.example.domain.model

data class User(
    val uid: String,
    val email: String,
    /**
     * The ISO country code chosen at sign-up, where it is known.
     *
     * Null for anyone signed in from a session that predates the country field, and for users the
     * auth backend hands back without a profile - the country lives with the player's own stats
     * (see [com.example.domain.repository.UserStatsRepository.countryCode]), which is what the
     * leaderboard reads, so a null here is missing detail rather than a missing country.
     */
    val countryCode: String? = null
)
