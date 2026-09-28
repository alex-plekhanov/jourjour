package app.jourjour.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that the dictionaries really are filled in. Two ways of getting this wrong have
 * already happened: filling them from a background coroutine, so the first query came back
 * empty; and filling them from onDestructiveMigration, which runs while no table exists.
 */
@RunWith(AndroidJUnit4::class)
class DefaultDataTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataCallback(context))
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun the_dictionaries_are_filled_by_the_first_query() = runBlocking {
        assertTrue(db.currencyDao().getAll().isNotEmpty())
        assertTrue(db.paymentMethodDao().getAll().isNotEmpty())
    }

    @Test
    fun the_number_of_decimals_comes_from_icu() = runBlocking {
        assertEquals(2, db.currencyDao().getByCode("USD")?.decimals)
        assertEquals(0, db.currencyDao().getByCode("JPY")?.decimals)
        // Three-decimal currencies are the reason the internal scale is thousandths.
        assertEquals(3, db.currencyDao().getByCode("KWD")?.decimals)
        // ICU answers with what people actually pay rather than what ISO 4217 lists: the
        // para has been out of circulation for years, and nobody enters 0.37 of one.
        assertEquals(0, db.currencyDao().getByCode("RSD")?.decimals)
    }

    @Test
    fun preloaded_rows_point_at_a_string_resource() = runBlocking {
        val usd = db.currencyDao().getByCode("USD")
        assertNotNull(usd)
        assertEquals("currency_usd", usd?.nameKey)
    }

    @Test
    fun cash_is_pinned_to_its_wallet_and_a_card_is_not() = runBlocking {
        // Banknotes cannot be handed over in a currency they are not; a card converts.
        val methods = db.paymentMethodDao().getAll().associateBy { it.nameKey }
        assertEquals(true, methods["payment_method_cash"]?.fixedCurrency)
        assertEquals(false, methods["payment_method_card"]?.fixedCurrency)
    }

    @Test
    fun the_fuel_category_counts_quantity_and_has_favourites() = runBlocking {
        val fuel = db.categoryDao().getAll().first { it.nameKey == "category_fuel" }
        assertTrue(fuel.trackQuantity)

        val favorites = db.categoryFavoriteDao().getByCategory(fuel.id)
        assertTrue(favorites.isNotEmpty())
    }

    @Test
    fun the_currency_exchange_category_has_both_halves() = runBlocking {
        val exchange = db.categoryDao().getAll()
            .first { it.nameKey == "category_currency_exchange" }
        assertTrue(exchange.isExpense)
        assertTrue(exchange.isIncome)
    }

}
