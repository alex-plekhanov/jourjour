package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.jourjour.data.entity.ChecklistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    /**
     * Every item of the trip, oldest first. The sections are pulled out of this in the
     * order they first appear, so a section keeps the place it was made in and an item
     * added to it later joins the end of that section rather than the end of the list.
     */
    @Query("SELECT * FROM checklist_items WHERE trip_id = :tripId ORDER BY id")
    fun observeByTrip(tripId: Long): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE trip_id = :tripId ORDER BY id")
    suspend fun getByTrip(tripId: Long): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ChecklistItem): Long

    @Update
    suspend fun update(item: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** A section is its items, so deleting one is deleting them. */
    @Query("DELETE FROM checklist_items WHERE trip_id = :tripId AND section = :section")
    suspend fun deleteSection(tripId: Long, section: String)

    /** And renaming one is rewriting the name on every item that carries it. */
    @Query(
        "UPDATE checklist_items SET section = :newName " +
            "WHERE trip_id = :tripId AND section = :oldName"
    )
    suspend fun renameSection(tripId: Long, oldName: String, newName: String)

    /** The tick, which is the one thing changed straight from the list. */
    @Query("UPDATE checklist_items SET is_done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)
}
