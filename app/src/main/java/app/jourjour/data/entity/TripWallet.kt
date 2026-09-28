package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.jourjour.core.money.Money
import java.time.LocalDateTime

/**
 * A wallet of a trip: a payment method in one currency, plus what it held when the trip
 * began. It is what an entry is paid from, and what a recount counts.
 *
 * The same payment method may join a trip more than once — two folds of cash in dollars
 * are two wallets — so what tells wallets apart is the [name], which is unique within the
 * trip. A blank name is filled in on the way out from the payment method and the currency
 * ("Cash (US Dollar)"); it is left blank rather than written down so that it follows the
 * device language, the way every other preloaded name does.
 *
 * [currencyCode] cannot be changed once the wallet exists: entries and recounts are
 * amounts in it, and rewriting it would silently reinterpret every one of them. A wallet
 * nothing points at can be deleted instead.
 */
@Entity(
    tableName = "trip_wallets",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PaymentMethod::class,
            parentColumns = ["id"],
            childColumns = ["payment_method_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Only names that were actually typed are held apart here; SQLite counts nulls as
        // distinct, so wallets that go by their derived name are checked before saving.
        Index(value = ["trip_id", "name"], unique = true),
        Index("payment_method_id"),
        Index("currency_code"),
    ],
)
data class TripWallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    /** What a person called this wallet; null leaves it to the payment method and currency. */
    val name: String? = null,
    /** Balance at the start of the trip, in thousandths. See [Money]. */
    @ColumnInfo(name = "opening_balance_milli")
    val openingBalanceMilli: Long = 0,
    /** When an entry was last paid from here: it puts the wallet at the top of the picker. */
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: LocalDateTime? = null,
)

data class TripWalletDetails(
    @Embedded
    val wallet: TripWallet,
    @Relation(parentColumn = "payment_method_id", entityColumn = "id")
    val paymentMethod: PaymentMethod,
    @Relation(parentColumn = "currency_code", entityColumn = "code")
    val currency: Currency,
)
