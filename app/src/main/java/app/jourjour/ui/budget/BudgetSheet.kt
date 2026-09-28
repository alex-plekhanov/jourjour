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
    val isCategorySheetVisible: Boolean = false,
    /** Notes are folded away until there are some, as they are on the entry form. */
    val isNotesVisible: Boolean = false,
    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val currencyError: Int? = null,
) {
    val isNew: Boolean get() = id == 0L

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
                quantity = quantity?.let(MoneyFormat::formatQuantityForInput).orEmpty(),
                price = app.jourjour.core.money.unitPrice(amount, quantity)
                    ?.let { MoneyFormat.formatAmountForInput(it, details.currency.decimals) }
                    .orEmpty(),
                amount = MoneyFormat.formatAmountForInput(amount, details.currency.decimals),
                currencyCode = item.currencyCode,
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
        // sum was arrived at, and the sum is already in.
        quantityMilli = MoneyFormat.parseQuantity(quantity)?.milli,
        amountMilli = requireNotNull(amountValue).milli,
        currencyCode = requireNotNull(currencyCode),
    )
}
