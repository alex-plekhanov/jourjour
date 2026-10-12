package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A checklist is read in the order somebody put it in, so the order is a thing they can
 * put right: a line against another line, a section against another section.
 *
 * What is worth checking is that the list comes out in the order the drop promised, that a
 * line carried across a heading changes section, that a section takes its items with it,
 * and that nothing is written where nothing moved.
 */
class ReorderingTest {

    private fun item(id: Long, section: String, title: String, place: Int = 0) = ChecklistItem(
        id = id,
        tripId = 1,
        section = section,
        title = title,
        sortOrder = place,
    )

    /** Papers: passport, visa. Bag: charger, socks. In that order, numbered from the top. */
    private val list = listOf(
        item(1, "Papers", "Passport", place = 0),
        item(2, "Papers", "Visa", place = 1),
        item(3, "Bag", "Charger", place = 2),
        item(4, "Bag", "Socks", place = 3),
    )

    /** The list as the screen would read it after the writes have been applied. */
    private fun List<ChecklistItem>.after(placements: List<Placement>?): List<Pair<String, String>> {
        val writes = checkNotNull(placements).associateBy { it.id }
        val applied = map { item ->
            writes[item.id]?.let { item.copy(section = it.section, sortOrder = it.sortOrder) } ?: item
        }
        return sectionsOf(applied.sortedWith(compareBy({ it.sortOrder }, { it.id })))
            .flatMap { section -> section.items.map { section.name to it.title } }
    }

    @Test
    fun `an item dropped in front of another stands in front of it`() {
        val moved = itemLanding(list, draggedId = 4, targetId = 3, side = DropSide.BEFORE)

        assertEquals(
            listOf(
                "Papers" to "Passport",
                "Papers" to "Visa",
                "Bag" to "Socks",
                "Bag" to "Charger",
            ),
            list.after(moved),
        )
    }

    @Test
    fun `an item dropped behind another stands behind it`() {
        val moved = itemLanding(list, draggedId = 1, targetId = 2, side = DropSide.AFTER)

        assertEquals(
            listOf(
                "Papers" to "Visa",
                "Papers" to "Passport",
                "Bag" to "Charger",
                "Bag" to "Socks",
            ),
            list.after(moved),
        )
    }

    @Test
    fun `an item carried across a heading takes the section it lands in`() {
        // Dragging a line into another section is the only way a list has of saying it
        // belongs there, there being no row for a section to drop it on.
        val moved = itemLanding(list, draggedId = 4, targetId = 1, side = DropSide.BEFORE)

        assertEquals(
            listOf(
                "Papers" to "Socks",
                "Papers" to "Passport",
                "Papers" to "Visa",
                "Bag" to "Charger",
            ),
            list.after(moved),
        )
    }

    @Test
    fun `a section left by its last item has stopped existing`() {
        val one = listOf(
            item(1, "Papers", "Passport", place = 0),
            item(2, "Bag", "Charger", place = 1),
        )

        val moved = itemLanding(one, draggedId = 2, targetId = 1, side = DropSide.AFTER)

        assertEquals(listOf("Papers" to "Passport", "Papers" to "Charger"), one.after(moved))
    }

    @Test
    fun `a section dropped in front of another takes its items with it`() {
        val moved = sectionLanding(list, dragged = "Bag", target = "Papers", side = DropSide.BEFORE)

        assertEquals(
            listOf(
                "Bag" to "Charger",
                "Bag" to "Socks",
                "Papers" to "Passport",
                "Papers" to "Visa",
            ),
            list.after(moved),
        )
    }

    @Test
    fun `a section dropped behind another goes behind the whole of it`() {
        val moved = sectionLanding(list, dragged = "Papers", target = "Bag", side = DropSide.AFTER)

        assertEquals(
            listOf(
                "Bag" to "Charger",
                "Bag" to "Socks",
                "Papers" to "Passport",
                "Papers" to "Visa",
            ),
            list.after(moved),
        )
    }

    @Test
    fun `a section carries the items it was written in two sittings as`() {
        // The rows of a section need not be next to each other — an item added to a
        // section written earlier lands at the end of the list — and moving the section
        // gathers them.
        val scattered = listOf(
            item(1, "Papers", "Passport", place = 0),
            item(2, "Bag", "Charger", place = 1),
            item(3, "Papers", "Visa", place = 2),
        )

        val moved = sectionLanding(
            scattered,
            dragged = "Bag",
            target = "Papers",
            side = DropSide.BEFORE,
        )

        assertEquals(
            listOf("Bag" to "Charger", "Papers" to "Passport", "Papers" to "Visa"),
            scattered.after(moved),
        )
    }

    @Test
    fun `nothing is a target for itself`() {
        assertNull(itemLanding(list, draggedId = 1, targetId = 1, side = DropSide.BEFORE))
        assertNull(itemLanding(list, draggedId = 1, targetId = 1, side = DropSide.AFTER))
        assertNull(sectionLanding(list, dragged = "Bag", target = "Bag", side = DropSide.BEFORE))
    }

    @Test
    fun `a line this list has not got is no target at all`() {
        assertNull(itemLanding(list, draggedId = 1, targetId = 99, side = DropSide.BEFORE))
        assertNull(itemLanding(list, draggedId = 99, targetId = 1, side = DropSide.BEFORE))
        assertNull(sectionLanding(list, dragged = "Papers", target = "Boot", side = DropSide.AFTER))
    }

    @Test
    fun `letting go where the thing already is writes nothing`() {
        // Both ways of saying the same place: in front of what follows it, behind what it
        // follows. A drag that changed its mind ends here, and it must not cost a write.
        assertEquals(
            emptyList<Placement>(),
            itemLanding(list, draggedId = 1, targetId = 2, side = DropSide.BEFORE),
        )
        assertEquals(
            emptyList<Placement>(),
            itemLanding(list, draggedId = 2, targetId = 1, side = DropSide.AFTER),
        )
        assertEquals(
            emptyList<Placement>(),
            sectionLanding(list, dragged = "Bag", target = "Papers", side = DropSide.AFTER),
        )
    }

    @Test
    fun `only the lines that moved are written`() {
        val moved = checkNotNull(
            itemLanding(list, draggedId = 3, targetId = 4, side = DropSide.AFTER)
        )

        // The papers above them are where they were, and nobody has to hear about it.
        assertEquals(listOf(3L, 4L), moved.map { it.id }.sorted())
        assertTrue(moved.all { it.section == "Bag" })
    }

    @Test
    fun `a list nobody has dragged is given its numbers by the first drop`() {
        // Every line of it carries nought, the order having come from the ids until now,
        // so the first drop writes out most of the list rather than one line of it — all
        // but the line that happens to land on the number it was already carrying.
        val fresh = list.map { it.copy(sortOrder = 0) }

        val moved = checkNotNull(
            sectionLanding(fresh, dragged = "Bag", target = "Papers", side = DropSide.BEFORE)
        )

        assertEquals(listOf(1L to 2, 2L to 3, 4L to 1), moved.sortedBy { it.id }.map { it.id to it.sortOrder })
        assertEquals(
            listOf(
                "Bag" to "Charger",
                "Bag" to "Socks",
                "Papers" to "Passport",
                "Papers" to "Visa",
            ),
            fresh.after(moved),
        )
    }
}
