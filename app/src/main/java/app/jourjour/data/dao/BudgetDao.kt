package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
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

    @Transaction
    @Query("SELECT * FROM budget_items WHERE id = :id")
    suspend fun getById(id: Long): BudgetItemDetails?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: BudgetItem): Long

    @Update
    suspend fun update(item: BudgetItem)

    @Query("DELETE FROM budget_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
