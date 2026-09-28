package app.jourjour.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWallet
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The "is this value in use" checks are hand-written SQL, and nothing in the interface
 * reveals them until someone tries to delete a value that is taken — hence these tests.
 */
@RunWith(AndroidJUnit4::class)
class UsageQueriesTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        // No preloading: the dictionaries are filled here so the references are controlled.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun a_currency_is_free_while_nothing_points_at_it() = runBlocking {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        assertFalse(db.currencyDao().isUsed("USD"))
    }

    @Test
    fun a_currency_is_held_by_a_wallet() = runBlocking {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        db.currencyDao().insert(Currency("JPY", "Japanese Yen", "¥", decimals = 0))
        val paymentMethodId = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        val tripId = db.tripDao().insert(newTrip())

        // A trip of its own holds no currency any more: only its wallets do.
        assertFalse(db.currencyDao().isUsed("USD"))
        assertFalse(db.currencyDao().isUsed("JPY"))

        db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = paymentMethodId, currencyCode = "USD")
        )
        assertTrue(db.currencyDao().isUsed("USD"))
        assertFalse(db.currencyDao().isUsed("JPY"))
    }

    @Test
    fun a_payment_method_is_held_by_the_wallets_made_with_it() = runBlocking {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        val paymentMethodId = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        val tripId = db.tripDao().insert(newTrip())

        assertFalse(db.tripWalletDao().existsForPaymentMethod(paymentMethodId))
        db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = paymentMethodId, currencyCode = "USD")
        )
        assertTrue(db.tripWalletDao().existsForPaymentMethod(paymentMethodId))
    }

    @Test
    fun a_category_and_a_wallet_are_held_by_an_entry() = runBlocking {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        val tripId = db.tripDao().insert(newTrip())
        val categoryId = db.categoryDao().insert(Category(name = "Dining", icon = "🍽"))
        val paymentMethodId = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        val walletId = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = paymentMethodId, currencyCode = "USD")
        )

        assertFalse(db.entryDao().existsForCategory(categoryId))
        assertFalse(db.entryDao().existsForWallet(walletId))

        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = categoryId,
                dateTime = LocalDateTime.of(2026, 4, 3, 12, 30),
                expenseWalletId = walletId,
                expenseAmountMilli = 1_250_500,
                expenseCurrencyCode = "USD",
            )
        )

        assertTrue(db.entryDao().existsForCategory(categoryId))
        assertTrue(db.entryDao().existsForWallet(walletId))
        assertTrue(db.currencyDao().isUsed("USD"))
    }

    @Test
    fun the_income_half_holds_its_wallet_too() = runBlocking {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        db.currencyDao().insert(Currency("RUB", "Russian Ruble", "₽"))
        val tripId = db.tripDao().insert(newTrip())
        val categoryId = db.categoryDao().insert(
            Category(name = "Exchange", icon = "💱", isExpense = true, isIncome = true)
        )
        val method = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        val usdWallet = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = method, currencyCode = "USD")
        )
        val rubWallet = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = method, currencyCode = "RUB")
        )

        // A currency exchange: out of one wallet and into another, all in one entry.
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = categoryId,
                dateTime = LocalDateTime.of(2026, 4, 4, 10, 0),
                expenseWalletId = usdWallet,
                expenseAmountMilli = 100_000,
                expenseCurrencyCode = "USD",
                incomeWalletId = rubWallet,
                incomeAmountMilli = 9_000_000,
                incomeCurrencyCode = "RUB",
            )
        )

        assertTrue(db.entryDao().existsForWallet(usdWallet))
        assertTrue(db.entryDao().existsForWallet(rubWallet))
        assertTrue(db.currencyDao().isUsed("RUB"))
    }

    @Test
    fun the_favourites_of_a_category_are_replaced_wholesale() = runBlocking {
        val categoryId = db.categoryDao().insert(Category(name = "Fuel", icon = "⛽"))
        db.categoryFavoriteDao().replaceForCategory(categoryId, favorites("Petrol 92", "Petrol 95"))
        db.categoryFavoriteDao().replaceForCategory(categoryId, favorites("Petrol 95", "Diesel"))

        // Order comes from the position in the list, not from the alphabet.
        assertEquals(
            listOf("Petrol 95", "Diesel"),
            db.categoryFavoriteDao().getByCategory(categoryId).map { it.name },
        )
    }

    /** The category and the order are filled in by the replace itself. */
    private fun favorites(vararg names: String) =
        names.map { CategoryFavorite(categoryId = 0, name = it) }

    private fun newTrip() = Trip(
        name = "Test",
        startDate = LocalDate.of(2026, 4, 3),
        endDate = LocalDate.of(2026, 4, 17),
    )
}
