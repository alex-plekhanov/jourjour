package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A section has no row of its own — it is the text on its items — so what is worth
 * checking is that they gather back into the same sections, in the order somebody wrote
 * them, and that a section knows when nothing in it is waiting on anybody.
 */
class ChecklistTest {

    private fun item(
        id: Long,
        section: String,
        title: String,
        done: Boolean = false,
        skipped: Boolean = false,
    ) = ChecklistItem(
        id = id,
        tripId = 1,
        section = section,
        title = title,
        isDone = done,
        isSkipped = skipped,
    )

    @Test
    fun `sections keep the order they were first written in, not the alphabet`() {
        val sections = sectionsOf(
            listOf(
                item(1, "Papers", "Passport"),
                item(2, "Bag", "Charger"),
                item(3, "Papers", "Visa"),
            )
        )

        assertEquals(listOf("Papers", "Bag"), sections.map { it.name })
        // And an item added later joins the end of its own section, not the end of the list.
        assertEquals(listOf("Passport", "Visa"), sections.first().items.map { it.title })
    }

    @Test
    fun `a section is settled once nothing in it is outstanding`() {
        val done = item(1, "Papers", "Passport", done = true)
        val ruledOut = item(2, "Papers", "Visa", skipped = true)
        val waiting = item(3, "Papers", "Insurance")

        assertTrue(sectionsOf(listOf(done, ruledOut)).single().isSettled)
        assertFalse(sectionsOf(listOf(done, ruledOut, waiting)).single().isSettled)
    }

    @Test
    fun `ruling an item out settles it without saying it was done`() {
        val ruledOut = item(1, "Papers", "Visa", skipped = true)

        assertFalse(ruledOut.isOutstanding)
        assertFalse(ruledOut.isDone)
    }

    @Test
    fun `an item can be both ticked and ruled out, and is still settled`() {
        // Ruling out an item that was already ticked must not bring it back to the list.
        val both = item(1, "Papers", "Visa", done = true, skipped = true)

        assertFalse(both.isOutstanding)
        assertTrue(sectionsOf(listOf(both)).single().isSettled)
    }

    @Test
    fun `an empty checklist has no sections`() {
        assertEquals(emptyList<ChecklistSection>(), sectionsOf(emptyList()))
    }
}
