package app.jourjour.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jourjour.data.entity.BudgetItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema an installed app may be sitting on, brought up to the current one.
 *
 * The database is the one thing in the app that cannot be rewritten with the next release:
 * what is in it belongs to somebody, and a migration that does not leave the tables as
 * Room expects fails on their phone rather than here. So each migration runs against a
 * real database of the version it starts from, with rows in it, and the result is opened
 * through Room — which compares every table, column and index against the schema this
 * build was compiled with and throws if one of them is out.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun a_trip_survives_the_budget_tables_arriving() {
        helper.createDatabase(NAME, 1).use { db ->
            db.insert("currencies", SQLiteDatabase.CONFLICT_ABORT, values("code" to "EUR", "name" to "Euro", "symbol" to "€", "decimals" to 2, "sort_order" to 0))
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values("id" to 1L, "name" to "Accommodation", "icon" to "🏨", "is_expense" to 1, "is_income" to 0, "track_quantity" to 0, "sort_order" to 0))
            db.insert("trips", SQLiteDatabase.CONFLICT_ABORT, values("id" to 1L, "name" to "Kotor", "start_date" to 20_575L, "end_date" to 20_584L, "created_at" to 0L))
        }

        helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2)

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            NAME,
        ).addMigrations(*MIGRATIONS).build()

        runBlocking {
            // The trip is still there, which is the point of migrating rather than
            // rebuilding; and the table that arrived takes a row pointing at it.
            assertEquals(listOf("Kotor"), db.tripDao().getAll().map { it.name })

            val id = db.budgetDao().insert(
                BudgetItem(
                    tripId = 1,
                    categoryId = 1,
                    title = "Ten nights",
                    quantityMilli = 10_000,
                    amountMilli = 500_000,
                    currencyCode = "EUR",
                )
            )
            assertEquals("Ten nights", db.budgetDao().getById(id)?.item?.title)
        }
        db.close()
    }

    private fun values(vararg pairs: Pair<String, Any>) = ContentValues().apply {
        pairs.forEach { (column, value) ->
            when (value) {
                is Long -> put(column, value)
                is Int -> put(column, value)
                else -> put(column, value.toString())
            }
        }
    }

    private companion object {
        /** Not the app's own database: the helper creates and deletes this one. */
        const val NAME = "migration-test.db"
    }
}
