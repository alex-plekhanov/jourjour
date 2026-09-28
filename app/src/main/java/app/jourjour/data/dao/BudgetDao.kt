package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.TripBudgetCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * Every line of the trip's budget, in the order the categories are listed in
     * everywhere else, and within a category in the order the lines were added.
     */
    @Transaction
    @Query(
        "SELECT budget_items.* FROM budget_items " +
            "JOIN categories ON categories.id = budget_items.category_id " +
            "WHERE budget_items.trip_id = :tripId " +
            "ORDER BY categories.sort_order, categories.id, budget_items.id"
    )
    fun observeByTrip(tripId: Long): Flow<List<BudgetItemDetails>>

    /** The same lines in the same order, for a table to be written from. */
    @Transaction
    @Query(
        "SELECT budget_items.* FROM budget_items " +
            "JOIN categories ON categories.id = budget_items.category_id " +
            "WHERE budget_items.trip_id = :tripId " +
            "ORDER BY categories.sort_order, categories.id, budget_items.id"
    )
    suspend fun getByTrip(tripId: Long): List<BudgetItemDetails>

    @Transaction
    @Query("SELECT * FROM budget_items WHERE id = :id")
    suspend fun getById(id: Long): BudgetItemDetails?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: BudgetItem): Long

    @Update
    suspend fun update(item: BudgetItem)

    @Query("DELETE FROM budget_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM budget_items WHERE trip_id = :tripId AND category_id = :categoryId")
    suspend fun countInCategory(tripId: Long, categoryId: Long): Int

    // region What has been settled about a category

    @Query("SELECT * FROM trip_budget_categories WHERE trip_id = :tripId")
    fun observeCategories(tripId: Long): Flow<List<TripBudgetCategory>>

    @Query("SELECT * FROM trip_budget_categories WHERE trip_id = :tripId AND category_id = :categoryId")
    suspend fun category(tripId: Long, categoryId: Long): TripBudgetCategory?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: TripBudgetCategory): Long

    @Update
    suspend fun update(category: TripBudgetCategory)

    @Query("DELETE FROM trip_budget_categories WHERE trip_id = :tripId AND category_id = :categoryId")
    suspend fun deleteCategory(tripId: Long, categoryId: Long)

    // endregion
}
