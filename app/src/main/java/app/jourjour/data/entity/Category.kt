package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A category of expense or income. It can be both at once — "Currency exchange" spends
 * 100 USD in cash and receives 9000 RUB in cash within a single entry.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** An emoji — any character a person likes. */
    val icon: String,
    @ColumnInfo(name = "is_expense")
    val isExpense: Boolean = true,
    @ColumnInfo(name = "is_income")
    val isIncome: Boolean = false,
    /** Show the quantity field and the derived unit price on an entry of this category. */
    @ColumnInfo(name = "track_quantity")
    val trackQuantity: Boolean = false,
    @ColumnInfo(name = "name_key")
    val nameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)

data class CategoryWithFavorites(
    @Embedded
    val category: Category,
    @Relation(parentColumn = "id", entityColumn = "category_id")
    val favorites: List<CategoryFavorite>,
)
