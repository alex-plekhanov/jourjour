package app.jourjour.repository

import app.jourjour.data.dao.BudgetDao
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(private val dao: BudgetDao) {

    fun observeByTrip(tripId: Long): Flow<List<BudgetItemDetails>> = dao.observeByTrip(tripId)

    suspend fun get(id: Long): BudgetItemDetails? = dao.getById(id)

    /** Writes a line, new or changed, and answers with its id. */
    suspend fun save(item: BudgetItem): Long =
        if (item.id == 0L) dao.insert(item) else dao.update(item).let { item.id }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
