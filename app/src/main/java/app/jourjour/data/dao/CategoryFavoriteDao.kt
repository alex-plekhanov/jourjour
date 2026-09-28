package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.entity.CategoryFavorite

@Dao
interface CategoryFavoriteDao {

    @Query("SELECT * FROM category_favorites WHERE category_id = :categoryId ORDER BY sort_order, name")
    fun observeByCategory(categoryId: Long): Flow<List<CategoryFavorite>>

    @Query("SELECT * FROM category_favorites WHERE category_id = :categoryId ORDER BY sort_order, name")
    suspend fun getByCategory(categoryId: Long): List<CategoryFavorite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: CategoryFavorite)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<CategoryFavorite>)

    @Query("DELETE FROM category_favorites WHERE category_id = :categoryId AND name = :name")
    suspend fun delete(categoryId: Long, name: String)

    @Query("DELETE FROM category_favorites WHERE category_id = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    /**
     * Replaces a category's favourites wholesale. They are nothing but a short list, so
     * recreating the set costs less and breaks less than reconciling row-by-row edits;
     * order comes from the position in the list. The category and the order are this
     * method's to fill in — the caller supplies the name and, for a preloaded favourite,
     * the resource key that keeps it translated.
     */
    @Transaction
    suspend fun replaceForCategory(categoryId: Long, favorites: List<CategoryFavorite>) {
        deleteByCategory(categoryId)
        insertAll(
            favorites.mapIndexed { index, favorite ->
                favorite.copy(categoryId = categoryId, sortOrder = index)
            }
        )
    }
}
