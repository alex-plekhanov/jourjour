package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A saved title for a category: "Dining" offers breakfast, lunch and dinner, "Fuel"
 * offers the grades. Stored as ordinary text a person can edit and delete.
 *
 * Preloaded favourites carry a [nameKey] and are shown in the device's language, the same
 * way the dictionaries are; a favourite a person typed has none and is shown as typed.
 * The stored [name] stays the key part of the row either way, so a translated favourite
 * is still one row rather than one per language.
 */
@Entity(
    tableName = "category_favorites",
    primaryKeys = ["category_id", "name"],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
)
data class CategoryFavorite(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "name_key")
    val nameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
