package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.data.dao.BudgetDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.TripBudgetCategory
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

    /**
     * Takes a line, and with it what was settled about its category once the category has
     * no lines left: a category that is not in the budget has nothing to declare closed,
     * and a row saying otherwise would come back to life with the next line written.
     */
    suspend fun delete(details: BudgetItemDetails) = db.withTransaction {
        dao.deleteById(details.item.id)
        val tripId = details.item.tripId
        val categoryId = details.item.categoryId
        if (dao.countInCategory(tripId, categoryId) == 0) dao.deleteCategory(tripId, categoryId)
    }

    // region What has been settled about a category

    fun observeCategories(tripId: Long): Flow<List<TripBudgetCategory>> =
        dao.observeCategories(tripId)

    /** Declares the spending of a category finished, or opens it again. */
    suspend fun setSpendingClosed(tripId: Long, categoryId: Long, closed: Boolean) =
        settle(tripId, categoryId) { it.copy(isSpendingClosed = closed) }

    /**
     * Writes one of a category's flags, making the row where there is none and taking it
     * away again once it says nothing: an absent row and a row of two falses are the same
     * thing, and only one of them is worth keeping.
     */
    private suspend fun settle(
        tripId: Long,
        categoryId: Long,
        change: (TripBudgetCategory) -> TripBudgetCategory,
    ) = db.withTransaction {
        val current = dao.category(tripId, categoryId)
            ?: TripBudgetCategory(tripId = tripId, categoryId = categoryId)
        val changed = change(current)
        when {
            !changed.isSpendingClosed && !changed.isPlanningClosed ->
                dao.deleteCategory(tripId, categoryId)
            changed.id == 0L -> dao.insert(changed)
            else -> dao.update(changed)
        }
        Unit
    }

    // endregion
}
