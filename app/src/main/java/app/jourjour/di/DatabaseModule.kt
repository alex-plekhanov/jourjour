package app.jourjour.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.jourjour.BuildConfig
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
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.db.DefaultDataCallback
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(DefaultDataCallback(context))
            .apply {
                // Nothing is released yet, so the schema is free to change and the
                // database is simply rebuilt. Replace this with migrations before the
                // first release, or shipping an update will wipe people's trips.
                if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
            }
            .build()

    @Provides
    fun provideCurrencyDao(db: AppDatabase): CurrencyDao = db.currencyDao()

    @Provides
    fun providePaymentMethodDao(db: AppDatabase): PaymentMethodDao = db.paymentMethodDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCategoryFavoriteDao(db: AppDatabase): CategoryFavoriteDao = db.categoryFavoriteDao()

    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun provideTripWalletDao(db: AppDatabase): TripWalletDao = db.tripWalletDao()

    @Provides
    fun provideTripRateDao(db: AppDatabase): TripRateDao = db.tripRateDao()

    @Provides
    fun provideChecklistDao(db: AppDatabase): ChecklistDao = db.checklistDao()

    @Provides
    fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideBalanceSnapshotDao(db: AppDatabase): BalanceSnapshotDao = db.balanceSnapshotDao()

    @Provides
    fun provideIconPresetDao(db: AppDatabase): IconPresetDao = db.iconPresetDao()
}
