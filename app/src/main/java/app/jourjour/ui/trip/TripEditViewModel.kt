package app.jourjour.ui.trip

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.rates.FetchedRate
import app.jourjour.core.rates.RateLookup
import app.jourjour.core.rates.RateLookupError
import app.jourjour.core.rates.RateLookupFailure
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.repository.ScheduleRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.common.Commit
import app.jourjour.ui.common.EditorEvent
import app.jourjour.ui.common.WalletForm
import app.jourjour.ui.common.WalletFormCheck
import app.jourjour.ui.navigation.Route
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * A wallet inside the form. It keeps the id of the row it stands for — zero while the
 * wallet has never been saved — because entries point at wallets, and recreating them on
 * every save would cut those entries loose.
 */
data class WalletDraft(
    val id: Long = 0,
    val paymentMethodId: Long,
    val currencyCode: String,
    val name: String? = null,
    val openingBalance: Money = Money.ZERO,
    val lastUsedAt: LocalDateTime? = null,
)

/** The open wallet sheet: the fields, and which draft they are about. */
data class TripWalletSheet(
    /** null while adding a wallet; otherwise the index of the one being edited. */
    val editingIndex: Int? = null,
    val form: WalletForm = WalletForm(),
) {
    val isNew: Boolean get() = editingIndex == null
}

/**
 * A rate inside the form. It keeps the id of the row it stands for, so that editing one
 * changes that row rather than replacing it, the same way a wallet does — though unlike a
 * wallet nothing points at a rate, so losing one would cost only the rate itself.
 */
data class RateDraft(
    val id: Long = 0,
    val fromCurrencyCode: String,
    val toCurrencyCode: String,
    val rate: Money,
    val multiplier: Long,
    val note: String = "",
    val noteFromLookup: Boolean = false,
)

/** The open rate sheet: the fields, and which draft they are about. */
data class TripRateSheet(
    val editingIndex: Int? = null,
    val form: RateForm,
) {
    val isNew: Boolean get() = editingIndex == null
}

data class TripEditState(
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusWeeks(1),
    /** Cover photo: a file name in the trip covers folder, or null when there is none. */
    val photoFile: String? = null,
    val wallets: List<WalletDraft> = emptyList(),
    val rates: List<RateDraft> = emptyList(),
    /** The base currency from the settings, or null while none has been chosen. */
    val baseCurrency: String? = null,
    val currencies: List<Currency> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val startDateError: Int? = null,
    @param:StringRes val endDateError: Int? = null,
    val isEditMode: Boolean = false,
    val isDeleteDialogVisible: Boolean = false,
    /** How many entries would go with the trip — the prompt says so before deleting. */
    val entryCount: Int = 0,
    /** How many entries of this trip carry a receipt photo. */
    val photoCount: Int = 0,
    val isPhotoDeleteDialogVisible: Boolean = false,
    val walletSheet: TripWalletSheet? = null,
    val rateSheet: TripRateSheet? = null,
) {
    val hasErrors: Boolean get() = nameError != null || startDateError != null || endDateError != null

    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }

    fun paymentMethod(id: Long?): PaymentMethod? = paymentMethods.firstOrNull { it.id == id }

    /**
     * A wallet that has been saved keeps its currency for good: entries and recounts are
     * amounts in it, and changing it would silently reinterpret every one of them.
     */
    val walletCurrencyIsLocked: Boolean
        get() = walletSheet?.editingIndex?.let { wallets.getOrNull(it)?.id != 0L } == true

    /**
     * Rates whose far end is no longer what the settings call the base currency. They are
     * left exactly as they were — rewriting them would change what the figures mean — and
     * the form says so instead, listing the codes it found.
     *
     * Empty while no base currency is set at all: there the form already says that nothing
     * can be converted, and a second line naming every rate as wrong would only repeat it.
     */
    val ratesAgainstAnotherBase: List<String>
        get() = when (baseCurrency) {
            null -> emptyList()
            else -> rates.map { it.toCurrencyCode }.distinct().filter { it != baseCurrency }
        }
}

@HiltViewModel
class TripEditViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val entryRepository: EntryRepository,
    private val photoStorage: PhotoStorage,
    /** Also handed to the form, which draws the cover it is about to save. */
    val coverStorage: TripCoverStorage,
    private val rateLookup: RateLookup,
    private val appPreferences: AppPreferences,
    private val scheduleRepository: ScheduleRepository,
    private val currencyRepository: CurrencyRepository,
    paymentMethodRepository: PaymentMethodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.TripEdit>().tripId

    private val form = MutableStateFlow(TripEditState(isEditMode = tripId != Route.NEW))

    val state: StateFlow<TripEditState> = combine(
        form,
        currencyRepository.observeAll(),
        paymentMethodRepository.observeAll(),
        entryRepository.observePhotoCount(tripId),
        appPreferences.baseCurrency,
    ) { form, currencies, paymentMethods, photoCount, baseCurrency ->
        form.copy(
            currencies = currencies,
            paymentMethods = paymentMethods,
            photoCount = photoCount,
            baseCurrency = baseCurrency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = form.value,
    )

    private val commit = Commit()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var original: Trip? = null

    /** Wallets the trip had when the form opened: what is missing from it gets deleted. */
    private var loadedWalletIds: List<Long> = emptyList()

    /** The same for rates, which are also written one by one rather than recreated. */
    private var loadedRateIds: List<Long> = emptyList()

    init {
        if (tripId != Route.NEW) load(tripId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val trip = tripRepository.getById(id)
            if (trip == null) {
                _events.send(EditorEvent.Closed)
                return@launch
            }
            original = trip
            val wallets = tripRepository.getWallets(id).map { details ->
                WalletDraft(
                    id = details.wallet.id,
                    paymentMethodId = details.wallet.paymentMethodId,
                    currencyCode = details.wallet.currencyCode,
                    name = details.wallet.name,
                    openingBalance = Money(details.wallet.openingBalanceMilli),
                    lastUsedAt = details.wallet.lastUsedAt,
                )
            }
            loadedWalletIds = wallets.map { it.id }
            val rates = tripRepository.getRates(id).map { rate ->
                RateDraft(
                    id = rate.id,
                    fromCurrencyCode = rate.fromCurrencyCode,
                    toCurrencyCode = rate.toCurrencyCode,
                    rate = Money(rate.rateMilli),
                    multiplier = rate.multiplier,
                    note = rate.note,
                    noteFromLookup = rate.noteFromLookup,
                )
            }
            loadedRateIds = rates.map { it.id }
            form.value = TripEditState(
                name = trip.name,
                startDate = trip.startDate,
                endDate = trip.endDate,
                photoFile = trip.photoFile,
                wallets = wallets,
                rates = rates,
                isEditMode = true,
                entryCount = tripRepository.countEntries(id),
            )
        }
    }

    // region Trip fields

    fun updateName(value: String) = update { it.copy(name = value) }

    fun updateStartDate(value: LocalDate) = update {
        // The end follows the start so the period is never left plainly wrong.
        val end = if (it.endDate < value) value else it.endDate
        it.copy(startDate = value, endDate = end)
    }

    fun updateEndDate(value: LocalDate) = update { it.copy(endDate = value) }

    private fun update(transform: (TripEditState) -> TripEditState) {
        form.update { validate(transform(it)) }
    }

    private fun validate(state: TripEditState) = state.copy(
        nameError = R.string.error_name_empty.takeIf { state.name.isBlank() },
        // The schedule is not asked about here: that takes a read, and this runs on every
        // keystroke. It is asked once, on Save — see there.
        startDateError = null,
        endDateError = R.string.error_end_before_start.takeIf { state.endDate < state.startDate },
    )

    // endregion

    // region Trip wallets

    fun openWalletSheet(index: Int? = null) {
        // The dictionaries live in the combined state, not in the form on its own.
        val dictionaries = state.value
        val wallet = index?.let { dictionaries.wallets.getOrNull(it) }
        val fields = if (wallet == null) {
            WalletForm()
        } else {
            WalletForm.of(
                wallet.paymentMethodId,
                wallet.currencyCode,
                wallet.name,
                wallet.openingBalance,
                dictionaries.currency(wallet.currencyCode)?.decimals ?: DEFAULT_DECIMALS,
            )
        }
        form.update { it.copy(walletSheet = TripWalletSheet(index, fields.named(dictionaries))) }
    }

    fun closeWalletSheet() = form.update { it.copy(walletSheet = null) }

    fun walletSheetSelectPaymentMethod(paymentMethod: PaymentMethod) =
        updateWalletForm { it.selectPaymentMethod(paymentMethod.id) }

    fun walletSheetSelectCurrency(code: String?) = updateWalletForm { it.selectCurrency(code) }

    fun walletSheetUpdateName(value: String) = updateWalletForm { it.typeName(value) }

    fun walletSheetUpdateBalance(value: String) = updateWalletForm { it.typeOpeningBalance(value) }

    /** Changes the fields, keeping the name in step with the pair until someone types one. */
    private fun updateWalletForm(transform: (WalletForm) -> WalletForm) {
        val dictionaries = state.value
        form.update { state ->
            val sheet = state.walletSheet ?: return@update state
            state.copy(walletSheet = sheet.copy(form = transform(sheet.form).named(dictionaries)))
        }
    }

    private fun WalletForm.named(dictionaries: TripEditState): WalletForm = withGeneratedName(
        context,
        dictionaries.paymentMethod(paymentMethodId),
        dictionaries.currency(currencyCode),
    )

    fun confirmWallet() {
        val dictionaries = state.value
        val state = form.value
        val sheet = state.walletSheet ?: return

        val checked = sheet.form.check(
            context,
            dictionaries.paymentMethod(sheet.form.paymentMethodId),
            dictionaries.currency(sheet.form.currencyCode),
        ) { label ->
            state.wallets.withIndex().any { (index, wallet) ->
                index != sheet.editingIndex && walletLabel(wallet, dictionaries) == label
            }
        }
        val accepted = when (checked) {
            is WalletFormCheck.Refused -> {
                form.update { it.copy(walletSheet = sheet.copy(form = checked.form)) }
                return
            }
            is WalletFormCheck.Accepted -> checked
        }

        val existing = sheet.editingIndex?.let { state.wallets.getOrNull(it) }
        val draft = WalletDraft(
            id = existing?.id ?: 0,
            paymentMethodId = accepted.paymentMethodId,
            // Saved wallets keep the currency they were created with, whatever the sheet says.
            currencyCode = existing?.takeIf { it.id != 0L }?.currencyCode ?: accepted.currencyCode,
            name = accepted.name,
            openingBalance = accepted.openingBalance,
            lastUsedAt = existing?.lastUsedAt,
        )
        val wallets = if (sheet.editingIndex == null) {
            state.wallets + draft
        } else {
            state.wallets.toMutableList().apply { this[sheet.editingIndex] = draft }
        }
        form.update { it.copy(wallets = wallets, walletSheet = null) }
    }

    /**
     * Taking a wallet off the form is checked at once rather than at save time: by then
     * the sheet is gone and there would be nothing to point the refusal at.
     */
    fun removeWallet(index: Int) {
        val wallet = form.value.wallets.getOrNull(index) ?: return
        viewModelScope.launch {
            if (wallet.id != 0L && tripRepository.walletIsUsed(wallet.id)) {
                _events.send(EditorEvent.Error(context.getString(R.string.error_wallet_in_use)))
                return@launch
            }
            form.update { it.copy(wallets = it.wallets.filterIndexed { i, _ -> i != index }) }
        }
    }

    /** What a wallet is called on the form — typed, or built from its method and currency. */
    private fun walletLabel(draft: WalletDraft, dictionaries: TripEditState): String = walletName(
        context,
        draft.name,
        dictionaries.paymentMethod(draft.paymentMethodId),
        dictionaries.currency(draft.currencyCode),
    )

    // endregion

    // region Exchange rates

    /**
     * @param index null to add a rate. A new one converts to the base currency of the
     * moment; an existing one keeps whatever it was made against.
     */
    fun openRateSheet(index: Int? = null) {
        val current = state.value
        val rate = index?.let { current.rates.getOrNull(it) }
        val form = when {
            rate != null -> RateForm.of(rate)
            // Guarded by the form, which offers no way in without a base currency.
            else -> RateForm(toCurrencyCode = current.baseCurrency ?: return)
        }
        this.form.update { it.copy(rateSheet = TripRateSheet(index, form)) }
    }

    fun closeRateSheet() = form.update { it.copy(rateSheet = null) }

    fun rateSheetSelectCurrency(code: String?) {
        updateRateForm { it.copy(fromCurrencyCode = code, fromError = null) }
        // Choosing a currency is using it, and a rate is made for money a trip is about to
        // be spent in. Here rather than when the trip is saved: the picker should have it
        // at the top the next time it opens, which may well be before anything is saved.
        code?.let { viewModelScope.launch { currencyRepository.touch(it, LocalDateTime.now()) } }
    }

    fun rateSheetUpdateMultiplier(value: String) = updateRateForm {
        it.copy(multiplier = value, multiplierError = null)
    }

    fun rateSheetUpdateRate(value: String) = updateRateForm {
        it.copy(rate = value, rateError = null)
    }

    /**
     * A note typed by hand becomes the person's own, and the next lookup writes elsewhere.
     * Emptying the field hands it back: there is nothing of theirs left to keep.
     */
    fun rateSheetUpdateNote(value: String) = updateRateForm {
        it.copy(note = value, noteFromLookup = it.noteFromLookup && value.isBlank())
    }

    /** Anything typed by hand supersedes what was fetched, so the note about it goes. */
    private fun updateRateForm(transform: (RateForm) -> RateForm) {
        form.update { state ->
            val sheet = state.rateSheet ?: return@update state
            val changed = transform(sheet.form).copy(lookupError = null, fetchedDate = null)
            state.copy(rateSheet = sheet.copy(form = changed))
        }
    }

    /**
     * Asks the internet what the pair was worth.
     *
     * On the day the trip starts, where that day has been and gone; otherwise the latest
     * there is. A trip still ahead has no rate of its own — nobody has published one for a
     * day that has not happened — and the rate of today is the only honest guess at it.
     */
    fun fetchRate() {
        val current = state.value
        val sheet = current.rateSheet ?: return
        val from = sheet.form.fromCurrencyCode ?: return
        if (sheet.form.isFetching) return

        form.update {
            it.copy(
                rateSheet = sheet.copy(
                    form = sheet.form.copy(
                        isFetching = true,
                        lookupError = null,
                        fetchedDate = null,
                    )
                )
            )
        }
        val startDate = current.startDate
        viewModelScope.launch {
            val asked = startDate.takeIf { it.isBefore(LocalDate.now()) }
            val result = rateLookup.fetch(from, sheet.form.toCurrencyCode, asked)
            form.update { state ->
                val open = state.rateSheet ?: return@update state
                // The sheet may have been closed and another opened while the reply was on
                // its way; a rate for a pair nobody is looking at is dropped.
                if (open.form.fromCurrencyCode != from) return@update state
                state.copy(rateSheet = open.copy(form = open.form.filledWith(result)))
            }
        }
    }

    /**
     * The day the figure belongs to is the one thing a fetched rate has to say, and it is
     * said in the note — which is where somebody reading the trip's form will see it,
     * rather than on a sheet that closes. Unless the note is already somebody's own: that
     * is theirs, so the date goes under the button instead, as it always did.
     */
    private fun RateForm.filledWith(result: Result<FetchedRate>): RateForm {
        val fetched = result.getOrNull() ?: return copy(
            isFetching = false,
            lookupError = messageFor(result.exceptionOrNull()),
        )
        val stamped = note.isBlank() || noteFromLookup
        return copy(
            multiplier = fetched.multiplier.toString(),
            rate = MoneyFormat.formatRateForInput(fetched.rate),
            note = if (stamped) stampFor(fetched.date) else note,
            noteFromLookup = stamped,
            multiplierError = null,
            rateError = null,
            isFetching = false,
            fetchedDate = fetched.date.takeUnless { stamped },
        )
    }

    /** What a lookup writes into the note: the day the figure it brought back belongs to. */
    private fun stampFor(date: LocalDate): String {
        val locale = context.resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return context.getString(R.string.rate_note_fetched, formatter.format(date))
    }

    @StringRes
    private fun messageFor(failure: Throwable?): Int =
        when ((failure as? RateLookupFailure)?.error) {
            RateLookupError.UNSUPPORTED_PAIR -> R.string.error_rate_unsupported
            RateLookupError.UNREACHABLE -> R.string.error_rate_unreachable
            else -> R.string.error_rate_malformed
        }

    fun confirmRate() {
        val state = form.value
        val sheet = state.rateSheet ?: return

        val checked = sheet.form.check { from, to ->
            state.rates.withIndex().any { (index, rate) ->
                index != sheet.editingIndex &&
                    rate.fromCurrencyCode == from &&
                    rate.toCurrencyCode == to
            }
        }
        val accepted = when (checked) {
            is RateFormCheck.Refused -> {
                form.update { it.copy(rateSheet = sheet.copy(form = checked.form)) }
                return
            }
            is RateFormCheck.Accepted -> checked
        }

        val draft = RateDraft(
            id = sheet.editingIndex?.let { state.rates.getOrNull(it)?.id } ?: 0,
            fromCurrencyCode = accepted.fromCurrencyCode,
            toCurrencyCode = accepted.toCurrencyCode,
            rate = accepted.rate,
            multiplier = accepted.multiplier,
            note = accepted.note,
            noteFromLookup = accepted.noteFromLookup,
        )
        val rates = if (sheet.editingIndex == null) {
            state.rates + draft
        } else {
            state.rates.toMutableList().apply { this[sheet.editingIndex] = draft }
        }
        form.update { it.copy(rates = rates, rateSheet = null) }
    }

    /** Nothing points at a rate, so it goes on the spot with nothing to check. */
    fun removeRate(index: Int) = form.update {
        it.copy(rates = it.rates.filterIndexed { i, _ -> i != index })
    }

    // endregion

    fun save() {
        val state = validate(form.value).also { form.value = it }
        if (state.hasErrors) return

        val previous = original
        commit.launch(viewModelScope) {
            // Dates that no longer cover the schedule would leave events nothing can show
            // and the form cannot reach, so the trip refuses to be shortened over them —
            // the same refusal a dictionary row makes while something points at it.
            if (previous != null && !coversTheSchedule(previous.id, state)) return@launch
            write(state, previous)
        }
    }

    /** Says whether the dates still hold the schedule, and writes the reason where not. */
    private suspend fun coversTheSchedule(tripId: Long, state: TripEditState): Boolean {
        val (before, after) = scheduleRepository.countOutside(
            tripId = tripId,
            from = state.startDate,
            until = state.endDate,
            home = appPreferences.homeZone.first(),
        )
        if (before == 0 && after == 0) return true
        form.update {
            it.copy(
                startDateError = R.string.error_dates_cover_events.takeIf { before > 0 },
                endDateError = R.string.error_dates_cover_events.takeIf { after > 0 },
            )
        }
        return false
    }

    private suspend fun write(state: TripEditState, previous: Trip?) {
        val trip = Trip(
            id = previous?.id ?: Route.NEW,
            name = state.name.trim(),
            startDate = state.startDate,
            endDate = state.endDate,
            photoFile = state.photoFile,
            createdAt = previous?.createdAt ?: LocalDateTime.now(),
        )

        try {
            // One write, not one per wallet: a trip saved with half of its wallets —
            // the other half refused, say, by a name already taken — would look saved
            // and be wrong.
            val id = tripRepository.inTransaction {
                val id = if (previous == null) tripRepository.insert(trip) else {
                    tripRepository.update(trip)
                    trip.id
                }
                saveWallets(id, state)
                saveRates(id, state)
                id
            }
            // The cover the trip used to carry is only safe to remove once the row no
            // longer points at it; until then a failed save would leave a broken link.
            if (previous?.photoFile != null && previous.photoFile != trip.photoFile) {
                coverStorage.delete(previous.photoFile)
            }
            // A trip that has only just been made is handed over rather than closed:
            // it was made in order to be used.
            _events.send(
                if (previous == null) EditorEvent.Created(id) else EditorEvent.Closed
            )
        } catch (e: Exception) {
            _events.send(EditorEvent.Error(context, R.string.error_save_trip, e))
        }
    }

    /**
     * Wallets are written one by one — added, changed, or taken away — rather than
     * recreated: entries and recounts point at them by id.
     */
    private suspend fun saveWallets(tripId: Long, state: TripEditState) {
        val kept = state.wallets.map { draft ->
            val wallet = TripWallet(
                id = draft.id,
                tripId = tripId,
                paymentMethodId = draft.paymentMethodId,
                currencyCode = draft.currencyCode,
                name = draft.name,
                openingBalanceMilli = draft.openingBalance.milli,
                lastUsedAt = draft.lastUsedAt,
            )
            if (draft.id == 0L) tripRepository.addWallet(wallet) else {
                tripRepository.updateWallet(wallet)
                draft.id
            }
        }
        (loadedWalletIds - kept.toSet()).forEach { goneId ->
            tripRepository.getWallet(goneId)?.let { tripRepository.deleteWallet(it.wallet) }
        }
        loadedWalletIds = kept
    }

    /**
     * Rates go the same way as wallets — added, changed, or taken away — even though
     * nothing points at them: a rate rewritten on every save would take a new id each
     * time for no gain.
     */
    private suspend fun saveRates(tripId: Long, state: TripEditState) {
        val kept = state.rates.map { draft ->
            val rate = TripRate(
                id = draft.id,
                tripId = tripId,
                fromCurrencyCode = draft.fromCurrencyCode,
                toCurrencyCode = draft.toCurrencyCode,
                rateMilli = draft.rate.milli,
                multiplier = draft.multiplier,
                note = draft.note,
                noteFromLookup = draft.noteFromLookup,
            )
            if (draft.id == 0L) tripRepository.addRate(rate) else {
                tripRepository.updateRate(rate)
                draft.id
            }
        }
        (loadedRateIds - kept.toSet()).forEach { tripRepository.deleteRate(it) }
        loadedRateIds = kept
    }

    // region Cover photo

    /**
     * Brings the picked photo into the app's own folder at once, without waiting for the
     * form to be saved. The link the picker hands out is good only while the screen that
     * asked for it lives, and the form has to show what it is about to save.
     */
    fun pickCover(source: Uri) {
        viewModelScope.launch {
            val name = coverStorage.importFrom(source)
            if (name == null) {
                _events.send(EditorEvent.Error(context.getString(R.string.error_cover_import)))
                return@launch
            }
            setCover(name)
        }
    }

    fun removeCover() = setCover(null)

    /**
     * The file the form turns away from stays where it is: the trip still points at it
     * until the form is saved, and a cover nothing points at is swept up on the next start.
     */
    private fun setCover(name: String?) = form.update { it.copy(photoFile = name) }

    // endregion

    // region Receipt photos of the trip

    fun showPhotoDeleteDialog() = form.update { it.copy(isPhotoDeleteDialogVisible = true) }

    fun hidePhotoDeleteDialog() = form.update { it.copy(isPhotoDeleteDialogVisible = false) }

    /**
     * Drops every receipt photo of the trip while keeping the entries. Photos are the bulk
     * of what the app stores, and once a trip is settled they are usually the first thing
     * a person is willing to give up.
     */
    fun deleteAllPhotos() {
        hidePhotoDeleteDialog()
        viewModelScope.launch {
            try {
                val names = entryRepository.photoFiles(tripId)
                entryRepository.clearPhotos(tripId)
                photoStorage.deleteAll(names)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, R.string.error_delete_photos, e))
            }
        }
    }

    // endregion

    fun showDeleteDialog() = form.update { it.copy(isDeleteDialogVisible = true) }

    fun hideDeleteDialog() = form.update { it.copy(isDeleteDialogVisible = false) }

    fun delete() {
        val trip = original ?: return
        hideDeleteDialog()
        commit.launch(viewModelScope) {
            try {
                // Foreign keys take the trip's wallets and entries along with it, which
                // is exactly what the prompt warned about. The photos go once the rows are
                // gone — until then a failed delete would leave entries pointing at nothing.
                val photos = entryRepository.photoFiles(tripId)
                tripRepository.delete(trip)
                photoStorage.deleteAll(photos)
                coverStorage.delete(trip.photoFile)
                // The form was opened from the trip's own screen, which has just lost its
                // trip: closing would land on it, so the caller is told to go further.
                _events.send(EditorEvent.Deleted)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, R.string.error_delete_trip, e))
            }
        }
    }

    private companion object {
        const val DEFAULT_DECIMALS = 2
    }
}
