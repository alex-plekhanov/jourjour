package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.jourjour.core.money.Money
import java.time.LocalDateTime

/**
 * A currency.
 *
 * [decimals] is the number of decimal places for input and display ONLY (USD has 2,
 * JPY has 0, KWD has 3). It has no bearing on storage: amounts are always whole numbers
 * of thousandths, see [Money].
 *
 * [nameKey] is filled in on preloaded rows and points at the string resource holding the
 * translated name; [name] is the fallback and the name of rows a person created.
 * See [app.jourjour.core.text.localizedName].
 */
@Entity(tableName = "currencies")
data class Currency(
    @PrimaryKey
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    @ColumnInfo(name = "name_key")
    val nameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    /**
     * When an entry was last written in this currency, across every trip. Null until it
     * has been used at all. It is what floats the handful of currencies a person actually
     * deals in to the top of the picker, ahead of the seventy that sit in the dictionary.
     */
    @ColumnInfo(name = "used_at")
    val usedAt: LocalDateTime? = null,
)