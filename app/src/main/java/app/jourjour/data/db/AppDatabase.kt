package app.jourjour.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import app.jourjour.data.dao.BalanceSnapshotDao
import app.jourjour.data.dao.CategoryDao
import app.jourjour.data.dao.CategoryFavoriteDao
import app.jourjour.data.dao.ChecklistDao
import app.jourjour.data.dao.CurrencyDao
import app.jourjour.data.dao.EntryDao
import app.jourjour.data.dao.IconPresetDao
import app.jourjour.data.dao.PaymentMethodDao
import app.jourjour.data.dao.TripDao
import app.jourjour.data.dao.TripRateDao
import app.jourjour.data.dao.TripWalletDao
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.ChecklistItem
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.IconPreset
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import app.jourjour.di.DatabaseModule

/**
 * Hilt is the sole owner of the database, see [DatabaseModule].
 * There is deliberately no static factory: screens and repositories receive DAOs by
 * injection, so nobody can open a second connection behind the app's back.
 */
@Database(
    entities = [
        Currency::class,
        PaymentMethod::class,
        Category::class,
        CategoryFavorite::class,
        Trip::class,
        TripWallet::class,
        TripRate::class,
        ChecklistItem::class,
        Entry::class,
        BalanceSnapshot::class,
        IconPreset::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun currencyDao(): CurrencyDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryFavoriteDao(): CategoryFavoriteDao
    abstract fun tripDao(): TripDao
    abstract fun tripWalletDao(): TripWalletDao
    abstract fun tripRateDao(): TripRateDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun entryDao(): EntryDao
    abstract fun balanceSnapshotDao(): BalanceSnapshotDao
    abstract fun iconPresetDao(): IconPresetDao

    companion object {
        const val NAME = "jourjour.db"

        /** Named so that a dump can say which schema its rows came from. */
        const val VERSION = 1
    }
}

/** Fills the dictionaries with their default contents whenever the database is created. */
class DefaultDataCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DefaultData.populate(db, context)
    }

    /**
     * Rebuilding the database after a schema change creates it just as much, so the
     * dictionaries have to be filled in again. It cannot happen in `onDestructiveMigration`
     * — Room calls that between dropping the tables and recreating them, when there is
     * nothing to write into — so the check happens on open instead.
     */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        if (!DefaultData.isEmpty(db)) return
        // All or nothing. The check above looks at the currencies alone, so a process
        // killed once they had landed would leave the other dictionaries empty for good.
        db.beginTransaction()
        try {
            DefaultData.populate(db, context)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}