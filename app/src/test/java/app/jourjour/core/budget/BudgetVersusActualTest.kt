package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.ScheduleEvent
import app.jourjour.data.entity.ScheduleEventDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * The budget read against the spending. What is checked is the arithmetic of the third
 * column — what is left — in the cases where it is not simply one figure less another: a
 * category closed by hand, a category overspent, and one nobody planned for at all.
 */
class BudgetVersusActualTest {

    private val rub = Currency("RUB", "Russian Ruble", "₽", decimals = 2, sortOrder = 1)
    private val usd = Currency("USD", "US Dollar", "$", decimals = 2, sortOrder = 2)

    private val cash = PaymentMethod(id = 1, name = "Cash", icon = "💵", fixedCurrency = true)

    private val hotels = Category(id = 1, name = "Accommodation", icon = "🏨", sortOrder = 1)
    private val dining = Category(id = 2, name = "Restaurants", icon = "🍽", sortOrder = 2)
    private val refunds = Category(
        id = 3,
        name = "Refunds",
        icon = "💰",
        sortOrder = 3,
        isExpense = false,
        isIncome = true,
    )

    /** 1 USD = 100 RUB, which keeps the arithmetic in this file readable. */
    private val usdRate = TripRate(
        id = 1,
        tripId = 1,
        fromCurrencyCode = "USD",
        toCurrencyCode = "RUB",
        rateMilli = 100_000,
        multiplier = 1,
    )

    @Test
    fun what_is_left_is_what_was_planned_less_what_has_gone() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(entry(hotels, expenseMilli = 20_000_000, currency = rub)),
        )

        val row = (standing as BudgetVersusActual.Ready).categories.single()
        assertEquals(50_000_000L, row.planned?.milli)
        assertEquals(20_000_000L, row.spent.milli)
        assertEquals(30_000_000L, row.remaining?.milli)
    }

    @Test
    fun both_sides_are_brought_to_the_base_currency() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = usd)),
            entries = listOf(entry(hotels, expenseMilli = 200_000, currency = usd)),
        )

        // Five hundred dollars planned at a hundred roubles apiece, two hundred of it gone.
        val row = (standing as BudgetVersusActual.Ready).categories.single()
        assertEquals(50_000_000L, row.planned?.milli)
        assertEquals(20_000_000L, row.spent.milli)
        assertEquals(30_000_000L, row.remaining?.milli)
    }

    @Test
    fun a_closed_category_has_nothing_left_however_the_figures_came_out() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(entry(hotels, expenseMilli = 20_000_000, currency = rub)),
            closed = setOf(hotels.id),
        )

        val row = (standing as BudgetVersusActual.Ready).categories.single()
        assertEquals(0L, row.remaining?.milli)
        assertEquals(0L, standing.remaining.milli)
    }

    @Test
    fun spending_over_a_plan_leaves_nothing_rather_than_less_than_nothing() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(entry(hotels, expenseMilli = 70_000_000, currency = rub)),
        )

        val row = (standing as BudgetVersusActual.Ready).categories.single()
        assertEquals(0L, row.remaining?.milli)
        // The overspending is still there to be seen in the middle column.
        assertEquals(70_000_000L, row.spent.milli)
    }

    @Test
    fun a_category_nobody_planned_for_is_shown_with_what_went_on_it() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(entry(dining, expenseMilli = 3_000_000, currency = rub)),
        )

        val rows = (standing as BudgetVersusActual.Ready).categories
        val unplanned = rows.single { it.category.id == dining.id }
        assertNull(unplanned.planned)
        assertEquals(3_000_000L, unplanned.spent.milli)
        assertNull(unplanned.remaining)
        // It counts against what the trip has cost, but leaves the plan where it was.
        assertEquals(50_000_000L, standing.planned.milli)
        assertEquals(3_000_000L, standing.spent.milli)
        assertEquals(50_000_000L, standing.remaining.milli)
    }

    @Test
    fun money_that_came_back_takes_away_from_what_the_trip_has_cost() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(
                entry(hotels, expenseMilli = 20_000_000, currency = rub),
                entry(refunds, incomeMilli = 5_000_000, currency = rub, id = 2),
            ),
        )

        val ready = standing as BudgetVersusActual.Ready
        assertEquals(-5_000_000L, ready.categories.single { it.category.id == refunds.id }.spent.milli)
        assertEquals(15_000_000L, ready.spent.milli)
        // What is left of the plan is untouched by it: nobody budgeted for a refund.
        assertEquals(30_000_000L, ready.remaining.milli)
    }

    @Test
    fun where_the_trip_is_heading_is_what_has_gone_plus_what_is_still_to() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(
                entry(hotels, expenseMilli = 20_000_000, currency = rub),
                // Nobody planned for this one, and it is money the trip has cost all the same.
                entry(dining, expenseMilli = 3_000_000, currency = rub, id = 2),
            ),
        )

        // Thirty thousand of the plan is still to go, twenty-three thousand has gone.
        assertEquals(53_000_000L, (standing as BudgetVersusActual.Ready).expected.milli)
    }

    @Test
    fun closing_a_category_lowers_where_the_trip_is_heading() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 50_000_000, currency = rub)),
            entries = listOf(entry(hotels, expenseMilli = 20_000_000, currency = rub)),
            closed = setOf(hotels.id),
        )

        // The thirty thousand that will now never be spent is not coming out of anybody's
        // pocket, so what the trip is heading for is the twenty thousand already gone.
        assertEquals(20_000_000L, (standing as BudgetVersusActual.Ready).expected.milli)
    }

    @Test
    fun a_currency_with_no_rate_stops_the_whole_reading() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = usd)),
            entries = emptyList(),
            rates = emptyList(),
        )

        assertEquals(listOf("USD"), (standing as BudgetVersusActual.RatesMissing).codes)
    }

    @Test
    fun no_base_currency_is_said_rather_than_passed_over() {
        val standing = BudgetVersusActual.of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = rub)),
            entries = emptyList(),
            schedule = emptyList(),
            base = null,
            rates = emptyList(),
            closed = emptySet(),
            planningClosed = emptySet(),
        )

        assertEquals(BudgetVersusActual.NoBaseCurrency, standing)
    }

    @Test
    fun a_trip_with_neither_a_budget_nor_an_entry_has_nothing_to_read() {
        assertNull(of(items = emptyList(), entries = emptyList()))
    }

    @Test
    fun a_budgeted_category_reads_off_the_budget_while_its_planning_is_open() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = rub)),
            entries = emptyList(),
            schedule = listOf(booked(hotels, amountMilli = 430_000, currency = rub)),
        ) as BudgetVersusActual.Ready

        // Ten nights at fifty is still the answer while the nights are being booked.
        assertEquals(Money(500_000), standing.categories.single().planned)
        assertFalse(standing.categories.single().fromSchedule)
    }

    @Test
    fun and_off_the_schedule_once_the_planning_is_closed() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = rub)),
            entries = emptyList(),
            schedule = listOf(booked(hotels, amountMilli = 430_000, currency = rub)),
            planningClosed = setOf(hotels.id),
        ) as BudgetVersusActual.Ready

        // The nights are booked and came to less than the guess; the guess is done with.
        assertEquals(Money(430_000), standing.categories.single().planned)
        assertTrue(standing.categories.single().fromSchedule)
    }

    @Test
    fun a_category_the_budget_never_had_is_read_off_the_schedule_all_the_same() {
        val standing = of(
            items = emptyList(),
            entries = emptyList(),
            schedule = listOf(booked(hotels, amountMilli = 430_000, currency = rub)),
        ) as BudgetVersusActual.Ready

        // Nothing else to read it off, and a plan nobody can see is a plan nobody can use.
        assertEquals(Money(430_000), standing.categories.single().planned)
        assertEquals(Money(430_000), standing.planned)
    }

    @Test
    fun closing_the_planning_of_a_category_with_nothing_booked_plans_nothing() {
        val standing = of(
            items = listOf(budget(hotels, amountMilli = 500_000, currency = rub)),
            entries = emptyList(),
            planningClosed = setOf(hotels.id),
        ) as BudgetVersusActual.Ready

        // "This is the whole of it" said over an empty schedule is a figure, not an
        // absence: nothing is planned, so nothing is left.
        assertEquals(Money.ZERO, standing.categories.single().planned)
        assertEquals(Money.ZERO, standing.remaining)
    }

    private fun of(
        items: List<BudgetItemDetails>,
        entries: List<EntryDetails>,
        schedule: List<ScheduleEventDetails> = emptyList(),
        rates: List<TripRate> = listOf(usdRate),
        closed: Set<Long> = emptySet(),
        planningClosed: Set<Long> = emptySet(),
    ) = BudgetVersusActual.of(items, entries, schedule, rub, rates, closed, planningClosed)

    private fun booked(
        category: Category,
        amountMilli: Long,
        currency: Currency,
        id: Long = 1,
    ) = ScheduleEventDetails(
        event = ScheduleEvent(
            id = id,
            tripId = 1,
            title = "Night $id",
            startsAt = 1_790_000_000,
            durationMinutes = 60,
            categoryId = category.id,
            amountMilli = amountMilli,
            currencyCode = currency.code,
        ),
        category = category,
        currency = currency,
    )

    private fun budget(
        category: Category,
        amountMilli: Long,
        currency: Currency,
        id: Long = 1,
    ) = BudgetItemDetails(
        item = BudgetItem(
            id = id,
            tripId = 1,
            categoryId = category.id,
            amountMilli = amountMilli,
            currencyCode = currency.code,
        ),
        category = category,
        currency = currency,
    )

    private fun entry(
        category: Category,
        currency: Currency,
        expenseMilli: Long? = null,
        incomeMilli: Long? = null,
        id: Long = 1,
    ): EntryDetails {
        val wallet = TripWalletDetails(
            wallet = TripWallet(
                id = 1,
                tripId = 1,
                paymentMethodId = cash.id,
                currencyCode = currency.code,
            ),
            paymentMethod = cash,
            currency = currency,
        )
        return EntryDetails(
            entry = Entry(
                id = id,
                tripId = 1,
                categoryId = category.id,
                dateTime = LocalDateTime.of(2026, 5, 1, 12, 0),
                expenseWalletId = wallet.wallet.id.takeIf { expenseMilli != null },
                expenseAmountMilli = expenseMilli,
                expenseCurrencyCode = currency.code.takeIf { expenseMilli != null },
                incomeWalletId = wallet.wallet.id.takeIf { incomeMilli != null },
                incomeAmountMilli = incomeMilli,
                incomeCurrencyCode = currency.code.takeIf { incomeMilli != null },
            ),
            category = category,
            expenseWallet = wallet.takeIf { expenseMilli != null },
            incomeWallet = wallet.takeIf { incomeMilli != null },
            expenseCurrency = currency.takeIf { expenseMilli != null },
            incomeCurrency = currency.takeIf { incomeMilli != null },
        )
    }
}
