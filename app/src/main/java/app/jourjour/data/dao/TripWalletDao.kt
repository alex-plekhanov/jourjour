package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TripWalletDao {

    /**
     * The trip's wallets, the ones used most recently first. That is the order the entry
     * form offers them in, and a trip is paid from two or three of them over and over.
     */
    @Transaction
    @Query("SELECT * FROM trip_wallets WHERE trip_id = :tripId ORDER BY last_used_at DESC, id")
    fun observeByTrip(tripId: Long): Flow<List<TripWalletDetails>>

    @Transaction
    @Query("SELECT * FROM trip_wallets WHERE trip_id = :tripId ORDER BY last_used_at DESC, id")
    suspend fun getByTrip(tripId: Long): List<TripWalletDetails>

    @Transaction
    @Query("SELECT * FROM trip_wallets WHERE id = :id")
    suspend fun getById(id: Long): TripWalletDetails?

    @Transaction
    @Query("SELECT * FROM trip_wallets WHERE id = :id")
    fun observeById(id: Long): Flow<TripWalletDetails?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: TripWallet): Long

    @Update
    suspend fun update(wallet: TripWallet)

    @Delete
    suspend fun delete(wallet: TripWallet)

    /** Notes that an entry has just been paid from here. */
    @Query("UPDATE trip_wallets SET last_used_at = :usedAt WHERE id = :id")
    suspend fun touch(id: Long, usedAt: LocalDateTime)

    @Query("SELECT EXISTS(SELECT 1 FROM trip_wallets WHERE payment_method_id = :paymentMethodId)")
    suspend fun existsForPaymentMethod(paymentMethodId: Long): Boolean
}
