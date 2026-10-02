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

/**
 * One line of what a trip is expected to cost: three air tickets at sixty thousand, ten
 * nights at fifty dollars.
 *
 * A rough plan rather than a ceiling. It is written before anything is booked, in whatever
 * currency the thing is priced in, and it is what the spending is read against once the
 * trip is under way.
 *
 * [amountMilli] is the line's total and the only money stored; the price of one unit is
 * worked out from it and the quantity, as it is for an entry. Of the three figures a
 * person types — price, quantity, total — only two are ever independent, and storing all
 * three would let them drift apart the first time one was edited.
 *
 * [quantityMilli] is free of the category's `trackQuantity`, which governs the entry form
 * alone: a night in a hotel is not something the app counts when the money is spent, but
 * ten of them is exactly how the sum was arrived at when it was planned.
 */
@Entity(
    tableName = "budget_items",
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
            entity = Currency::class,
            parentColumns = ["code"],
            childColumns = ["currency_code"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PaymentMethod::class,
            parentColumns = ["id"],
            childColumns = ["payment_method_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("trip_id"),
        Index("category_id"),
        Index("currency_code"),
        Index("payment_method_id"),
    ],
)
data class BudgetItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val title: String = "",
    val notes: String = "",
    /** How many of the thing, in thousandths. See [Quantity]. */
    @ColumnInfo(name = "quantity_milli")
    val quantityMilli: Long? = null,
    /** What the line comes to, in thousandths. See [Money]. */
    @ColumnInfo(name = "amount_milli")
    val amountMilli: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    /**
     * How it is meant to be paid for, where anybody has said. Optional, and usually left
     * alone: a plan written months ahead says what a thing costs, not which pocket it will
     * come out of. Where it is said it is worth a great deal — it is what lets the trip be
     * read as "how much cash do I still need", which is a different question from "how
     * much is left to spend".
     */
    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: Long? = null,
)

/** A budget line with the dictionary rows it names, which is how a list shows it. */
data class BudgetItemDetails(
    @Embedded
    val item: BudgetItem,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category,
    @Relation(parentColumn = "currency_code", entityColumn = "code")
    val currency: Currency,
    @Relation(parentColumn = "payment_method_id", entityColumn = "id")
    val paymentMethod: PaymentMethod? = null,
)
