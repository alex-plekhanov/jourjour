package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Making room. What is written down has to move when something is put on top of it, and
 * how far is the whole question: far enough to be clear, never further, and an empty
 * afternoon takes up as much of the push as it is wide.
 */
class SettlingTest {

    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun `nothing moves when nothing is sat upon`() {
        val events = listOf(
            event(1, "2026-10-03T09:00", 60),
            event(2, "2026-10-03T12:00", 60),
        )
        assertEquals(emptyList<Move>(), settle(events, pinned = setOf(1)))
    }

    @Test
    fun `an empty hour absorbs the push and the rest of the day stands still`() {
        // Breakfast runs an hour over lunch; there is an hour free after lunch, which is
        // exactly enough, so the museum at three is not touched.
        val events = listOf(
            event(1, "2026-10-03T09:00", 4 * 60),
            event(2, "2026-10-03T12:00", 60),
            event(3, "2026-10-03T15:00", 60),
        )
        assertEquals(listOf(Move(2, at("2026-10-03T13:00"))), settle(events, pinned = setOf(1)))
    }

    @Test
    fun `a push carries on down the day while things are still in the way`() {
        val events = listOf(
            event(1, "2026-10-03T09:00", 5 * 60),
            event(2, "2026-10-03T12:00", 60),
            event(3, "2026-10-03T13:00", 60),
        )
        assertEquals(
            listOf(Move(2, at("2026-10-03T14:00")), Move(3, at("2026-10-03T15:00"))),
            settle(events, pinned = setOf(1)),
        )
    }

    @Test
    fun `a linked event goes wherever the one before it goes`() {
        val flight = event(1, "2026-10-03T09:00", 180)
        val bags = event(2, "2026-10-03T12:00", 20, follows = 1)
        // The flight is delayed two hours; the bags come off it whatever it does.
        val delayed = flight.copy(startsAt = at("2026-10-03T11:00"))

        assertEquals(
            listOf(Move(2, at("2026-10-03T14:00"))),
            settle(listOf(delayed, bags), pinned = setOf(1)),
        )
    }

    @Test
    fun `a chain moves as one thing, and pushes what it runs into`() {
        val flight = event(1, "2026-10-03T09:00", 180)
        val bags = event(2, "2026-10-03T12:00", 20, follows = 1)
        val taxi = event(3, "2026-10-03T12:20", 40, follows = 2)
        val hotel = event(4, "2026-10-03T13:30", 30)
        val delayed = flight.copy(startsAt = at("2026-10-03T10:00"))

        assertEquals(
            listOf(
                Move(2, at("2026-10-03T13:00")),
                Move(3, at("2026-10-03T13:20")),
                Move(4, at("2026-10-03T14:00")),
            ),
            settle(listOf(delayed, bags, taxi, hotel), pinned = setOf(1)),
        )
    }

    @Test
    fun `a chain moved earlier follows it back`() {
        val flight = event(1, "2026-10-03T09:00", 180)
        val bags = event(2, "2026-10-03T12:00", 20, follows = 1)
        val earlier = flight.copy(startsAt = at("2026-10-03T08:00"))

        assertEquals(
            listOf(Move(2, at("2026-10-03T11:00"))),
            settle(listOf(earlier, bags), pinned = setOf(1)),
        )
    }

    @Test
    fun `what came before is never shoved backwards to make room`() {
        val morning = event(1, "2026-10-03T09:00", 120)
        val moved = event(2, "2026-10-03T13:00", 60).copy(startsAt = at("2026-10-03T10:00"))

        // The clash stands. Clearing it would mean pushing the morning into yesterday,
        // and the warning says so instead.
        assertEquals(emptyList<Move>(), settle(listOf(morning, moved), pinned = setOf(2)))
        assertEquals(1, clashes(listOf(morning, moved)).size)
    }

    @Test
    fun `what begins at the same moment as the pinned one gets out of its way`() {
        // A copy laid exactly over its original, the original first in the list.
        val original = event(1, "2026-10-03T10:00", 60)
        val copy = event(2, "2026-10-03T10:00", 60)

        assertEquals(
            listOf(Move(1, at("2026-10-03T11:00"))),
            settle(listOf(original, copy), pinned = setOf(2)),
        )
    }

    @Test
    fun `a chain beginning with something else stays a chain and the other gives way`() {
        val flight = event(1, "2026-10-03T09:00", 60)
        val bags = event(2, "2026-10-03T10:00", 30, follows = 1)
        // Booked for the very minute the bags are, and listed ahead of them.
        val taxi = event(3, "2026-10-03T10:00", 30)

        assertEquals(
            listOf(Move(3, at("2026-10-03T10:30"))),
            settle(listOf(taxi, flight, bags), pinned = setOf(1)),
        )
    }

    @Test
    fun `the links are kept even when nothing is asked to move out of the way`() {
        val flight = event(1, "2026-10-03T09:00", 180)
        val bags = event(2, "2026-10-03T12:00", 20, follows = 1)
        val hotel = event(3, "2026-10-03T12:10", 30)
        val delayed = flight.copy(startsAt = at("2026-10-03T10:00"))

        // An event tied to another has no time of its own, so that much is written
        // whatever a person decides about the clash it causes.
        assertEquals(
            listOf(Move(2, at("2026-10-03T13:00"))),
            settle(listOf(delayed, bags, hotel), pinned = setOf(1), push = false),
        )
        assertEquals(1, clashes(listOf(delayed, bags.copy(startsAt = at("2026-10-03T13:00")), hotel)).size)
    }

    @Test
    fun `the event to follow is the one that ended nearest`() {
        val flight = event(1, "2026-10-03T14:00", 190)
        val afternoon = event(2, "2026-10-03T12:00", 8 * 60)
        val typed = at("2026-10-03T17:20")

        // The flight landed ten minutes ago; the long afternoon runs until eight and is
        // nearly three hours away from what was typed.
        assertEquals(1L, nearestBefore(listOf(flight, afternoon), typed)?.id)
        assertNull(nearestBefore(listOf(flight, afternoon), at("2026-10-03T08:00")))
    }

    @Test
    fun `settling always stops`() {
        // Every event linked to the one before it, and the first one moved a long way.
        val chain = (1..20L).map { id ->
            event(id, "2026-10-03T09:00", 30, follows = if (id == 1L) null else id - 1)
        }
        val moved = chain.map { if (it.id == 1L) it.copy(startsAt = at("2026-10-04T09:00")) else it }

        val moves = settle(moved, pinned = setOf(1))
        assertEquals(19, moves.size)
        assertTrue(clashes(moved.map { e -> moves.find { it.id == e.id }?.let { e.copy(startsAt = it.startsAt) } ?: e }).isEmpty())
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(moscow).toEpochSecond()

    private fun event(id: Long, at: String, minutes: Int, follows: Long? = null) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = "Event $id",
        startsAt = at(at),
        durationMinutes = minutes,
        followsId = follows,
    )
}
