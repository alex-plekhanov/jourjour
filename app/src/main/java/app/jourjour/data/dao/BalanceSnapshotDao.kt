package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.BalanceSnapshotDetails

@Dao
interface BalanceSnapshotDao {

    @Transaction
    @Query(
        """
        SELECT s.* FROM balance_snapshots s
        JOIN trip_wallets w ON w.id = s.wallet_id
        WHERE w.trip_id = :tripId
        ORDER BY s.date_time DESC, s.id DESC
        """
    )
    fun observeByTrip(tripId: Long): Flow<List<BalanceSnapshotDetails>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: BalanceSnapshot): Long

    @Query("SELECT * FROM balance_snapshots WHERE wallet_id = :walletId ORDER BY date_time, id")
    suspend fun getByWallet(walletId: Long): List<BalanceSnapshot>
}
