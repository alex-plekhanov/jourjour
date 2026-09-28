package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A way of paying — cash, a card, a transfer.
 *
 * It carries no currency of its own. Money belongs to a wallet, and a wallet is this
 * method in one currency on one trip; see [TripWallet]. What the method does decide is
 * whether it can be used to pay in some other currency than the one its wallet holds:
 * a card lets the bank convert, a fold of banknotes does not.
 */
@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** An emoji — any character a person likes. */
    val icon: String,
    /**
     * Whether a wallet of this method can only ever hold its own currency. Cash can: notes
     * cannot be handed over in a currency they are not. A card cannot: the bank converts.
     */
    @ColumnInfo(name = "fixed_currency")
    val fixedCurrency: Boolean = false,
    @ColumnInfo(name = "name_key")
    val nameKey: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
