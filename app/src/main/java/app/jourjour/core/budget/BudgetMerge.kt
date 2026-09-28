package app.jourjour.core.budget

import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import java.util.Locale

/**
 * The lines a budget has not got already, in the order they came.
 *
 * A budget names a thing once: "Accommodation — Hotel" is written for the trip, not for
 * each hotel, so a line of the same name under the same category is the line that is
 * already there rather than a second one. A nameless line is a line of its own by the same
 * rule — it matches the nameless line of its category and nothing else.
 *
 * The set of what is present grows as this goes, so a file carrying the same line twice
 * contributes it once.
 */
fun onlyNew(
    candidates: List<BudgetItem>,
    existing: List<BudgetItemDetails>,
): List<BudgetItem> {
    val present = existing.mapTo(mutableSetOf()) { it.item.key() }
    return candidates.filter { present.add(it.key()) }
}

/**
 * How two lines are told apart: the category and the name, the name not minding case.
 *
 * "Hotel" and "hotel" are the same thing to whoever wrote them, and an import that added
 * the second beside the first would be answering a question about letters rather than
 * about money.
 */
private fun BudgetItem.key(): Pair<Long, String> =
    categoryId to title.trim().lowercase(Locale.ROOT)
