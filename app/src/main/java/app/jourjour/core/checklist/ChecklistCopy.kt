package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem
import java.util.Locale

/**
 * What a copy would add to a trip, having left out what is already there.
 *
 * @param section the one section to take, or null for all of them.
 * @param title the one item to take out of that section, or null for all of it.
 * @param tripId the trip the copies belong to.
 */
fun itemsToCopy(
    source: List<ChecklistItem>,
    target: List<ChecklistItem>,
    section: String?,
    title: String?,
    tripId: Long,
): List<ChecklistItem> {
    val wanted = source
        .filter { section == null || it.section == section }
        .filter { title == null || it.title == title }
    return onlyNew(wanted, target).map { item ->
        ChecklistItem(
            tripId = tripId,
            section = item.section,
            title = item.title,
            // Nothing arrives done. What was packed for the last trip is exactly what has
            // not been packed for this one; carrying the ticks across would hand over a
            // list that claims the work is finished.
            //
            // A file being read in is different: it says what it says, and a person who
            // wrote "done" in a column meant it. See where this is called from.
            isDone = false,
            // How many does come across. Three shirts last time is three shirts this
            // time: it belongs to the item rather than to the trip that was packed.
            quantityMilli = item.quantityMilli,
            // Ruled out does come across either way. It is a standing judgement about the
            // item — no swimming things in November — and a list copied without it would
            // ask the same questions again every time.
            isSkipped = item.isSkipped,
        )
    }
}

/**
 * The candidates a trip has not got already, in the order they came.
 *
 * Two items of the same wording in the same section are one thing to a person, whichever
 * trip or file they were written on. The set of what is present grows as this goes, so a
 * source carrying the same line twice contributes it once.
 */
fun onlyNew(
    candidates: List<ChecklistItem>,
    existing: List<ChecklistItem>,
): List<ChecklistItem> {
    val present = existing.mapTo(mutableSetOf()) { it.key() }
    return candidates.filter { present.add(it.key()) }
}

/**
 * How two items are told apart: the section and the wording, neither minding case.
 *
 * "Passport" and "passport" are the same thing to whoever wrote them, and a copy that
 * added the second beside the first would be answering a question about letters rather
 * than about packing.
 */
private fun ChecklistItem.key(): Pair<String, String> =
    section.lowercase(Locale.ROOT) to title.lowercase(Locale.ROOT)
