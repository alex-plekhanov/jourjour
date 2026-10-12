package app.jourjour.core.checklist

import app.jourjour.data.entity.ChecklistItem

/**
 * A section of a checklist: a name and the items carrying it.
 *
 * It exists only as long as something carries the name — there is no row for a section
 * anywhere — so a section can never be empty, and [isSettled] never has to answer for an
 * empty one.
 */
data class ChecklistSection(
    val name: String,
    val items: List<ChecklistItem>,
) {
    /**
     * Nothing here is waiting on anybody: every item is either ticked or ruled out for
     * this trip. The heading says so by being struck through, and a settled section is
     * what the screen opens with folded away.
     */
    val isSettled: Boolean get() = items.none { it.isOutstanding }
}

/**
 * Gathers a trip's items into their sections.
 *
 * The order is the order the items come in, which is the order they have been dragged into
 * and, failing that, the order they were made: a section takes the place of its first
 * item, and an item added later joins the end of its own section rather than the end of
 * the list. Nothing is sorted — a checklist is read in the order somebody put it in, and
 * alphabetical order would scatter "passport, visa, insurance" into three unrelated places.
 *
 * Items of one section need not arrive together, since an item added to a section that was
 * written earlier lands at the end of the whole list: they are gathered back under the
 * first of them, which is what keeps the section where it was.
 */
fun sectionsOf(items: List<ChecklistItem>): List<ChecklistSection> =
    items.groupBy { it.section }.map { (name, grouped) -> ChecklistSection(name, grouped) }

/**
 * The same items, flat, in the order the screen reads them: each section in its place and
 * its items under it.
 *
 * Which is not quite the order the rows come in — a section written in two sittings has
 * its items gathered back together — and it is what anything meant to match the screen
 * has to go by: where a drop would land, and what order a table is written in. A file
 * whose lines came in the order the rows happen to sit in would interleave two sections
 * that the screen shows one after the other.
 */
fun inReadingOrder(items: List<ChecklistItem>): List<ChecklistItem> =
    sectionsOf(items).flatMap { it.items }
