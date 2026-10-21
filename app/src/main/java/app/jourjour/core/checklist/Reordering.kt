package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem

/**
 * Which part of the line under the finger a drag would land on.
 *
 * A list has no empty space between its lines to aim at — they touch — so a line of the
 * same kind as the thing carried is two targets: its upper half puts it in front, its lower
 * half behind. A heading under an item is one target, the whole of it: [INTO] that section.
 */
enum class DropSide { BEFORE, AFTER, INTO }

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
    if (draggedId == targetId || side == DropSide.INTO) return null
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
    if (dragged == target || side == DropSide.INTO) return null
    val sections = sectionsOf(items)
    val carried = sections.firstOrNull { it.name == dragged } ?: return null
    if (sections.none { it.name == target }) return null
    val moved = sections.filterNot { it.name == dragged }.toMutableList()
    moved.add(moved.indexOfFirst { it.name == target }.on(side), carried)
    return placements(sections.flatMap { it.items }, moved.flatMap { it.items })
}

/**
 * What dropping the item [draggedId] on the heading of [section] would do: put it at the
 * end of that section, the place an item added to it would take.
 *
 * The heading is the one target a folded section has — its items are not on the screen to
 * be dropped against — and the obvious one for a section that is open but long, whose
 * last item is a screen away. Its own section's heading is a target too, and takes the
 * item to the bottom of it.
 *
 * @return the writes, empty where the item is the last of that section already, or null
 *   where there is no such section or no such item.
 */
fun itemIntoSection(
    items: List<ChecklistItem>,
    draggedId: Long,
    section: String,
): List<Placement>? {
    val order = inReadingOrder(items)
    val dragged = order.firstOrNull { it.id == draggedId } ?: return null
    if (order.none { it.section == section }) return null
    val moved = order.filterNot { it.id == draggedId }.toMutableList()
    // Behind the last of the others, or — where it was the only one — back where it was,
    // its section being nothing but itself.
    val last = moved.indexOfLast { it.section == section }
    if (last < 0) return emptyList()
    moved.add(last + 1, dragged.copy(section = section))
    return placements(order, moved)
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
