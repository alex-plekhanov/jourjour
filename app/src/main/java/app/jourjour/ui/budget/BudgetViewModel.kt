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
import app.jourjour.core.budget.CurrencyAmount
import app.jourjour.core.budget.plannedByCurrency
import app.jourjour.core.budget.scheduledDeposits
import app.jourjour.core.budget.scheduledByCurrency
import app.jourjour.core.budget.spentByCurrency
import app.jourjour.core.budget.BudgetVersusActual
import app.jourjour.core.budget.onlyNew
import app.jourjour.core.export.BudgetColumn
import app.jourjour.core.export.BudgetCsv
import app.jourjour.core.export.BudgetCsvImport
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.rates.convertBetween
import app.jourjour.core.rates.quotesTo
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.ScheduleEventDetails
import app.jourjour.data.entity.TripBudgetCategory
import app.jourjour.data.entity.TripRate
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.repository.BudgetRepository
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.ScheduleRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import javax.inject.Inject

/** The lines of one category, as the list shows them, and what they come to beside the fact. */
@Immutable
data class BudgetCategoryBlock(
    val category: Category,
    val items: List<BudgetItemDetails>,
    /** What was budgeted, currency by currency; what a person wrote, in the money they wrote it in. */
    val planned: List<CurrencyAmount> = emptyList(),
    /** What the schedule expects it to cost — the finer plan, the same way. */
    val scheduled: List<CurrencyAmount> = emptyList(),
    /** The deposits inside the schedule's figure, the same way. */
    val scheduledDeposits: List<CurrencyAmount> = emptyList(),
    /** What has gone on it so far, the same way. Empty until something has. */
    val spent: List<CurrencyAmount> = emptyList(),
    val isSpendingClosed: Boolean = false,
    val isPlanningClosed: Boolean = false,
)

@Immutable
data class BudgetState(
    val blocks: List<BudgetCategoryBlock> = emptyList(),
    /** The budget read against the spending. Null while there is neither. */
    val standing: BudgetVersusActual? = null,
    /** The categories whose spending has been declared finished. */
    val closedCategories: Set<Long> = emptySet(),
    /** Whether the prompt for closing the whole budget at once is up. */
    val isClosingAll: Boolean = false,
    /** The currency converter while it is open. */
    val converter: ConverterSheet? = null,
    /** What a line may be filed under: what a person spends on, never what comes back. */
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    /** What a line may say it will be paid with, where anybody says. */
    val paymentMethods: List<PaymentMethod> = emptyList(),
    /** The trip's rates, which the converter reads; nothing on the screen shows them. */
    val rates: List<TripRate> = emptyList(),
    /** The favourite titles of the category the open sheet names. */
    val favorites: List<String> = emptyList(),
    val sheet: BudgetSheet? = null,
    val isLoading: Boolean = true,
) {
    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }

    /** Whether there is anything left to close: a budget, with a category still open in it. */
    val canCloseAll: Boolean get() = blocks.any { !it.isSpendingClosed }

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
    entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    paymentMethodRepository: PaymentMethodRepository,
    scheduleRepository: ScheduleRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val sheet = MutableStateFlow<BudgetSheet?>(null)

    /** The favourite titles of whatever category the open sheet names. */
    private val favorites = MutableStateFlow<List<CategoryFavorite>>(emptyList())

    /** Whether the prompt for closing every category at once is up. */
    private val closingAll = MutableStateFlow(false)

    /** The converter while it is open, and nothing while it is not. */
    private val converter = MutableStateFlow<ConverterSheet?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the tab has to say after a table went out or came in: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** The currency a new line starts in, remembered from the last one written. */
    private var lastCurrencyCode: String? = null

    /** What the budget is made of, and the entries it is read against. */
    private val sources = combine(
        repository.observeByTrip(tripId),
        entryRepository.observeByTrip(tripId),
        repository.observeCategories(tripId),
        tripRepository.observeRates(tripId),
        appPreferences.baseCurrency,
    ) { items, entries, settled, rates, baseCode ->
        Sources(items, entries, settled, rates, baseCode)
    }

    private val written = combine(
        sources,
        currencyRepository.observeAll(),
        categoryRepository.observeAll(),
        scheduleRepository.observeByTrip(tripId),
        paymentMethodRepository.observeAll(),
    ) { sources, currencies, categories, schedule, paymentMethods ->
        val base = currencies.firstOrNull { it.code == sources.baseCode }
        val closed = sources.settled
            .filter { it.isSpendingClosed }
            .mapTo(mutableSetOf()) { it.categoryId }
        val planningClosed = sources.settled
            .filter { it.isPlanningClosed }
            .mapTo(mutableSetOf()) { it.categoryId }
        BudgetState(
            blocks = blocksOf(sources.items, sources.entries, schedule, closed, planningClosed),
            standing = BudgetVersusActual.of(
                items = sources.items,
                entries = sources.entries,
                schedule = schedule,
                base = base,
                rates = sources.rates,
                closed = closed,
                planningClosed = planningClosed,
            ),
            closedCategories = closed,
            // Money planned is money going out. A category that only ever brings money in
            // has nothing to budget, and an exchange moves a person's own money about.
            categories = categories.filter { it.isExpense && !it.isIncome },
            currencies = currencies,
            paymentMethods = paymentMethods,
            rates = sources.rates,
            isLoading = false,
        )
    }

    /** The five flows the screen is built from, carried together for want of a wider combine. */
    private data class Sources(
        val items: List<BudgetItemDetails>,
        val entries: List<EntryDetails>,
        val settled: List<TripBudgetCategory>,
        val rates: List<TripRate>,
        val baseCode: String?,
    )

    val state: StateFlow<BudgetState> = combine(
        written,
        sheet,
        favorites,
        closingAll,
        converter,
    ) { base, openSheet, favorites, closingAll, converter ->
        base.copy(
            sheet = openSheet,
            // Shown the way they are stored, which for a preloaded favourite is the
            // translation the device asks for.
            favorites = favorites.map { resolveName(context, it.name, it.nameKey) },
            isClosingAll = closingAll,
            converter = converter,
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

    /**
     * The lines under their categories, in the order the query already put them in — and
     * after them the categories the schedule knows about and the budget never did.
     *
     * Those have no lines and no budget of their own to total, but they have a plan, and
     * a plan that is not on the screen is a plan nobody can read against anything.
     */
    private fun blocksOf(
        items: List<BudgetItemDetails>,
        entries: List<EntryDetails>,
        schedule: List<ScheduleEventDetails>,
        closed: Set<Long>,
        planningClosed: Set<Long>,
    ): List<BudgetCategoryBlock> {
        val planned = plannedByCurrency(items)
        val scheduled = scheduledByCurrency(schedule)
        val bookedDeposits = scheduledDeposits(schedule)
        val spent = spentByCurrency(entries)
        val written = items.groupBy { it.category.id }
        val booked = schedule
            .mapNotNull { it.category }
            .filter { it.id in scheduled && it.id !in written }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        return written.map { (categoryId, group) -> group.first().category to group }
            .plus(booked.map { it to emptyList<BudgetItemDetails>() })
            .map { (category, group) ->
                BudgetCategoryBlock(
                    category = category,
                    items = group,
                    planned = planned[category.id].orEmpty(),
                    scheduled = scheduled[category.id].orEmpty(),
                    scheduledDeposits = bookedDeposits[category.id].orEmpty(),
                    spent = spent[category.id].orEmpty(),
                    isSpendingClosed = category.id in closed,
                    isPlanningClosed = category.id in planningClosed,
                )
            }
    }

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

    /**
     * How the line is meant to be paid for, or nothing at all.
     *
     * Optional throughout: a budget written months ahead says what a thing costs, and
     * which pocket it comes out of is often the last thing anybody knows about it.
     */
    fun selectPaymentMethod(paymentMethod: PaymentMethod?) = sheet.update {
        it?.copy(paymentMethodId = paymentMethod?.id)
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
                        paymentMethodId = checked.paymentMethodId,
                    )
                )
                lastCurrencyCode = checked.currencyCode
                sheet.value = null
            }
        }
    }

    // endregion

    /**
     * Takes away the line the form is open on, which is the only place it is offered
     * from. A line is one line to type again, so it goes without being asked about twice.
     */
    fun deleteOpen() {
        val open = sheet.value ?: return
        val details = state.value.blocks
            .flatMap { it.items }
            .firstOrNull { it.item.id == open.id }
            ?: return
        viewModelScope.launch {
            repository.delete(details)
            sheet.value = null
        }
    }

    /**
     * Declares a category's spending finished, or opens it again.
     *
     * A statement about the category rather than about a line: what is matched to a budget
     * is matched by category, so "there will be no more of this" is not something one line
     * of several could mean on its own.
     */
    fun toggleSpendingClosed(category: Category) {
        val closed = category.id in state.value.closedCategories
        viewModelScope.launch {
            repository.setSpendingClosed(tripId, category.id, !closed)
        }
    }

    /**
     * Declares the plan of a category complete, or opens it again.
     *
     * What changes is which of two answers the base-currency column takes: while it is
     * open the budget's own estimate holds, and once it is closed the schedule replaces
     * it — ten nights at fifty becomes the nights actually booked, at what they came to.
     */
    fun togglePlanningClosed(category: Category) {
        val closed = state.value.blocks.any { it.category.id == category.id && it.isPlanningClosed }
        viewModelScope.launch {
            repository.setPlanningClosed(tripId, category.id, !closed)
        }
    }

    /**
     * The same said about the whole trip at once, which is what a person does when they
     * are home and the last card statement has come in.
     *
     * Asked about first: it is one tap over a screenful of categories, and the way back is
     * category by category.
     */
    fun askCloseAll() = closingAll.update { true }

    fun cancelCloseAll() = closingAll.update { false }

    fun confirmCloseAll() {
        val open = state.value.blocks.filterNot { it.isSpendingClosed }
        closingAll.value = false
        viewModelScope.launch {
            open.forEach { repository.setSpendingClosed(tripId, it.category.id, true) }
        }
    }

    // region The converter

    /**
     * Opens the converter on the two currencies a person is most likely to want: the one
     * they were last dealing in, and the one they reckon in.
     *
     * What it offers is what the trip has rates for, and the base currency those rates
     * point at — the only road between two currencies this app has. The rates are the
     * person's own, which is the first thing the sheet says.
     */
    fun openConverter() {
        viewModelScope.launch {
            val base = currentBase()
            val convertible = convertible(base)
            converter.value = ConverterSheet(
                currencies = convertible,
                base = base,
                // Last used first, and anything rather than nothing: a converter opening
                // on the base currency twice over would convert nothing into itself.
                fromCode = convertible
                    .filter { it.code != base?.code }
                    .maxByOrNull { it.usedAt ?: LocalDateTime.MIN }
                    ?.code,
                toCode = base?.code,
            )
        }
    }

    fun closeConverter() {
        converter.value = null
    }

    /** The far end of a conversion follows the near one: the amount given stands. */
    fun converterUpdateFrom(value: String) = converter.update { open ->
        val sheet = open?.copy(from = value) ?: return@update null
        sheet.copy(to = sheet.convertFrom())
    }

    fun converterUpdateTo(value: String) = converter.update { open ->
        val sheet = open?.copy(to = value) ?: return@update null
        sheet.copy(from = sheet.convertTo())
    }

    /**
     * Choosing a currency leaves the amount beside it where it is and works the other one
     * out again: the figure a person just typed is the one they meant.
     */
    fun converterSelectFrom(code: String?) {
        converter.update { open ->
            val sheet = open?.copy(fromCode = code) ?: return@update null
            sheet.copy(to = sheet.convertFrom())
        }
        touch(code)
    }

    fun converterSelectTo(code: String?) {
        converter.update { open ->
            val sheet = open?.copy(toCode = code) ?: return@update null
            sheet.copy(from = sheet.convertTo())
        }
        touch(code)
    }

    private fun ConverterSheet.convertFrom(): String = converted(
        text = from,
        from = fromCurrency,
        to = toCurrency,
    )

    private fun ConverterSheet.convertTo(): String = converted(
        text = to,
        from = toCurrency,
        to = fromCurrency,
    )

    /** One side of the converter worked out from the other, or blank where it cannot be. */
    private fun converted(text: String, from: Currency?, to: Currency?): String {
        if (from == null || to == null) return ""
        val amount = MoneyFormat.parseAmount(text, from.decimals) ?: return ""
        val quotes = quotesTo(converterBase ?: return "", state.value.rates)
        val result = convertBetween(amount, from.code, to.code, quotes) ?: return ""
        return MoneyFormat.formatAmountForInput(result, to.decimals)
    }

    private val converterBase: Currency? get() = converter.value?.base


    private suspend fun currentBase(): Currency? {
        val code = appPreferences.baseCurrency.first()
        return state.value.currencies.firstOrNull { it.code == code }
    }

    /** What a conversion may name: everything with a rate to the base, and the base. */
    private fun convertible(base: Currency?): List<Currency> {
        if (base == null) return emptyList()
        val withRates = state.value.rates
            .filter { it.toCurrencyCode == base.code }
            .mapNotNull { rate -> state.value.currencies.firstOrNull { it.code == rate.fromCurrencyCode } }
        return (withRates + base)
            .distinctBy { it.code }
            .sortedWith(compareBy<Currency> { it.sortOrder }.thenBy { it.code })
    }

    private fun touch(code: String?) {
        code?.let { viewModelScope.launch { currencyRepository.touch(it, LocalDateTime.now()) } }
    }

    // endregion

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
     * Reads a table in and merges it, the way a checklist merges: a line the budget
     * already carries is passed over. A budget names a thing once — "Accommodation —
     * Hotel" is written for the trip rather than for each hotel — so a line of the same
     * name under the same category is the line already there.
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
                        val fresh = onlyNew(result.items, repository.getByTrip(tripId))
                        repository.addAll(fresh)
                        _messages.send(
                            context.getString(
                                R.string.budget_imported,
                                fresh.size,
                                result.items.size - fresh.size,
                            )
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
