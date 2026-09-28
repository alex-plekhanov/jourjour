package app.jourjour.ui.entry

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.jourjour.R
import app.jourjour.core.export.ReceiptsZip
import app.jourjour.core.export.TripColumn
import app.jourjour.core.export.TripCsv
import app.jourjour.core.export.TripCsvImport
import app.jourjour.core.export.TripXlsx
import app.jourjour.core.money.Money
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.core.text.resolveName
import app.jourjour.core.text.walletName
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject

/** The tally for one currency: how much went out and how much came in. */
@Immutable
data class CurrencyTotal(
    val currency: Currency,
    val expense: Money = Money.ZERO,
    val income: Money = Money.ZERO,
)

/** One day's entries together with that day's tally. */
@Immutable
data class DayGroup(
    val date: LocalDate,
    val entries: List<EntryDetails>,
    val totals: List<CurrencyTotal>,
)

@Immutable
data class EntriesState(
    val trip: Trip? = null,
    val days: List<DayGroup> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    val isLoading: Boolean = true,
    /** How many entries carry a receipt photo — nothing to archive when this is zero. */
    val photoCount: Int = 0,
    /** What is ticked in the column dialog, or null while it is closed. */
    val columnSelection: Set<TripColumn>? = null,
    /**
     * What is typed in the search box, or null while the box is closed. Empty means the
     * box is open and still empty, which shows everything — the same as closed, but the
     * cursor is waiting.
     */
    val search: String? = null,
)

@HiltViewModel
class EntriesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val entryRepository: EntryRepository,
    /** The dictionaries an imported row is matched against by name. */
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val currencyRepository: CurrencyRepository,
    /** Remembers which columns the last spreadsheet was written with. */
    private val appPreferences: AppPreferences,
    /** Where the receipt photos the archive gathers actually live. */
    private val photoStorage: PhotoStorage,
    /** The trip's cover, drawn behind the top of the screen. */
    val coverStorage: TripCoverStorage,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the screen has to say after an action: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** What the screen has open on top of the list, as opposed to what the database says. */
    private data class Overlays(
        val columns: Set<TripColumn>? = null,
        val search: String? = null,
    )

    private val overlays = MutableStateFlow(Overlays())

    /**
     * Everything the trip has, before a search narrows it down. An export writes the trip,
     * not the view of it: a file named after the trip that quietly held a tenth of it
     * because a search box was open would be found out far too late.
     */
    private var allEntries: List<EntryDetails> = emptyList()

    val state: StateFlow<EntriesState> = combine(
        tripRepository.observeById(tripId),
        entryRepository.observeByTrip(tripId),
        overlays,
    ) { trip, entries, open ->
        allEntries = entries
        val shown = open.search?.let { query -> entries.filter { matches(it, query) } } ?: entries
        EntriesState(
            trip = trip,
            days = groupByDay(shown, tallied = open.search == null),
            // A search is a question about which entries there are, not about what they
            // came to; a tally of the matches would read as the trip's own.
            totals = if (open.search == null) totals(entries) else emptyList(),
            isLoading = false,
            // Counted off the entries already at hand rather than asked for separately.
            photoCount = entries.count { it.entry.photoFile != null },
            columnSelection = open.columns,
            search = open.search,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = EntriesState(),
    )

    /** Entries already come newest first, so the days fall out in the same order. */
    private fun groupByDay(entries: List<EntryDetails>, tallied: Boolean): List<DayGroup> =
        entries.groupBy { it.entry.dateTime.toLocalDate() }
            .map { (date, dayEntries) ->
                DayGroup(date, dayEntries, if (tallied) totals(dayEntries) else emptyList())
            }

    /**
     * Whether an entry answers to what was typed: its category, its title, its notes or
     * its place. Not its amounts — a search for "12" would then turn up every entry that
     * happened to cost that, and the four fields above are the ones a person writes in.
     *
     * The category is matched on the name as it is read, so that a search in the device's
     * language finds a row seeded in another one.
     */
    private fun matches(details: EntryDetails, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val entry = details.entry
        return sequenceOf(
            entry.title,
            entry.notes,
            entry.place,
            dictionaryName(details.category.name, details.category.nameKey),
        ).any { it.lowercase().contains(needle) }
    }

    /** Opens the search box, empty, with everything still on show. */
    fun openSearch() = overlays.update { it.copy(search = "") }

    fun updateSearch(query: String) = overlays.update { it.copy(search = query) }

    fun closeSearch() = overlays.update { it.copy(search = null) }

    /**
     * Every currency is tallied on its own: yen cannot be added to dollars without a
     * rate, and the app holds no rates.
     */
    private fun totals(entries: List<EntryDetails>): List<CurrencyTotal> {
        val byCurrency = linkedMapOf<String, CurrencyTotal>()

        entries.forEach { details ->
            details.expenseCurrency?.let { currency ->
                byCurrency.merge(currency, Money(details.entry.expenseAmountMilli ?: 0), expense = true)
            }
            details.incomeCurrency?.let { currency ->
                byCurrency.merge(currency, Money(details.entry.incomeAmountMilli ?: 0), expense = false)
            }
        }
        return byCurrency.values.toList()
    }

    private fun MutableMap<String, CurrencyTotal>.merge(
        currency: Currency,
        amount: Money,
        expense: Boolean,
    ) {
        val current = getOrPut(currency.code) { CurrencyTotal(currency) }
        this[currency.code] = if (expense) {
            current.copy(expense = current.expense + amount)
        } else {
            current.copy(income = current.income + amount)
        }
    }

    // region Export

    /** Writes the trip's entries as a CSV table into the file the person picked. */
    fun exportTo(target: Uri) {
        val entries = exportable()
        export(target, R.string.export_done) { output ->
            val csv = TripCsv.build(
                headers = headers(),
                entries = entries,
                separator = context.getString(R.string.csv_separator),
                nameOf = ::dictionaryName,
                walletNameOf = ::walletLabel,
            )
            output.write(csv.toByteArray(Charsets.UTF_8))
            entries.size
        }
    }

    /**
     * The same table as a spreadsheet, where a number stays a number and a date a date,
     * carrying only the columns that were ticked.
     */
    fun exportXlsxTo(target: Uri) {
        val entries = exportable()
        val sheetName = state.value.trip?.name.orEmpty()
        val columns = TripColumn.entries.filter { it in chosenColumns }
        export(target, R.string.export_done) { output ->
            TripXlsx.write(
                sheetName = sheetName,
                columns = columns,
                titleOf = ::header,
                entries = entries,
                nameOf = ::dictionaryName,
                walletNameOf = ::walletLabel,
                target = output,
            )
        }
    }

    // region Choosing what a spreadsheet carries

    /**
     * What the last sheet was written with, kept apart from the dialog so that closing it
     * without confirming changes nothing.
     */
    private var chosenColumns: Set<TripColumn> = TripColumn.entries.toSet()

    /** Opens the dialog on last time's choice, or on everything if there was no last time. */
    fun chooseColumns() {
        viewModelScope.launch {
            val saved = appPreferences.spreadsheetColumns.first()
                .mapNotNull { name -> TripColumn.entries.firstOrNull { it.name == name } }
                .toSet()
            chosenColumns = saved.ifEmpty { TripColumn.entries.toSet() }
            overlays.update { it.copy(columns = chosenColumns) }
        }
    }

    fun toggleColumn(column: TripColumn) = overlays.update { open ->
        val columns = open.columns ?: return@update open
        open.copy(
            columns = if (column in columns) columns - column else columns + column
        )
    }

    fun closeColumns() = overlays.update { it.copy(columns = null) }

    /**
     * Takes the ticks as the choice and remembers them. The export itself starts after
     * this, once the person has said where the file goes.
     */
    fun confirmColumns() {
        val columns = overlays.value.columns.orEmpty()
        if (columns.isEmpty()) return
        chosenColumns = columns
        closeColumns()
        viewModelScope.launch {
            appPreferences.setSpreadsheetColumns(columns.map { it.name }.toSet())
        }
    }

    // endregion

    /**
     * A title for every column there is. The `when` is exhaustive on purpose: a new column
     * fails to compile until it has been given a name here.
     */
    private fun headers(): List<String> = TripColumn.csv.map(::header)

    /** What a column is called, for the dialog that asks which of them to write. */
    fun columnTitle(column: TripColumn): String = header(column)

    private fun header(column: TripColumn): String {
        return context.getString(
            when (column) {
                TripColumn.DATE -> R.string.csv_date
                TripColumn.TIME -> R.string.csv_time
                TripColumn.CATEGORY -> R.string.csv_category
                TripColumn.TITLE -> R.string.csv_title
                TripColumn.PLACE -> R.string.csv_place
                TripColumn.QUANTITY -> R.string.csv_quantity
                TripColumn.EXPENSE_AMOUNT -> R.string.csv_expense_amount
                TripColumn.EXPENSE_CURRENCY -> R.string.csv_expense_currency
                TripColumn.EXPENSE_WALLET -> R.string.csv_expense_wallet
                TripColumn.INCOME_WALLET -> R.string.csv_income_wallet
                TripColumn.EXPENSE_PAYMENT_METHOD -> R.string.csv_expense_payment_method
                TripColumn.INCOME_AMOUNT -> R.string.csv_income_amount
                TripColumn.INCOME_CURRENCY -> R.string.csv_income_currency
                TripColumn.INCOME_PAYMENT_METHOD -> R.string.csv_income_payment_method
                TripColumn.EXPENSE_SETTLEMENT_AMOUNT -> R.string.csv_expense_settlement_amount
                TripColumn.EXPENSE_SETTLEMENT_CURRENCY -> R.string.csv_expense_settlement_currency
                TripColumn.INCOME_SETTLEMENT_AMOUNT -> R.string.csv_income_settlement_amount
                TripColumn.INCOME_SETTLEMENT_CURRENCY -> R.string.csv_income_settlement_currency
                TripColumn.COORDINATES -> R.string.csv_coordinates
                TripColumn.NOTES -> R.string.csv_notes
            }
        )
    }

    /** Gathers the trip's receipt photos into one archive. */
    fun exportReceiptsTo(target: Uri) {
        val entries = exportable()
        export(target, R.string.export_receipts_done) { output ->
            ReceiptsZip.write(entries, ::dictionaryName, photoStorage::file, output)
        }
    }

    /**
     * Reads a table back into the trip.
     *
     * The entries are added to whatever is already there rather than replacing it, and
     * nothing at all is written unless every row of the file made sense: half an import
     * would leave no way of telling by eye which half arrived.
     */
    fun importFrom(source: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(source)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(source.toString())
                }

                val result = TripCsvImport.read(
                    text = text,
                    headers = headers(),
                    tripId = tripId,
                    categories = categoryRepository.getAll(),
                    wallets = tripRepository.getWallets(tripId),
                    currencies = currencyRepository.getAll(),
                    nameOf = ::dictionaryName,
                    walletNameOf = ::walletLabel,
                )

                when (result) {
                    is TripCsvImport.Result.Ready -> {
                        entryRepository.insertAll(result.entries)
                        _messages.send(
                            context.getString(R.string.import_done, result.entries.size)
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

    /** Says what stopped the import, in terms of the file the person is looking at. */
    private fun describe(result: TripCsvImport.Result): String = when (result) {
        TripCsvImport.Result.HeaderMismatch ->
            context.getString(R.string.error_import_header)
        is TripCsvImport.Result.MissingColumns ->
            context.getString(
                R.string.error_import_missing_columns,
                result.columns.joinToString { header(it) },
            )
        is TripCsvImport.Result.Empty ->
            context.getString(R.string.error_import_empty, result.line, header(result.column))
        is TripCsvImport.Result.Unexpected ->
            context.getString(
                R.string.error_import_unexpected,
                result.line,
                header(result.column),
                result.value,
            )
        is TripCsvImport.Result.WrongCurrency ->
            context.getString(
                R.string.error_import_wrong_currency,
                result.line,
                header(result.column),
                result.value,
                walletLabel(result.wallet),
                result.wallet.currency.code,
            )
        is TripCsvImport.Result.Unknown ->
            context.getString(
                R.string.error_import_unknown,
                result.line,
                header(result.column),
                result.value,
            )
        is TripCsvImport.Result.Malformed ->
            context.getString(
                R.string.error_import_malformed,
                result.line,
                header(result.column),
                result.value,
            )
        is TripCsvImport.Result.Ready -> ""
    }

    /**
     * What an export works from: the entries the screen already holds rather than a fresh
     * query. An export can only be asked for once the list is up. The whole trip, not the
     * part a search has left on screen — see [allEntries].
     */
    private fun exportable(): List<EntryDetails> = allEntries

    private fun dictionaryName(name: String, nameKey: String?): String =
        resolveName(context, name, nameKey)

    /** What a wallet is called in a file: the typed name, or the one it derives. */
    private fun walletLabel(details: TripWalletDetails): String = walletName(context, details)

    /**
     * Runs an export and says how it went. [write] answers with how many things it wrote,
     * which is what [done] is given to put in the message.
     */
    private fun export(target: Uri, @StringRes done: Int, write: (OutputStream) -> Int) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use(write)
                        ?: error(target.toString())
                }
                _messages.send(context.getString(done, count))
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_export, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    // endregion
}
