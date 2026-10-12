package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.core.checklist.Placement
import app.jourjour.data.dao.ChecklistDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.ChecklistItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecklistRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: ChecklistDao,
) {

    fun observeByTrip(tripId: Long): Flow<List<ChecklistItem>> = dao.observeByTrip(tripId)

    suspend fun getByTrip(tripId: Long): List<ChecklistItem> = dao.getByTrip(tripId)

    /**
     * Writes the item, new or changed.
     *
     * @param place where it is to stand among the trip's items, or null to give it the end
     *   of the list — which, the list being read as its sections, is the end of its own
     *   section. A new line has no place yet, and neither has one the form has just moved
     *   to another section: the number it was given among its old neighbours would land it
     *   anywhere at all among its new ones.
     */
    suspend fun save(item: ChecklistItem, place: Int? = null): Long = db.withTransaction {
        val placed = item.copy(sortOrder = place ?: dao.nextPlace(item.tripId))
        if (placed.id == 0L) dao.insert(placed) else placed.id.also { dao.update(placed) }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * The order the list has been dragged into, written as one: half a rearranged list is
     * a list in no order at all.
     */
    suspend fun place(placements: List<Placement>) = db.withTransaction {
        placements.forEach { dao.place(it.id, it.section, it.sortOrder) }
    }

    /** Takes the section and everything in it: the two cannot be told apart. */
    suspend fun deleteSection(tripId: Long, section: String) = dao.deleteSection(tripId, section)

    suspend fun renameSection(tripId: Long, oldName: String, newName: String) =
        dao.renameSection(tripId, oldName, newName)

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    /** Every item of one section at once, bar the ones ruled out for this trip. */
    suspend fun setSectionDone(tripId: Long, section: String, done: Boolean) =
        dao.setSectionDone(tripId, section, done)

    /**
     * A copy from another trip lands as one write: half a checklist is worse than none.
     *
     * The lot joins the end of the list in the order it came, which puts what belongs to a
     * section this trip already has at the end of that section and leaves whole new
     * sections at the foot — the same places they would land in one at a time.
     */
    suspend fun addAll(items: List<ChecklistItem>) = db.withTransaction {
        var place = items.firstOrNull()?.let { dao.nextPlace(it.tripId) } ?: 0
        items.forEach { dao.insert(it.copy(sortOrder = place++)) }
    }
}
