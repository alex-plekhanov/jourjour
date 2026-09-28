package app.jourjour.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import java.time.LocalDateTime

/**
 * A record of money spent or received — the entity the app exists for.
 *
 * The shape is flat: an entry has an expense half and an income half, each filled in when
 * the category allows it. A currency exchange is a single entry with both halves filled
 * (100 USD out of the cash wallet, 9000 RUB into it).
 *
 * A half names the wallet it moved through and the currency it was written in. The two
 * usually agree; they differ when the payment method allows paying in another currency,
 * and then the settled amount says what the wallet was actually charged — in the wallet's
 * own currency, which is why no currency is stored beside it.
 *
 * Amounts and quantities are whole numbers of thousandths, see [Money] and [Quantity].
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TripWallet::class,
            parentColumns = ["id"],
            childColumns = ["expense_wallet_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TripWallet::class,
            parentColumns = ["id"],
            childColumns = ["income_wallet_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["expense_currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["income_currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trip_id", "date_time"]),
        Index("category_id"),
        Index("expense_wallet_id"),
        Index("income_wallet_id"),
        Index("expense_currency_code"),
        Index("income_currency_code"),
    ],
)
data class Entry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "date_time")
    val dateTime: LocalDateTime,
    val title: String = "",
    val place: String = "",
    val notes: String = "",
    /** Present when the category tracks quantity. Unit price is derived, never stored. */
    @ColumnInfo(name = "quantity_milli")
    val quantityMilli: Long? = null,

    @ColumnInfo(name = "expense_wallet_id")
    val expenseWalletId: Long? = null,
    @ColumnInfo(name = "expense_amount_milli")
    val expenseAmountMilli: Long? = null,
    @ColumnInfo(name = "expense_currency_code")
    val expenseCurrencyCode: String? = null,

    @ColumnInfo(name = "income_wallet_id")
    val incomeWalletId: Long? = null,
    @ColumnInfo(name = "income_amount_milli")
    val incomeAmountMilli: Long? = null,
    @ColumnInfo(name = "income_currency_code")
    val incomeCurrencyCode: String? = null,

    /**
     * What was actually taken off the wallet, in the wallet's own currency. It only means
     * anything when payment was made in some other currency, and it is not known straight
     * away — a bank reports it later — so the field stands on its own and stays optional.
     */
    @ColumnInfo(name = "expense_settlement_amount_milli")
    val expenseSettlementAmountMilli: Long? = null,
    @ColumnInfo(name = "income_settlement_amount_milli")
    val incomeSettlementAmountMilli: Long? = null,

    /**
     * File name of the receipt photo inside the app's own storage. A name rather than a
     * content:// link: the permission behind a link can be revoked, a name always resolves.
     */
    @ColumnInfo(name = "photo_file")
    val photoFile: String? = null,

    /**
     * Where it happened. Two numbers rather than one string: a string would have to be
     * parsed on every trip to the map, and it would depend on what the locale uses as a
     * decimal separator. Filled in only when a person taps the button on the entry form.
     */
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val hasExpense: Boolean get() = expenseAmountMilli != null
    val hasIncome: Boolean get() = incomeAmountMilli != null
}

data class EntryDetails(
    @Embedded
    val entry: Entry,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category,
    @Relation(entity = TripWallet::class, parentColumn = "expense_wallet_id", entityColumn = "id")
    val expenseWallet: TripWalletDetails?,
    @Relation(entity = TripWallet::class, parentColumn = "income_wallet_id", entityColumn = "id")
    val incomeWallet: TripWalletDetails?,
    @Relation(parentColumn = "expense_currency_code", entityColumn = "code")
    val expenseCurrency: Currency?,
    @Relation(parentColumn = "income_currency_code", entityColumn = "code")
    val incomeCurrency: Currency?,
)

/**
 * The quantity the entry counts, which is none where its category counts nothing.
 *
 * A stored quantity can outlive the reason it was stored: a category is edited to stop
 * counting, or a file brings a quantity for a category that never asked for one. The form
 * shows the field only where the category counts, so such a figure is one nobody can
 * correct or even see — and a total made of figures like that is a total of nothing anyone
 * entered. Everything that reads a quantity back reads it from here.
 */
val EntryDetails.countedQuantityMilli: Long?
    get() = entry.quantityMilli?.takeIf { category.trackQuantity }
