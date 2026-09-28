package app.jourjour.core.budget

import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a file being read in adds to a budget that already exists. A budget names a thing
 * once, so the category and the name are what a line is known by — a nameless line
 * included, since that is a line like any other.
 */
class BudgetMergeTest {

    private val hotels = Category(id = 1, name = "Accommodation", icon = "🏨", sortOrder = 1)
    private val flights = Category(id = 2, name = "Getting there", icon = "✈️", sortOrder = 2)
    private val usd = Currency("USD", "US Dollar", "$", decimals = 2)

    @Test
    fun a_line_the_budget_already_carries_is_passed_over() {
        val fresh = onlyNew(
            candidates = listOf(
                item(category = hotels, title = "Hotel", amountMilli = 900_000),
                item(category = hotels, title = "Camping", amountMilli = 100_000),
            ),
            existing = listOf(existing(category = hotels, title = "Hotel", amountMilli = 500_000)),
        )

        // The figure in the file does not overwrite the one on the screen: the line is
        // there, and what it says is what somebody last said on the form.
        assertEquals(listOf("Camping"), fresh.map { it.title })
    }

    @Test
    fun the_same_name_under_another_category_is_another_line() {
        val fresh = onlyNew(
            candidates = listOf(item(category = flights, title = "Hotel", amountMilli = 100_000)),
            existing = listOf(existing(category = hotels, title = "Hotel", amountMilli = 500_000)),
        )

        assertEquals(1, fresh.size)
    }

    @Test
    fun a_nameless_line_matches_the_nameless_line_of_its_category() {
        val fresh = onlyNew(
            candidates = listOf(
                item(category = hotels, amountMilli = 100_000),
                item(category = flights, amountMilli = 100_000),
            ),
            existing = listOf(existing(category = hotels, amountMilli = 500_000)),
        )

        assertEquals(listOf(flights.id), fresh.map { it.categoryId })
    }

    @Test
    fun a_name_written_two_ways_is_one_name() {
        val fresh = onlyNew(
            candidates = listOf(item(category = hotels, title = " hotel ", amountMilli = 100_000)),
            existing = listOf(existing(category = hotels, title = "Hotel", amountMilli = 500_000)),
        )

        assertEquals(emptyList<BudgetItem>(), fresh)
    }

    @Test
    fun a_file_carrying_the_same_line_twice_contributes_it_once() {
        val fresh = onlyNew(
            candidates = listOf(
                item(category = hotels, title = "Hotel", amountMilli = 100_000),
                item(category = hotels, title = "Hotel", amountMilli = 200_000),
            ),
            existing = emptyList(),
        )

        assertEquals(listOf(100_000L), fresh.map { it.amountMilli })
    }

    private fun item(
        category: Category,
        title: String = "",
        amountMilli: Long,
    ) = BudgetItem(
        tripId = 1,
        categoryId = category.id,
        title = title,
        amountMilli = amountMilli,
        currencyCode = usd.code,
    )

    private fun existing(
        category: Category,
        title: String = "",
        amountMilli: Long,
    ) = BudgetItemDetails(
        item = item(category, title, amountMilli).copy(id = 1),
        category = category,
        currency = usd,
    )
}
