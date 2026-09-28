package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.jourjour.data.entity.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY start_date DESC, id DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips ORDER BY start_date")
    suspend fun getAll(): List<Trip>

    /** Every cover in use, so the sweep on startup can tell files apart from leftovers. */
    @Query("SELECT photo_file FROM trips WHERE photo_file IS NOT NULL")
    suspend fun allPhotoFiles(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}