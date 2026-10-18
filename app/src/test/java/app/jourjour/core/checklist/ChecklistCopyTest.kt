package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copying a list off an older trip. What matters is what is left out — anything already
 * there — and which of the two flags survives the journey.
 */
class ChecklistCopyTest {

    private fun item(
        id: Long,
        tripId: Long,
        section: String,
        title: String,
        done: Boolean = false,
        skipped: Boolean = false,
    ) = ChecklistItem(
        id = id,
        tripId = tripId,
        section = section,
        title = title,
        isDone = done,
        isSkipped = skipped,
    )

    private val source = listOf(
        item(1, 1, "Papers", "Passport"),
        item(2, 1, "Papers", "Visa"),
        item(3, 1, "Bag", "Charger"),
        item(4, 1, "Bag", "Adapter"),
    )

    @Test
    fun `everything comes across when no section is named`() {
        val copied = itemsToCopy(source, emptyList(), section = null, title = null, tripId = 2)

        assertEquals(4, copied.size)
        assertTrue(copied.all { it.tripId == 2L })
        // New rows, not the old ones with their ids attached.
        assertTrue(copied.all { it.id == 0L })
    }

    @Test
    fun `one section takes that section whole`() {
        val copied = itemsToCopy(source, emptyList(), section = "Bag", title = null, tripId = 2)

        assertEquals(listOf("Charger", "Adapter"), copied.map { it.title })
    }

    @Test
    fun `one item takes that item alone`() {
        val copied = itemsToCopy(source, emptyList(), section = "Bag", title = "Adapter", tripId = 2)

        assertEquals(listOf("Adapter"), copied.map { it.title })
    }

    @Test
    fun `what the trip already has is left behind`() {
        val target = listOf(item(9, 2, "Papers", "Passport"))

        val copied = itemsToCopy(source, target, section = null, title = null, tripId = 2)

        assertEquals(listOf("Visa", "Charger", "Adapter"), copied.map { it.title })
    }

    @Test
    fun `the same wording in another section is not the same item`() {
        val target = listOf(item(9, 2, "Bag", "Passport"))

        val copied = itemsToCopy(source, target, section = "Papers", title = null, tripId = 2)

        assertEquals(listOf("Passport", "Visa"), copied.map { it.title })
    }

    @Test
    fun `a duplicate differing only in case is still a duplicate`() {
        val target = listOf(item(9, 2, "papers", "PASSPORT"))

        val copied = itemsToCopy(source, target, section = "Papers", title = null, tripId = 2)

        assertEquals(listOf("Visa"), copied.map { it.title })
    }

    @Test
    fun `how many and what was said are no part of what makes two items the same`() {
        // Three shirts on the list and two in the file are the same line about shirts, and
        // so are "the blue one" and "any". What a copy or a file is asked is whether the
        // list already says to take them, and a second "Shirt" beside the first would be
        // answering a different question.
        val target = listOf(
            item(9, 2, "Bag", "Shirt").copy(quantityMilli = 3_000, notes = "The blue one"),
        )
        val file = listOf(item(1, 1, "Bag", "Shirt").copy(quantityMilli = 2_000, notes = "Any"))

        assertEquals(emptyList<ChecklistItem>(), onlyNew(file, target))
    }

    @Test
    fun `a source carrying the same line twice copies it once`() {
        val twice = source + item(5, 1, "Papers", "Passport")

        val copied = itemsToCopy(twice, emptyList(), section = "Papers", title = null, tripId = 2)

        assertEquals(listOf("Passport", "Visa"), copied.map { it.title })
    }

    @Test
    fun `nothing arrives done, and what was ruled out stays ruled out`() {
        val previous = listOf(
            item(1, 1, "Bag", "Charger", done = true),
            item(2, 1, "Bag", "Swimsuit", done = true, skipped = true),
        )

        val copied = itemsToCopy(previous, emptyList(), section = null, title = null, tripId = 2)

        assertTrue(copied.none { it.isDone })
        assertEquals(listOf(false, true), copied.map { it.isSkipped })
    }

    @Test
    fun `how many and what was said about it come across with the item`() {
        // Both belong to the thing rather than to the trip it was packed for: three shirts
        // last time is three shirts this time, and "the blue one" is still the blue one.
        val previous = listOf(
            item(1, 1, "Bag", "Shirt").copy(quantityMilli = 3_000, notes = "The blue one"),
            item(2, 1, "Bag", "Charger"),
        )

        val copied = itemsToCopy(previous, emptyList(), section = null, title = null, tripId = 2)

        assertEquals(listOf(3_000L, null), copied.map { it.quantityMilli })
        assertEquals(listOf("The blue one", ""), copied.map { it.notes })
    }
}
