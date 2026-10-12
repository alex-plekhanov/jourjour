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
     * Every item of the trip, in the place it has been dragged into and, where nobody has
     * dragged it, the order it was made in: a list nobody has rearranged carries nought
     * all the way down and falls back on the second entirely.
     *
     * The sections are pulled out of this in the order they first appear, so a section
     * keeps its place and an item added to it later joins the end of that section rather
     * than the end of the list.
     */
    @Query("SELECT * FROM checklist_items WHERE trip_id = :tripId ORDER BY sort_order, id")
    fun observeByTrip(tripId: Long): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE trip_id = :tripId ORDER BY sort_order, id")
    suspend fun getByTrip(tripId: Long): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ChecklistItem): Long

    @Update
    suspend fun update(item: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Where a line stands, which is the whole of what a drag writes: its place among the
     * trip's items and the section it carries, since dragging a line across a heading is
     * how it is moved from one section to another.
     */
    @Query("UPDATE checklist_items SET section = :section, sort_order = :sortOrder WHERE id = :id")
    suspend fun place(id: Long, section: String, sortOrder: Int)

    /**
     * The place a line lands on when nobody has said where it goes: the end of the trip's
     * list. Which is the end of its own section, a section being the run of items that
     * carry its name and the list being read as its sections.
     */
    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM checklist_items WHERE trip_id = :tripId")
    suspend fun nextPlace(tripId: Long): Int

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

    /**
     * The same tick for a whole section at once. Items ruled out for this trip keep out
     * of it: their tick says nothing, and settling them would be answering for them.
     */
    @Query(
        "UPDATE checklist_items SET is_done = :done " +
            "WHERE trip_id = :tripId AND section = :section AND is_skipped = 0"
    )
    suspend fun setSectionDone(tripId: Long, section: String, done: Boolean)
}
