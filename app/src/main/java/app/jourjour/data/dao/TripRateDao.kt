package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.jourjour.data.entity.TripRate
import kotlinx.coroutines.flow.Flow

@Dao
interface TripRateDao {

    /** By the code they convert from, which is how they are read: one line per currency. */
    @Query("SELECT * FROM trip_rates WHERE trip_id = :tripId ORDER BY from_currency_code")
    fun observeByTrip(tripId: Long): Flow<List<TripRate>>

    @Query("SELECT * FROM trip_rates WHERE trip_id = :tripId ORDER BY from_currency_code")
    suspend fun getByTrip(tripId: Long): List<TripRate>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rate: TripRate): Long

    @Update
    suspend fun update(rate: TripRate)

    @Delete
    suspend fun delete(rate: TripRate)

    @Query("DELETE FROM trip_rates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
