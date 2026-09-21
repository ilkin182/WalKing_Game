package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.data.local.AppDatabase
import com.example.domain.model.Race
import com.example.domain.model.RaceFormat
import com.example.domain.model.RaceMode
import com.example.domain.model.RaceParticipant
import com.example.domain.race.RaceCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NOW = 1_700_000_000_000L
private const val DAY = 24 * 60 * 60 * 1000L

/**
 * Against a real (in-memory) database rather than a fake DAO, because the thing worth testing here
 * is the transaction: taking the last slot, and refusing to let an incoming copy of a race overwrite
 * the stored one. A fake DAO would be testing the fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalRaceRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LocalRaceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = LocalRaceRepository(database.raceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun race(code: String = "ABC234", maxParticipants: Int = 3) = Race(
        id = RaceCode.idOf(code),
        code = code,
        name = "Həftəlik yarış",
        mode = RaceMode.DISTANCE,
        maxParticipants = maxParticipants,
        createdAt = NOW,
        startsAt = NOW,
        endsAt = NOW + 7 * DAY,
        hostId = "p-host",
        hostName = "Elçin"
    )

    private fun participant(id: String, raceId: String, score: Double = 0.0) = RaceParticipant(
        raceId = raceId,
        playerId = id,
        nickname = id,
        countryCode = "AZ",
        joinedAt = NOW,
        score = score,
        updatedAt = NOW
    )

    @Test
    fun `a created race comes back by its code with the host in it`() = runTest {
        val subject = race()
        repository.create(subject, participant("p-host", subject.id))

        val found = repository.findByCode("ABC234")

        assertNotNull(found)
        assertEquals(subject.name, found!!.race.name)
        assertEquals(RaceMode.DISTANCE, found.race.mode)
        assertTrue(found.contains("p-host"))
    }

    @Test
    fun `an unknown code finds nothing`() = runTest {
        assertNull(repository.findByCode("ZZZZ22"))
    }

    @Test
    fun `the last slot goes to one player and the next is turned away`() = runTest {
        val subject = race(maxParticipants = 2)
        repository.create(subject, participant("p-host", subject.id))

        val second = repository.join(subject, participant("p-second", subject.id))
        val third = repository.join(subject, participant("p-third", subject.id))

        assertNotNull(second)
        assertEquals(2, second!!.participantCount)
        assertNull("the third player should not have got in", third)
    }

    @Test
    fun `joining a race this device has never seen creates it locally`() = runTest {
        val fromLink = race(code = "LINK55")

        val joined = repository.join(fromLink, participant("p-friend", fromLink.id))

        assertNotNull(joined)
        assertEquals("LINK55", joined!!.race.code)
        assertTrue(joined.contains("p-friend"))
    }

    @Test
    fun `an incoming copy cannot widen the stored race`() = runTest {
        val stored = race(maxParticipants = 2)
        repository.create(stored, participant("p-host", stored.id))
        repository.join(stored, participant("p-second", stored.id))

        val widened = stored.copy(maxParticipants = 50)
        val third = repository.join(widened, participant("p-third", widened.id))

        assertNull(third)
        assertEquals(2, repository.findByCode(stored.code)!!.race.maxParticipants)
    }

    @Test
    fun `re-joining keeps the score already built up`() = runTest {
        val subject = race()
        repository.create(subject, participant("p-host", subject.id))
        repository.join(subject, participant("p-friend", subject.id))
        repository.updateScore(subject.id, "p-friend", score = 6.5, at = NOW + DAY)

        repository.join(subject, participant("p-friend", subject.id, score = 0.0))

        val roster = repository.findByCode(subject.code)!!
        assertEquals(2, roster.participantCount)
        assertEquals(6.5, roster.participants.first { it.playerId == "p-friend" }.score, 0.0001)
    }

    @Test
    fun `only the races the player is in are observed`() = runTest {
        val mine = race(code = "MINE22")
        val theirs = race(code = "THEM22")
        repository.create(mine, participant("p-me", mine.id))
        repository.create(theirs, participant("p-other", theirs.id))

        repository.observeRosters("p-me").test {
            val rosters = awaitItem()
            assertEquals(1, rosters.size)
            assertEquals("MINE22", rosters.first().race.code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a co-op race keeps its format and its goal through the database`() = runTest {
        val coop = race(code = "TEAM77").copy(
            format = RaceFormat.COOP,
            targetScore = 250.0
        )
        repository.create(coop, participant("p-host", coop.id))

        val found = repository.findByCode("TEAM77")!!.race

        assertEquals(RaceFormat.COOP, found.format)
        assertEquals(250.0, found.teamTarget!!, 0.0001)
    }

    @Test
    fun `leaving removes the player, cancelling removes the race`() = runTest {
        val subject = race()
        repository.create(subject, participant("p-host", subject.id))
        repository.join(subject, participant("p-friend", subject.id))

        repository.leave(subject.id, "p-friend")
        assertFalse(repository.findByCode(subject.code)!!.contains("p-friend"))

        repository.delete(subject.id)
        assertNull(repository.findByCode(subject.code))
    }
}
