package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryWithFavorites

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    suspend fun getAll(): List<Category>

    @Transaction
    @Query("SELECT * FROM categories ORDER BY sort_order, name")
    fun observeAllWithFavorites(): Flow<List<CategoryWithFavorites>>

    @Transaction
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getWithFavorites(id: Long): CategoryWithFavorites?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
