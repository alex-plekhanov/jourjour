package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem

/**
 * Which side of the line under the finger a drag would land on.
 *
 * A list has no empty space between its lines to aim at — they touch — so every line is
 * two targets: its upper half puts what is being carried in front of it, its lower half
 * behind it.
 */
enum class DropSide { BEFORE, AFTER }

/** Where a line is to stand: in which section, and where among the trip's items. */
data class Placement(val id: Long, val section: String, val sortOrder: Int)

/**
 * What dropping the item [draggedId] against [targetId] would do.
 *
 * The item takes the target's section, which is what carrying it across a heading means:
 * a section is the text on its items and there is nowhere else in this list for one to
 * be. Its place is the target's, in front of it or behind it.
 *
 * @return the writes that would do it, empty where the drop would change nothing, or null
 *   where this is no target at all — itself, or a line this list has not got.
 */
fun itemLanding(
    items: List<ChecklistItem>,
    draggedId: Long,
    targetId: Long,
    side: DropSide,
): List<Placement>? {
    if (draggedId == targetId) return null
    val order = inReadingOrder(items)
    val dragged = order.firstOrNull { it.id == draggedId } ?: return null
    val target = order.firstOrNull { it.id == targetId } ?: return null
    val moved = order.filterNot { it.id == draggedId }.toMutableList()
    moved.add(
        moved.indexOfFirst { it.id == targetId }.on(side),
        dragged.copy(section = target.section),
    )
    return placements(order, moved)
}

/**
 * What dropping the section [dragged] against [target] would do.
 *
 * A section moves as its items, there being nothing else to it: the whole run of them
 * goes in front of the target's run or behind it, in the order it is already in. Whether
 * it is folded away makes no difference — a folded section is a section.
 */
fun sectionLanding(
    items: List<ChecklistItem>,
    dragged: String,
    target: String,
    side: DropSide,
): List<Placement>? {
    if (dragged == target) return null
    val sections = sectionsOf(items)
    val carried = sections.firstOrNull { it.name == dragged } ?: return null
    if (sections.none { it.name == target }) return null
    val moved = sections.filterNot { it.name == dragged }.toMutableList()
    moved.add(moved.indexOfFirst { it.name == target }.on(side), carried)
    return placements(sections.flatMap { it.items }, moved.flatMap { it.items })
}

/** In front of the target is its own place; behind it is the next one along. */
private fun Int.on(side: DropSide): Int = if (side == DropSide.AFTER) this + 1 else this

/**
 * The writes that take the list from the order it is in to the order it is to be in.
 *
 * Every line is given its place as a plain count from the top. The numbers belong to the
 * list and nothing outside it reads them, so renumbering the lot is less to think about
 * than finding room between two of them — and only the lines that come out differently
 * are written, a list dragged by one line being a list most of which has not moved.
 */
private fun placements(
    before: List<ChecklistItem>,
    after: List<ChecklistItem>,
): List<Placement> {
    val was = before.associateBy { it.id }
    return after.mapIndexedNotNull { at, item ->
        val original = was[item.id] ?: return@mapIndexedNotNull null
        Placement(item.id, item.section, at)
            .takeIf { original.sortOrder != at || original.section != item.section }
    }
}
