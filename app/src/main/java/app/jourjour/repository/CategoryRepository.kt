package app.jourjour.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import app.jourjour.data.dao.CategoryDao
import app.jourjour.data.dao.CategoryFavoriteDao
import app.jourjour.data.dao.EntryDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.CategoryWithFavorites
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: CategoryDao,
    private val favoriteDao: CategoryFavoriteDao,
    private val entryDao: EntryDao,
) {

    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    suspend fun getAll(): List<Category> = dao.getAll()

    fun observeAllWithFavorites(): Flow<List<CategoryWithFavorites>> = dao.observeAllWithFavorites()

    suspend fun getById(id: Long): Category? = dao.getById(id)

    suspend fun insert(category: Category): Long = dao.insert(category)

    suspend fun update(category: Category) = dao.update(category)

    suspend fun delete(category: Category) = dao.delete(category)

    suspend fun isUsed(categoryId: Long): Boolean = entryDao.existsForCategory(categoryId)

    fun observeFavorites(categoryId: Long): Flow<List<CategoryFavorite>> =
        favoriteDao.observeByCategory(categoryId)

    suspend fun addFavorite(categoryId: Long, name: String) =
        favoriteDao.insert(CategoryFavorite(categoryId = categoryId, name = name))

    suspend fun removeFavorite(categoryId: Long, name: String) = favoriteDao.delete(categoryId, name)

    suspend fun getFavorites(categoryId: Long): List<CategoryFavorite> =
        favoriteDao.getByCategory(categoryId)

    /**
     * Writes a category and its favourite titles as one: a category saved without its
     * favourites, the second write refused, would look saved and be wrong. [favoritesOf]
     * is asked with the id, which a new category only has once its row is in.
     */
    suspend fun save(
        category: Category,
        favoritesOf: (categoryId: Long) -> List<CategoryFavorite>,
    ): Long = db.withTransaction {
        val id = if (category.id == 0L) dao.insert(category) else {
            dao.update(category)
            category.id
        }
        favoriteDao.replaceForCategory(id, favoritesOf(id))
        id
    }
}
