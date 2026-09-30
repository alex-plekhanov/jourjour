package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.jourjour.core.money.Money

/**
 * What one currency of a trip is worth in another, so that the trip's totals can be read
 * as one figure.
 *
 * Both ends are written down rather than one. [toCurrencyCode] is the base currency as it
 * stood when the rate was made, and the base currency is a setting a person may change:
 * a rate that only said "to the base" would quietly come to mean something else the day
 * they did. Rates whose far end is no longer the base are shown with a warning instead of
 * being reinterpreted.
 *
 * [multiplier] is how many units of [fromCurrencyCode] the rate is quoted for, and exists
 * for the currencies where one unit buys almost nothing: 1000 JPY = 8.78 USD says more
 * than 1 JPY = 0.00878 USD, and holds the same precision in the same three decimals.
 *
 * Nothing points at a rate, so one can be deleted at any time; it is a lens on the
 * entries, not part of them.
 */
@Entity(
    tableName = "trip_rates",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["from_currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["to_currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // One rate to a pair: a second would leave the totals depending on which was read.
        Index(value = ["trip_id", "from_currency_code", "to_currency_code"], unique = true),
        Index("from_currency_code"),
        Index("to_currency_code"),
    ],
)
data class TripRate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "from_currency_code")
    val fromCurrencyCode: String,
    @ColumnInfo(name = "to_currency_code")
    val toCurrencyCode: String,
    /**
     * What [multiplier] units of the source currency are worth, in thousandths. Three
     * decimals for every rate whatever the currencies are: unlike an amount, a rate is not
     * money anybody hands over, and rounding it to the two decimals a dollar is written in
     * would turn 1.8 into 2. See [Money].
     */
    @ColumnInfo(name = "rate_milli")
    val rateMilli: Long,
    /** How many units of the source currency the rate is quoted for. At least one. */
    val multiplier: Long = 1,
    /**
     * Whatever is worth remembering about where the figure came from — the exchange
     * office, the day it was published, the fact that it is a guess. Empty when there is
     * nothing to say.
     */
    val note: String = "",
    /**
     * Whether [note] is the stamp a lookup wrote rather than a person's own words.
     *
     * A lookup fills the note in with the day the figure belongs to, which is the one
     * thing about a fetched rate worth keeping; but a note somebody wrote is theirs, and
     * the next lookup leaves it alone and says the date elsewhere.
     */
    @ColumnInfo(name = "note_from_lookup")
    val noteFromLookup: Boolean = false,
)
