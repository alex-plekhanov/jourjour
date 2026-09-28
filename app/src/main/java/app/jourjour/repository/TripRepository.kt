package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.data.dao.CurrencyDao
import app.jourjour.data.dao.EntryDao
import app.jourjour.data.dao.TripDao
import app.jourjour.data.dao.TripRateDao
import app.jourjour.data.dao.TripWalletDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val db: AppDatabase,
    private val tripDao: TripDao,
    private val walletDao: TripWalletDao,
    private val rateDao: TripRateDao,
    private val entryDao: EntryDao,
    private val currencyDao: CurrencyDao,
) {

    fun observeAll(): Flow<List<Trip>> = tripDao.observeAll()

    fun observeById(id: Long): Flow<Trip?> = tripDao.observeById(id)

    suspend fun getById(id: Long): Trip? = tripDao.getById(id)

    suspend fun insert(trip: Trip): Long = tripDao.insert(trip)

    /** Runs [block] as one write: either all of it reaches the database or none of it. */
    suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }

    suspend fun update(trip: Trip) = tripDao.update(trip)

    suspend fun delete(trip: Trip) = tripDao.delete(trip)

    suspend fun allPhotoFiles(): List<String> = tripDao.allPhotoFiles()

    fun observeWallets(tripId: Long): Flow<List<TripWalletDetails>> = walletDao.observeByTrip(tripId)

    suspend fun getWallets(tripId: Long): List<TripWalletDetails> = walletDao.getByTrip(tripId)

    suspend fun getWallet(id: Long): TripWalletDetails? = walletDao.getById(id)

    fun observeWallet(id: Long): Flow<TripWalletDetails?> = walletDao.observeById(id)

    /**
     * A wallet made in a currency is that currency used: it floats up the picker from now,
     * as it would after an entry paid in it. Only when the wallet is made — its currency
     * cannot change afterwards.
     */
    suspend fun addWallet(wallet: TripWallet): Long {
        val id = walletDao.insert(wallet)
        currencyDao.touch(wallet.currencyCode, LocalDateTime.now())
        return id
    }

    suspend fun updateWallet(wallet: TripWallet) = walletDao.update(wallet)

    suspend fun deleteWallet(wallet: TripWallet) = walletDao.delete(wallet)

    /** Records that an entry has just been paid from here, which floats it up the picker. */
    suspend fun touchWallet(id: Long, usedAt: LocalDateTime) = walletDao.touch(id, usedAt)

    /**
     * A wallet may only go while no entry is written against it: an entry is an amount in
     * its currency, and there would be nowhere to move it to. Its recounts are no
     * obstacle — a recount cannot be deleted on its own, and it says nothing once the
     * wallet it counted is gone, so the foreign key takes them along.
     */
    suspend fun walletIsUsed(walletId: Long): Boolean = entryDao.existsForWallet(walletId)

    /** How many entries would go with the trip — the deletion prompt says so upfront. */
    suspend fun countEntries(tripId: Long): Int = entryDao.countForTrip(tripId)

    fun observeRates(tripId: Long): Flow<List<TripRate>> = rateDao.observeByTrip(tripId)

    suspend fun getRates(tripId: Long): List<TripRate> = rateDao.getByTrip(tripId)

    /**
     * Naming a currency in a rate is that currency used, the same as making a wallet in
     * it: a rate is set up for the money a trip is about to be spent in, so it floats up
     * the picker from now on.
     *
     * The far end is not touched. It is the base currency, which was chosen once in the
     * settings and not here, and the order of the picker should follow what a person is
     * actually dealing in.
     *
     * Only when the rate is made, as with a wallet. Saving a trip rewrites every rate it
     * carries, so touching on update as well would reshuffle the picker every time a form
     * was opened and saved with nothing changed.
     */
    suspend fun addRate(rate: TripRate): Long {
        val id = rateDao.insert(rate)
        currencyDao.touch(rate.fromCurrencyCode, LocalDateTime.now())
        return id
    }

    suspend fun updateRate(rate: TripRate) = rateDao.update(rate)

    /** Nothing points at a rate, so it goes without anything having to be checked first. */
    suspend fun deleteRate(id: Long) = rateDao.deleteById(id)
}
