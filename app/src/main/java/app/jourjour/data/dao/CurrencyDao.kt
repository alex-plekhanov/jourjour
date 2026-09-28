package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.Currency
import java.time.LocalDateTime

@Dao
interface CurrencyDao {

    @Query("SELECT * FROM currencies ORDER BY sort_order, code")
    fun observeAll(): Flow<List<Currency>>

    @Query("SELECT * FROM currencies ORDER BY sort_order, code")
    suspend fun getAll(): List<Currency>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun getByCode(code: String): Currency?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(currency: Currency)

    @Update
    suspend fun update(currency: Currency)

    @Delete
    suspend fun delete(currency: Currency)

    /**
     * Notes that money has just been recorded in this currency. A column update rather
     * than a whole-row write: the dictionary row may be being edited elsewhere, and the
     * only thing this knows about is the time.
     */
    @Query("UPDATE currencies SET used_at = :usedAt WHERE code = :code")
    suspend fun touch(code: String, usedAt: LocalDateTime)

    /** A currency cannot go while a wallet or an entry still points at it. */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM trip_wallets WHERE currency_code = :code)
            OR EXISTS(
                SELECT 1 FROM entries
                WHERE expense_currency_code = :code OR income_currency_code = :code
            )
            OR EXISTS(
                SELECT 1 FROM trip_rates
                WHERE from_currency_code = :code OR to_currency_code = :code
            )
        """
    )
    suspend fun isUsed(code: String): Boolean
}
