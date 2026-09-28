package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Transaction
    @Query("SELECT * FROM entries WHERE trip_id = :tripId ORDER BY date_time DESC, id DESC")
    fun observeByTrip(tripId: Long): Flow<List<EntryDetails>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getDetailsById(id: Long): EntryDetails?

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    @Query("SELECT * FROM entries WHERE trip_id = :tripId ORDER BY date_time, id")
    suspend fun getByTrip(tripId: Long): List<Entry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: Entry): Long

    /** Room wraps a list in a transaction, so an import either lands whole or not at all. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<Entry>)

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE category_id = :categoryId)")
    suspend fun existsForCategory(categoryId: Long): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM entries
            WHERE expense_wallet_id = :walletId OR income_wallet_id = :walletId
        )
        """
    )
    suspend fun existsForWallet(walletId: Long): Boolean

    @Query("SELECT COUNT(*) FROM entries WHERE trip_id = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    /**
     * What this wallet was last paid in. It is what a new entry on it opens on: a card is
     * used in the currency of the place, not in the one its account is held in.
     *
     * Read from the entries rather than remembered on the wallet, so that correcting or
     * deleting the last entry corrects this along with it.
     */
    @Query(
        """
        SELECT code FROM (
            SELECT expense_currency_code AS code, date_time FROM entries
            WHERE expense_wallet_id = :walletId AND expense_currency_code IS NOT NULL
            UNION ALL
            SELECT income_currency_code AS code, date_time FROM entries
            WHERE income_wallet_id = :walletId AND income_currency_code IS NOT NULL
        ) AS paid
        ORDER BY date_time DESC
        LIMIT 1
        """
    )
    suspend fun lastCurrencyForWallet(walletId: Long): String?

    /**
     * Sets what the bank actually charged. A dedicated statement rather than a whole-row
     * update: reconciliation happens later and in bulk, and the other fields are none of
     * its business.
     */
    @Query("UPDATE entries SET expense_settlement_amount_milli = :amountMilli WHERE id = :entryId")
    suspend fun setExpenseSettlement(entryId: Long, amountMilli: Long?)

    @Query("UPDATE entries SET income_settlement_amount_milli = :amountMilli WHERE id = :entryId")
    suspend fun setIncomeSettlement(entryId: Long, amountMilli: Long?)

    @Query("SELECT photo_file FROM entries WHERE trip_id = :tripId AND photo_file IS NOT NULL")
    suspend fun photoFiles(tripId: Long): List<String>

    /** Every photo any record points at — what the orphan sweep measures itself against. */
    @Query("SELECT photo_file FROM entries WHERE photo_file IS NOT NULL")
    suspend fun allPhotoFiles(): List<String>

    @Query("SELECT COUNT(*) FROM entries WHERE trip_id = :tripId AND photo_file IS NOT NULL")
    fun observePhotoCount(tripId: Long): Flow<Int>

    @Query("UPDATE entries SET photo_file = NULL WHERE trip_id = :tripId")
    suspend fun clearPhotos(tripId: Long)
}