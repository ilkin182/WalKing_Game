package com.example.data.repository

import android.content.Context
import com.example.domain.repository.PlayerIdentityRepository
import java.util.UUID

/**
 * The device's racing identity, minted once and kept in its own preferences file.
 *
 * Its own file rather than a key in `stomped_stats`, because the two have opposite lifetimes:
 * clearing progress wipes the stats, and an id that went with them would split a player out of every
 * race they were already in, mid-race.
 *
 * A random UUID and nothing else - no account, no email, nothing derived from the hardware. It goes
 * into rosters and into share links, so it has to be something a player can hand to a friend without
 * handing over anything about themselves.
 */
class LocalPlayerIdentityRepository(context: Context) : PlayerIdentityRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences("stomped_identity", Context.MODE_PRIVATE)

    override val playerId: String by lazy {
        prefs.getString(KEY_PLAYER_ID, null) ?: newId().also {
            prefs.edit().putString(KEY_PLAYER_ID, it).apply()
        }
    }

    private fun newId(): String = "p-" + UUID.randomUUID().toString().replace("-", "").take(16)

    private companion object {
        const val KEY_PLAYER_ID = "player_id"
    }
}
