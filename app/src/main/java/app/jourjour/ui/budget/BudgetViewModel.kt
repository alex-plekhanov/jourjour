package app.jourjour.ui.budget

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.jourjour.R
import app.jourjour.core.budget.BudgetTotals
import app.jourjour.core.export.BudgetColumn
import app.jourjour.core.export.BudgetCsv
import app.jourjour.core.export.BudgetCsvImport
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
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
import java.time.LocalDateTime
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
    /** The favourite titles of the category the open sheet names. */
    val favorites: List<String> = emptyList(),
    val sheet: BudgetSheet? = null,
    val isLoading: Boolean = true,
) {
    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }

    /** Whether the name on the sheet is one of them, which is what the star shows. */
    val isTitleFavorite: Boolean
        get() = sheet?.title?.trim()?.takeIf { it.isNotEmpty() } in favorites
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
    @param:ApplicationContext private val context: Context,
    private val repository: BudgetRepository,
    tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val sheet = MutableStateFlow<BudgetSheet?>(null)

    /** The favourite titles of whatever category the open sheet names. */
    private val favorites = MutableStateFlow<List<CategoryFavorite>>(emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the tab has to say after a table went out or came in: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

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

    val state: StateFlow<BudgetState> = combine(
        written,
        sheet,
        favorites,
    ) { base, openSheet, favorites ->
        base.copy(
            sheet = openSheet,
            // Shown the way they are stored, which for a preloaded favourite is the
            // translation the device asks for.
            favorites = favorites.map { resolveName(context, it.name, it.nameKey) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BudgetState(),
    )

    init {
        viewModelScope.launch {
            sheet.map { it?.categoryId }
                .distinctUntilChanged()
                .flatMapLatest { categoryId ->
                    if (categoryId == null) flowOf(emptyList())
                    else categoryRepository.observeFavorites(categoryId)
                }
                .collect { favorites.value = it }
        }
    }

    /** The lines under their categories, in the order the query already put them in. */
    private fun blocksOf(items: List<BudgetItemDetails>): List<BudgetCategoryBlock> =
        items.groupBy { it.category.id }
            .map { (_, group) -> BudgetCategoryBlock(group.first().category, group) }

    // region The sheet

    /** @param details null to write a new line. */
    fun openSheet(details: BudgetItemDetails? = null) {
        viewModelScope.launch {
            sheet.value = details?.let(BudgetSheet::of)
                // A new line starts on the category sheet, as a new entry does: nothing
                // else on the form can be filled in without knowing what the line is for,
                // and the category brings its favourite titles with it. The currency is
                // the one the last line was written in, and failing that the one the
                // person reckons in; the quantity is one, which most lines are.
                ?: BudgetSheet(
                    quantity = "1",
                    currencyCode = lastCurrencyCode ?: appPreferences.baseCurrency.first(),
                    isCategorySheetVisible = true,
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

    fun toggleNotes() = sheet.update { it?.copy(isNotesVisible = !it.isNotesVisible) }

    /** A favourite title of the category fills the name in. */
    fun pickFavorite(name: String) = sheet.update { it?.copy(title = name) }

    /** The star beside the name: adds it to the category's favourites and takes it out. */
    fun toggleTitleFavorite() {
        val open = sheet.value ?: return
        val categoryId = open.categoryId ?: return
        val title = open.title.trim()
        if (title.isEmpty()) return

        // The field holds what the chip showed, which for a preloaded favourite is the
        // translation; the row it belongs to is deleted under the name it was stored with.
        val existing = favorites.value
            .firstOrNull { resolveName(context, it.name, it.nameKey) == title }

        viewModelScope.launch {
            if (existing != null) {
                categoryRepository.removeFavorite(categoryId, existing.name)
            } else {
                categoryRepository.addFavorite(categoryId, title)
            }
        }
    }

    fun selectCurrency(code: String?) {
        sheet.update { current ->
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
        // Choosing a currency is using it, whether or not the line is ever saved: what the
        // picker offers first should follow what a person is dealing in now.
        code?.let { viewModelScope.launch { currencyRepository.touch(it, LocalDateTime.now()) } }
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

    // region The table

    /** Writes the budget to [target] as a CSV table. */
    fun exportTo(target: Uri) {
        viewModelScope.launch {
            val items = repository.getByTrip(tripId)
            try {
                withContext(Dispatchers.IO) {
                    val csv = BudgetCsv.build(
                        headers = headers(),
                        items = items,
                        separator = context.getString(R.string.csv_separator),
                        nameOf = { name, nameKey -> resolveName(context, name, nameKey) },
                    )
                    context.contentResolver.openOutputStream(target)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error(target.toString())
                }
                _messages.send(context.getString(R.string.budget_exported, items.size))
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_export, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /**
     * Reads a table in and adds what it says.
     *
     * Adds rather than merges, unlike a checklist: a budget line has no name it is known
     * by — two nights in two different places are two lines that may read the same — so
     * there is nothing to recognise a duplicate by. Importing the same file twice writes
     * it twice, which is visible on the screen and undone a line at a time.
     */
    fun importFrom(source: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(source.toString())
                }

                val result = BudgetCsvImport.read(
                    text = text,
                    headers = headers(),
                    tripId = tripId,
                    categories = state.value.categories,
                    currencies = state.value.currencies,
                    nameOf = { name, nameKey -> resolveName(context, name, nameKey) },
                )
                when (result) {
                    is BudgetCsvImport.Result.Ready -> {
                        repository.addAll(result.items)
                        _messages.send(
                            context.getString(R.string.budget_imported, result.items.size)
                        )
                    }
                    else -> _messages.send(describe(result))
                }
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_import, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /** The column titles in the language the device is set to, which is what a file carries. */
    private fun headers(): List<String> =
        BudgetColumn.entries.map { context.getString(it.titleRes) }

    /** Says what stopped the import, in terms of the file the person is looking at. */
    private fun describe(result: BudgetCsvImport.Result): String = when (result) {
        BudgetCsvImport.Result.HeaderMismatch ->
            context.getString(R.string.error_import_header)

        is BudgetCsvImport.Result.MissingColumns -> context.getString(
            R.string.error_import_missing_columns,
            result.columns.joinToString { context.getString(it.titleRes) },
        )

        is BudgetCsvImport.Result.Empty -> context.getString(
            R.string.error_import_empty_column,
            result.line,
            context.getString(result.column.titleRes),
        )

        is BudgetCsvImport.Result.Unknown -> context.getString(
            R.string.error_import_unknown,
            result.line,
            context.getString(result.column.titleRes),
            result.value,
        )

        is BudgetCsvImport.Result.Malformed -> context.getString(
            R.string.error_import_malformed,
            result.line,
            context.getString(result.column.titleRes),
            result.value,
        )

        is BudgetCsvImport.Result.NotAnExpense ->
            context.getString(R.string.error_import_not_an_expense, result.line, result.value)

        is BudgetCsvImport.Result.Ready -> ""
    }

    // endregion

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
