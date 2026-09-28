package app.jourjour.ui.budget

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.jourjour.core.budget.BudgetTotals
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.repository.BudgetRepository
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** The lines of one category, as the list shows them. */
@Immutable
data class BudgetCategoryBlock(
    val category: Category,
    val items: List<BudgetItemDetails>,
)

@Immutable
data class BudgetState(
    val blocks: List<BudgetCategoryBlock> = emptyList(),
    /** Null while there is nothing to add up. */
    val totals: BudgetTotals? = null,
    /** What a line may be filed under: what a person spends on, never what comes back. */
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val sheet: BudgetSheet? = null,
    val isLoading: Boolean = true,
) {
    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }
}

/**
 * What the trip is expected to cost.
 *
 * A rough plan written before anything is booked: a line for each thing worth naming, in
 * the currency that thing is priced in, added up per currency and, where the trip carries
 * the rates for it, as one figure in the base currency.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: BudgetRepository,
    tripRepository: TripRepository,
    categoryRepository: CategoryRepository,
    currencyRepository: CurrencyRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val sheet = MutableStateFlow<BudgetSheet?>(null)

    /** The currency a new line starts in, remembered from the last one written. */
    private var lastCurrencyCode: String? = null

    private val written = combine(
        repository.observeByTrip(tripId),
        tripRepository.observeRates(tripId),
        appPreferences.baseCurrency,
        currencyRepository.observeAll(),
        categoryRepository.observeAll(),
    ) { items, rates, baseCode, currencies, categories ->
        BudgetState(
            blocks = blocksOf(items),
            totals = BudgetTotals.of(
                items = items,
                base = currencies.firstOrNull { it.code == baseCode },
                rates = rates,
            ),
            // Money planned is money going out. A category that only ever brings money in
            // has nothing to budget, and an exchange moves a person's own money about.
            categories = categories.filter { it.isExpense && !it.isIncome },
            currencies = currencies,
            isLoading = false,
        )
    }

    val state: StateFlow<BudgetState> = combine(written, sheet) { base, openSheet ->
        base.copy(sheet = openSheet)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BudgetState(),
    )

    /** The lines under their categories, in the order the query already put them in. */
    private fun blocksOf(items: List<BudgetItemDetails>): List<BudgetCategoryBlock> =
        items.groupBy { it.category.id }
            .map { (_, group) -> BudgetCategoryBlock(group.first().category, group) }

    // region The sheet

    /** @param details null to write a new line. */
    fun openSheet(details: BudgetItemDetails? = null) {
        viewModelScope.launch {
            sheet.value = details?.let(BudgetSheet::of)
                // A new line starts in the currency the last one was written in, and
                // failing that in the one the person reckons in.
                ?: BudgetSheet(
                    currencyCode = lastCurrencyCode ?: appPreferences.baseCurrency.first()
                )
        }
    }

    fun closeSheet() {
        sheet.value = null
    }

    fun showCategorySheet() = sheet.update { it?.copy(isCategorySheetVisible = true) }

    fun hideCategorySheet() = sheet.update { it?.copy(isCategorySheetVisible = false) }

    fun selectCategory(category: Category) = sheet.update {
        it?.copy(
            categoryId = category.id,
            isCategorySheetVisible = false,
            categoryError = null,
        )
    }

    fun updateTitle(value: String) = sheet.update { it?.copy(title = value) }

    fun updateNotes(value: String) = sheet.update { it?.copy(notes = value) }

    fun showNotes() = sheet.update { it?.copy(isNotesVisible = true) }

    fun hideNotes() = sheet.update { it?.copy(isNotesVisible = false, notes = "") }

    fun selectCurrency(code: String?) = sheet.update { current ->
        val open = current ?: return@update null
        // The figures follow the currency into its own shape: five hundred dollars is
        // written 500.00 and five hundred yen is written 500.
        val decimals = decimalsOf(code)
        open.copy(
            currencyCode = code,
            currencyError = null,
            price = reformat(open.price, decimals),
            amount = reformat(open.amount, decimals),
        )
    }

    private fun reformat(value: String, decimals: Int): String =
        MoneyFormat.parseAmount(value, decimals)
            ?.let { MoneyFormat.formatAmountForInput(it, decimals) }
            ?: value

    /**
     * The quantity has changed, so one of the other two figures follows it.
     *
     * The total is what a price and a quantity make, so it is recomputed where there is a
     * price to multiply; failing that the price is what the total divides into, which is
     * the case of somebody who typed a lump sum and is now saying what it bought.
     */
    fun updateQuantity(value: String) = sheet.update { current ->
        val open = current ?: return@update null
        val quantity = MoneyFormat.parseQuantity(value)
        val decimals = decimalsOf(open.currencyCode)
        val price = MoneyFormat.parseAmount(open.price, decimals)
        val amount = MoneyFormat.parseAmount(open.amount, decimals)
        when {
            quantity == null -> open.copy(quantity = value)
            price != null -> open.copy(
                quantity = value,
                amount = format(multiply(price, quantity), decimals),
                amountError = null,
            )
            amount != null -> open.copy(
                quantity = value,
                price = format(unitPrice(amount, quantity), decimals),
            )
            else -> open.copy(quantity = value)
        }
    }

    /** A price is one of the two figures the total is made of; the total follows it. */
    fun updatePrice(value: String) = sheet.update { current ->
        val open = current ?: return@update null
        val decimals = decimalsOf(open.currencyCode)
        val price = MoneyFormat.parseAmount(value, decimals)
        val quantity = MoneyFormat.parseQuantity(open.quantity)
        if (price == null || quantity == null) {
            open.copy(price = value)
        } else {
            open.copy(
                price = value,
                amount = format(multiply(price, quantity), decimals),
                amountError = null,
            )
        }
    }

    /** The total is the figure that is kept, so the price gives way to it. */
    fun updateAmount(value: String) = sheet.update { current ->
        val open = current ?: return@update null
        val decimals = decimalsOf(open.currencyCode)
        val amount = MoneyFormat.parseAmount(value, decimals)
        val quantity = MoneyFormat.parseQuantity(open.quantity)
        val price = if (amount == null) open.price else format(unitPrice(amount, quantity), decimals)
        open.copy(amount = value, price = price, amountError = null)
    }

    fun confirmSheet() {
        val open = sheet.value ?: return
        when (val checked = open.check(decimalsOf(open.currencyCode))) {
            is BudgetSheetCheck.Refused -> sheet.value = checked.sheet
            is BudgetSheetCheck.Accepted -> viewModelScope.launch {
                repository.save(
                    BudgetItem(
                        id = open.id,
                        tripId = tripId,
                        categoryId = checked.categoryId,
                        title = checked.title,
                        notes = checked.notes,
                        quantityMilli = checked.quantityMilli,
                        amountMilli = checked.amountMilli,
                        currencyCode = checked.currencyCode,
                    )
                )
                lastCurrencyCode = checked.currencyCode
                sheet.value = null
            }
        }
    }

    // endregion

    /** A line is one line to type again, so it goes without being asked about twice. */
    fun delete(details: BudgetItemDetails) {
        viewModelScope.launch { repository.delete(details.item.id) }
    }

    /** Two while no currency has been named: most currencies have two, and none has three. */
    private fun decimalsOf(code: String?): Int =
        state.value.currency(code)?.decimals ?: DEFAULT_DECIMALS

    private fun format(amount: Money?, decimals: Int): String =
        amount?.let { MoneyFormat.formatAmountForInput(it, decimals) }.orEmpty()

    /**
     * A price for so many of a thing. Through [BigDecimal] because thousandths times
     * thousandths overflows a Long well inside what a trip in dong can cost.
     */
    private fun multiply(price: Money, quantity: Quantity): Money = Money(
        BigDecimal.valueOf(price.milli)
            .multiply(BigDecimal.valueOf(quantity.milli))
            .divide(BigDecimal.valueOf(1_000L), 0, RoundingMode.HALF_UP)
            .longValueExact()
    )

    private companion object {
        const val DEFAULT_DECIMALS = 2
    }
}
