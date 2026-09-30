package app.jourjour.repository

import androidx.room.withTransaction
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

    suspend fun add(item: ChecklistItem): Long = dao.insert(item)

    suspend fun update(item: ChecklistItem) = dao.update(item)

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Takes the section and everything in it: the two cannot be told apart. */
    suspend fun deleteSection(tripId: Long, section: String) = dao.deleteSection(tripId, section)

    suspend fun renameSection(tripId: Long, oldName: String, newName: String) =
        dao.renameSection(tripId, oldName, newName)

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    /** Every item of one section at once, bar the ones ruled out for this trip. */
    suspend fun setSectionDone(tripId: Long, section: String, done: Boolean) =
        dao.setSectionDone(tripId, section, done)

    /** A copy from another trip lands as one write: half a checklist is worse than none. */
    suspend fun addAll(items: List<ChecklistItem>) = db.withTransaction {
        items.forEach { dao.insert(it) }
    }
}
