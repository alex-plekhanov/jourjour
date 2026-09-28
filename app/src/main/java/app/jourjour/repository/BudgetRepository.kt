package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.data.dao.BudgetDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: BudgetDao,
) {

    fun observeByTrip(tripId: Long): Flow<List<BudgetItemDetails>> = dao.observeByTrip(tripId)

    suspend fun getByTrip(tripId: Long): List<BudgetItemDetails> = dao.getByTrip(tripId)

    suspend fun get(id: Long): BudgetItemDetails? = dao.getById(id)

    /** A table read in lands as one write: half a budget is worse than none. */
    suspend fun addAll(items: List<BudgetItem>) = db.withTransaction {
        items.forEach { dao.insert(it) }
    }

    /** Writes a line, new or changed, and answers with its id. */
    suspend fun save(item: BudgetItem): Long =
        if (item.id == 0L) dao.insert(item) else dao.update(item).let { item.id }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
