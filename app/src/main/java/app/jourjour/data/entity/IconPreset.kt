package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** What kind of record a preset icon is offered for. */
enum class IconTarget {
    PAYMENT_METHOD,
    CATEGORY,
}

/**
 * An icon offered when picking one for a payment method or a category.
 *
 * The app ships a handful of them, and anything a person types by hand is kept here too,
 * so an icon has to be found only once and is then a tap away for every later record.
 *
 * The target and the emoji together make the key: offering the same icon twice within one
 * kind of record makes no sense, and the insert simply ignores a repeat.
 */
@Entity(tableName = "icon_presets", primaryKeys = ["target", "emoji"])
data class IconPreset(
    val target: IconTarget,
    val emoji: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
