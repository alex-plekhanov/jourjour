package app.jourjour.ui.budget

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.BudgetItemDetails

/**
 * A budget line as it is being typed.
 *
 * Three of the fields are two figures. A person arrives at a line either way round —
 * "three tickets at sixty thousand" or "five hundred dollars for the lot" — so price,
 * quantity and total are all typed into, and whichever was touched last decides the one
 * the app works out. What is saved is the total and the quantity; see [BudgetItem].
 */
data class BudgetSheet(
    /** Zero while the line has never been saved. */
    val id: Long = 0,
    val categoryId: Long? = null,
    val title: String = "",
    val notes: String = "",
    val quantity: String = "",
    val price: String = "",
    val amount: String = "",
    val currencyCode: String? = null,
    /** How it is meant to be paid for, where anybody has said. Optional throughout. */
    val paymentMethodId: Long? = null,
    val isCategorySheetVisible: Boolean = false,
    /** Notes are folded away until there are some, as they are on the entry form. */
    val isNotesVisible: Boolean = false,
    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val currencyError: Int? = null,
) {
    val isNew: Boolean get() = id == 0L

    /**
     * How many the line is for: what was typed, or one where the field says nothing.
     *
     * The field is left empty for a line of one — which most lines are — and the form
     * shows a grey one in it to say so. Typed in, it had to be rubbed out first every
     * time the answer was anything else, and the figure it stood for is the figure every
     * other field already assumes.
     *
     * Null only for what will not parse at all: a line is saved whatever its quantity
     * says, so what cannot be read is simply not kept.
     */
    val countedQuantity: Quantity?
        get() = if (quantity.isBlank()) Quantity.ONE else MoneyFormat.parseQuantity(quantity)

    companion object {
        fun of(details: BudgetItemDetails): BudgetSheet {
            val item = details.item
            val amount = Money(item.amountMilli)
            val quantity = item.quantityMilli?.let(::Quantity)
            return BudgetSheet(
                id = item.id,
                categoryId = item.categoryId,
                title = item.title,
                notes = item.notes,
                // One is what an empty field means, so one is shown as an empty field.
                quantity = quantity?.takeIf { it != Quantity.ONE }
                    ?.let(MoneyFormat::formatQuantityForInput)
                    .orEmpty(),
                price = app.jourjour.core.money.unitPrice(amount, quantity)
                    ?.let { MoneyFormat.formatAmountForInput(it, details.currency.decimals) }
                    .orEmpty(),
                amount = MoneyFormat.formatAmountForInput(amount, details.currency.decimals),
                currencyCode = item.currencyCode,
                paymentMethodId = item.paymentMethodId,
                isNotesVisible = item.notes.isNotBlank(),
            )
        }
    }
}

/** A checked sheet, or the same sheet with the reasons it was refused written on it. */
sealed interface BudgetSheetCheck {
    data class Accepted(
        val categoryId: Long,
        val title: String,
        val notes: String,
        val quantityMilli: Long?,
        val amountMilli: Long,
        val currencyCode: String,
        val paymentMethodId: Long?,
    ) : BudgetSheetCheck

    data class Refused(val sheet: BudgetSheet) : BudgetSheetCheck
}

/** @param decimals of the chosen currency, which decides what counts as a valid amount. */
fun BudgetSheet.check(decimals: Int): BudgetSheetCheck {
    val amountValue = MoneyFormat.parseAmount(amount, decimals)
    val amountError = when {
        amount.isBlank() -> R.string.error_amount_required
        amountValue == null -> R.string.error_amount_invalid
        else -> null
    }
    val categoryError = R.string.error_category_required.takeIf { categoryId == null }
    val currencyError = R.string.error_currency_required.takeIf { currencyCode == null }

    if (amountError != null || categoryError != null || currencyError != null) {
        return BudgetSheetCheck.Refused(
            copy(
                amountError = amountError,
                categoryError = categoryError,
                currencyError = currencyError,
            )
        )
    }

    return BudgetSheetCheck.Accepted(
        categoryId = requireNotNull(categoryId),
        title = title.trim(),
        notes = notes.trim(),
        // A quantity that will not parse is left out rather than refused: it says how the
        // sum was arrived at, and the sum is already in. An empty one is one.
        quantityMilli = countedQuantity?.milli,
        amountMilli = requireNotNull(amountValue).milli,
        currencyCode = requireNotNull(currencyCode),
        paymentMethodId = paymentMethodId,
    )
}
