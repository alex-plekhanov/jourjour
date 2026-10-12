package app.jourjour.ui.entry

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.jourjour.core.location.Coordinates
import app.jourjour.core.location.LocationResult
import app.jourjour.core.location.LocationSource
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.text.resolveName
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.PhotoSettings
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.common.Commit
import app.jourjour.ui.common.EditorEvent
import app.jourjour.ui.common.WalletForm
import app.jourjour.ui.common.WalletFormCheck
import app.jourjour.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.jourjour.R
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.CategoryFavorite
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.repository.CategoryRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** The expense or income half of an entry: payment method, amount and currency. */
data class AmountBlock(
    val walletId: Long? = null,
    val amount: String = "",
    val currencyCode: String? = null,
    /**
     * What was charged in the account's own currency. Only filled in when payment was
     * made in some other currency, and usually later, on the reconciliation screen.
     */
    val settlement: String = "",
    /**
     * Whether that field is on show. Folded away like the notes are: most halves are paid
     * in the wallet's own money and never have one, and of those that do, most are settled
     * later on the reconciliation screen rather than here.
     */
    val isSettlementVisible: Boolean = false,
    @param:StringRes val walletError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    /**
     * An amount that had to be there and is not. Marked on the field without a word
     * under it — the form says so at its foot, once for both halves — because a message
     * under the amount pushes the controls hanging on its lower border out of place.
     */
    val amountMissing: Boolean = false,
) {
    val hasErrors: Boolean get() = walletError != null || amountError != null || amountMissing
}

/**
 * Making a wallet without leaving the entry form. The first entry of a trip is often
 * written before anybody has thought about wallets, and sending a person to the trip form
 * and back would lose what they had already typed.
 */
data class EntryWalletSheet(
    /** Which half asked for it — that is the half the new wallet lands in. */
    val forExpense: Boolean,
    /** Loaded for the sheet alone: nothing else on the entry form offers a payment method. */
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val form: WalletForm = WalletForm(),
) {
    fun paymentMethod(id: Long?): PaymentMethod? = paymentMethods.firstOrNull { it.id == id }
}

data class EntryEditState(
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val title: String = "",
    val place: String = "",
    val notes: String = "",
    val quantity: String = "",
    val coordinates: Coordinates? = null,
    val photoFile: String? = null,
    /**
     * A shot waiting to be looked at, where the photos are set to be reviewed. It is not
     * the entry's photo yet: the entry takes it only once it has been accepted.
     */
    val shotUnderReview: String? = null,
    val isLocating: Boolean = false,
    val expense: AmountBlock = AmountBlock(),
    val income: AmountBlock = AmountBlock(),

    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    /** The trip's wallets, the ones used most recently first. */
    val wallets: List<TripWalletDetails> = emptyList(),
    val trip: Trip? = null,
    val favorites: List<String> = emptyList(),

    /** Whether the notes field is on show. Folded away until asked for, or until filled in. */
    val isNotesVisible: Boolean = false,

    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val formError: Int? = null,
    val isEditMode: Boolean = false,
    val isDeleteDialogVisible: Boolean = false,
    val isCategorySheetVisible: Boolean = false,
    val walletSheet: EntryWalletSheet? = null,
) {
    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    val showExpense: Boolean get() = category?.isExpense == true
    val showIncome: Boolean get() = category?.isIncome == true
    val showQuantity: Boolean get() = category?.trackQuantity == true

    val isTitleFavorite: Boolean get() = title.trim().isNotEmpty() && title.trim() in favorites

    val hasErrors: Boolean
        get() = categoryError != null || formError != null || expense.hasErrors || income.hasErrors

    fun currency(code: String?): Currency? = currencies.firstOrNull { it.code == code }

    fun wallet(id: Long?): TripWalletDetails? = wallets.firstOrNull { it.wallet.id == id }

    /**
     * What this half of the entry may be denominated in.
     *
     * The wallet decides it: money in a cash wallet is the currency of the notes in it,
     * and nothing else. A payment method that lets the bank convert is the exception, and
     * then the whole dictionary is open. Before a wallet is chosen there is nothing to go
     * on, so there is nothing to choose from either.
     */
    fun currencyOptions(walletId: Long?): List<Currency> {
        val details = wallet(walletId) ?: return emptyList()
        if (details.paymentMethod.fixedCurrency) return listOf(details.currency)
        return currencies
    }

    /** The currency field only opens where there is more than one currency to choose. */
    fun currencyIsLocked(walletId: Long?): Boolean = currencyOptions(walletId).size <= 1

    /** The currency anything was last written in, anywhere. */
    val lastUsedCurrency: Currency?
        get() = currencies
            .mapNotNull { currency -> currency.usedAt?.let { currency to it } }
            .maxByOrNull { (_, usedAt) -> usedAt }
            ?.first

    /**
     * The wallet's own currency, when it differs from the one that was paid in. That is
     * the currency the wallet is actually charged in, and the amount has to be learnt from
     * the bank; paying in the wallet's own currency charges the very same amount.
     */
    fun settlementCurrency(block: AmountBlock): Currency? {
        val walletCurrency = wallet(block.walletId)?.currency ?: return null
        if (walletCurrency.code == block.currencyCode) return null
        return walletCurrency
    }

    /** Price per unit, for reference only: never stored, shown beside the quantity. */
    val unitPrice: Pair<Money, Currency>?
        get() {
            if (!showQuantity) return null
            val parsedQuantity = MoneyFormat.parseQuantity(quantity) ?: return null
            val block = if (expense.amount.isNotBlank()) expense else income
            val currency = currency(block.currencyCode) ?: return null
            val amount = MoneyFormat.parseAmount(block.amount, currency.decimals) ?: return null
            return unitPrice(amount, parsedQuantity)?.let { it to currency }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryEditViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
    private val locationSource: LocationSource,
    private val appPreferences: AppPreferences,
    val photoStorage: PhotoStorage,
    private val tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.EntryEdit>()

    /** The editable fields. */
    private val form = MutableStateFlow(
        EntryEditState(
            isEditMode = route.entryId != Route.NEW,
            // A new entry opens straight into the category picker. Nothing else on the
            // form can be filled in before a category is chosen — it decides which halves
            // and fields even appear — so stopping at an empty form first is a wasted tap.
            isCategorySheetVisible = route.entryId == Route.NEW,
        )
    )

    /**
     * The dictionaries live in a state of their own and are collected for as long as the
     * model does, not only while a screen is subscribed: filling in a currency and
     * validating the form depend on them, and both happen on a tap rather than on a draw.
     */
    private val references = MutableStateFlow(References())

    /**
     * The category's favourite titles, as rows rather than as text: a preloaded favourite
     * is shown in the device's language but is stored, added and deleted under the name
     * the row was written with.
     */
    private val favorites = MutableStateFlow<List<CategoryFavorite>>(emptyList())

    val state: StateFlow<EntryEditState> =
        combine(form, references, favorites) { form, references, favorites ->
            form.withReferences(references).copy(favorites = favorites.labels())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = form.value,
        )

    private val commit = Commit()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var original: Entry? = null

    /** Guards the prefill: the wallets arrive more than once, the prefill happens once. */
    private var lastWalletApplied = false

    init {
        viewModelScope.launch {
            combine(
                categoryRepository.observeAll(),
                currencyRepository.observeAll(),
                tripRepository.observeWallets(route.tripId),
                tripRepository.observeById(route.tripId),
            ) { categories, currencies, wallets, trip ->
                References(categories, currencies, wallets, trip)
            }.collect {
                references.value = it
                applyLastWallet()
            }
        }
        viewModelScope.launch {
            form.map { it.categoryId }
                .distinctUntilChanged()
                .flatMapLatest { categoryId ->
                    if (categoryId == null) {
                        flowOf(emptyList())
                    } else {
                        categoryRepository.observeFavorites(categoryId)
                    }
                }
                .collect { favorites.value = it }
        }
        if (route.entryId != Route.NEW) load(route.entryId)
    }

    /** The form plus the dictionaries — exactly what the screen sees. */
    private fun snapshot(): EntryEditState =
        form.value.withReferences(references.value).copy(favorites = favorites.value.labels())

    /** Favourite titles the way they are shown, which is also what a chip puts in the title. */
    private fun List<CategoryFavorite>.labels(): List<String> =
        map { resolveName(context, it.name, it.nameKey) }

    private fun EntryEditState.withReferences(references: References) = copy(
        categories = references.categories,
        currencies = references.currencies,
        wallets = references.wallets,
        trip = references.trip,
    )

    /**
     * Starts a new entry on the wallet the trip was last paid from — the head of the list,
     * which is kept in order of use.
     *
     * Runs once, and only for a new entry: an entry being edited already names its wallet,
     * and a form the person has begun filling in must not be overwritten when the
     * dictionaries arrive a moment later. A trip with no wallets yet starts on nothing,
     * and the picker's last line is there to make the first one.
     */
    private fun applyLastWallet() {
        if (route.entryId != Route.NEW || lastWalletApplied) return
        val last = snapshot().wallets.firstOrNull() ?: return

        lastWalletApplied = true
        selectWallet(expense = true, wallet = last)
        selectWallet(expense = false, wallet = last)
    }

    private fun load(entryId: Long) {
        viewModelScope.launch {
            val details = entryRepository.getDetailsById(entryId)
            if (details == null) {
                _events.send(EditorEvent.Closed)
                return@launch
            }
            val entry = details.entry
            original = entry
            form.value = EntryEditState(
                categoryId = entry.categoryId,
                date = entry.dateTime.toLocalDate(),
                time = entry.dateTime.toLocalTime(),
                title = entry.title,
                place = entry.place,
                notes = entry.notes,
                quantity = entry.quantityMilli
                    ?.let { MoneyFormat.formatQuantityForInput(
                        Quantity(it)
                    ) }
                    .orEmpty(),
                photoFile = entry.photoFile,
                // An entry that already carries notes opens with them in sight; hiding
                // what is filled in would look like the text had been lost.
                isNotesVisible = entry.notes.isNotBlank(),
                coordinates = entry.latitude?.let { latitude ->
                    entry.longitude?.let { longitude ->
                        Coordinates(
                            latitude,
                            longitude
                        )
                    }
                },
                expense = AmountBlock(
                    walletId = entry.expenseWalletId,
                    amount = amountToInput(entry.expenseAmountMilli, details.expenseCurrency),
                    currencyCode = entry.expenseCurrencyCode,
                    // The settled amount is in the wallet's currency, not the one paid in.
                    settlement = amountToInput(
                        entry.expenseSettlementAmountMilli,
                        details.expenseWallet?.currency,
                    ),
                    isSettlementVisible = entry.expenseSettlementAmountMilli != null,
                ),
                income = AmountBlock(
                    walletId = entry.incomeWalletId,
                    amount = amountToInput(entry.incomeAmountMilli, details.incomeCurrency),
                    currencyCode = entry.incomeCurrencyCode,
                    settlement = amountToInput(
                        entry.incomeSettlementAmountMilli,
                        details.incomeWallet?.currency,
                    ),
                    isSettlementVisible = entry.incomeSettlementAmountMilli != null,
                ),
                isEditMode = true,
            )
        }
    }

    private fun amountToInput(milli: Long?, currency: Currency?): String =
        if (milli == null) {
            ""
        } else {
            MoneyFormat.formatAmountForInput(
                Money(milli), currency?.decimals ?: DEFAULT_DECIMALS)
        }

    // region Entry fields

    fun showCategorySheet() = form.update { it.copy(isCategorySheetVisible = true) }

    fun hideCategorySheet() = form.update { it.copy(isCategorySheetVisible = false) }

    fun selectCategory(category: Category) {
        form.update { state ->
            state.copy(
                categoryId = category.id,
                isCategorySheetVisible = false,
                categoryError = null,
                // A half the new category no longer allows is cleared, or the entry would
                // keep an amount that the form no longer shows.
                expense = if (category.isExpense) state.expense else AmountBlock(),
                income = if (category.isIncome) state.income else AmountBlock(),
                quantity = if (category.trackQuantity) state.quantity else "",
            )
        }
        applyWalletCurrencies()
    }

    fun updateDate(value: LocalDate) = form.update { it.copy(date = value) }

    fun updateTime(value: LocalTime) = form.update { it.copy(time = value) }

    /**
     * Walks the date and time back to where the trip's previous entry stands, and forwards
     * to the next one.
     *
     * Entries are written in bursts — a morning of them typed up over coffee — and each
     * wants the moment of the one before or after it rather than now. Typing that in is a
     * date picker, a time picker and two guesses at what the last one said; these two
     * buttons are the same answer in one tap.
     *
     * It steps between *moments*, not records: everything the trip has at one moment is
     * stepped over together, a receipt and the tip paid with it having been one stop. The
     * step stays where it is at either end — the earliest moment has nothing before it, and
     * an entry asking for one is already where it would land. A trip with nothing in it at
     * all has no moments to walk, so the two buttons offer its own ends instead: the first
     * minute of the day it starts, the last of the day it ends.
     */
    fun stepToEarlierMoment() = stepTo { tripId, moment ->
        entryRepository.momentBefore(tripId, moment)
            ?: emptyTripFallback { trip -> LocalDateTime.of(trip.startDate, LocalTime.MIN) }
    }

    fun stepToLaterMoment() = stepTo { tripId, moment ->
        entryRepository.momentAfter(tripId, moment)
            ?: emptyTripFallback { trip -> LocalDateTime.of(trip.endDate, LAST_MINUTE_OF_DAY) }
    }

    private fun stepTo(moment: suspend (Long, LocalDateTime) -> LocalDateTime?) {
        viewModelScope.launch {
            val from = LocalDateTime.of(form.value.date, form.value.time)
            val to = moment(route.tripId, from) ?: return@launch
            form.update { it.copy(date = to.toLocalDate(), time = to.toLocalTime()) }
        }
    }

    /**
     * Where a trip holds no entries at all, one of its own ends; where it holds some, null,
     * which leaves the form as it is. The count is what separates the two: a trip whose
     * every entry stands at the moment on the form has nothing before or after it either,
     * and that is a step to be refused rather than a trip to be ranged over.
     */
    private suspend fun emptyTripFallback(end: (Trip) -> LocalDateTime): LocalDateTime? {
        if (entryRepository.countForTrip(route.tripId) > 0) return null
        return state.value.trip?.let(end)
    }

    fun updateTitle(value: String) = form.update { it.copy(title = value) }

    fun updatePlace(value: String) = form.update { it.copy(place = value) }

    fun updateNotes(value: String) = form.update { it.copy(notes = value) }

    fun updateQuantity(value: String) = form.update { it.copy(quantity = value) }

    fun pickFavorite(name: String) = form.update { it.copy(title = name) }

    /** Shows and hides the notes field, which most entries never need. */
    fun toggleNotes() = form.update { it.copy(isNotesVisible = !it.isNotesVisible) }

    /** The same for a half's settled amount, which most halves never have. */
    fun toggleSettlement(expense: Boolean) =
        updateBlock(expense) { it.copy(isSettlementVisible = !it.isSettlementVisible) }

    // region Coordinates

    fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    /** The screen asks for permission; this runs once it is granted or already held. */
    fun captureLocation() {
        if (form.value.isLocating) return
        form.update { it.copy(isLocating = true) }

        viewModelScope.launch {
            when (val result = locationSource.current()) {
                is LocationResult.Success ->
                    form.update { it.copy(coordinates = result.coordinates, isLocating = false) }

                LocationResult.PermissionDenied -> finishLocating(R.string.error_location_denied)
                LocationResult.Unavailable -> finishLocating(R.string.error_location_unavailable)
                LocationResult.Timeout -> finishLocating(R.string.error_location_timeout)
            }
        }
    }

    fun onLocationPermissionDenied() {
        viewModelScope.launch {
            _events.send(EditorEvent.Error(context.getString(R.string.error_location_denied)))
        }
    }

    fun clearLocation() = form.update { it.copy(coordinates = null) }

    // endregion

    // region Receipt photo

    /**
     * A shot has arrived from the camera, and what happens to it next is a setting.
     *
     * Asked to be looked at, it goes onto the review screen, which renders it and waits for
     * an answer. Otherwise it is rendered here and the entry takes it, which is the whole of
     * what used to happen: the camera already shows a confirmation screen of its own, and a
     * second one is worth stepping through only for somebody whose receipts come out the
     * wrong way up.
     */
    fun onShotTaken(name: String) {
        viewModelScope.launch {
            val settings = appPreferences.photoSettings.first()
            if (settings.review) {
                form.update { it.copy(shotUnderReview = name) }
            } else {
                render(name, quarterTurns = 0, settings = settings)
                onShotKept(name)
            }
        }
    }

    /**
     * Renders the shot as the photo the entry would keep, turned by [quarterTurns], for the
     * review screen to show.
     *
     * The camera's own pixels are set aside on the first call and every rendering is made
     * from them, so a receipt turned round twice is still one encode deep rather than three.
     * Nothing here touches the form: until the photo is accepted the entry carries no photo
     * at all, and a form backed out of leaves both files to the sweep.
     *
     * It is a suspending call rather than something launched into the view model's own
     * scope because the screen waits on it: there is nothing to show until it has been
     * written and read back.
     */
    suspend fun renderShot(name: String, quarterTurns: Int) =
        render(name, quarterTurns, appPreferences.photoSettings.first())

    private suspend fun render(name: String, quarterTurns: Int, settings: PhotoSettings) {
        photoStorage.setAside(name)
        if (settings.compress) {
            photoStorage.render(name, settings.maxSide, settings.quality, quarterTurns)
        } else {
            photoStorage.renderUnshrunk(name, quarterTurns)
        }
        // A shot too broken to decode is kept as the camera wrote it rather than leaving
        // the screen with nothing to show and the entry pointing at a file that is not
        // there. If even that fails there is no photo to accept, and the screen says so.
        if (!photoStorage.exists(name)) photoStorage.renderUnshrunk(name, quarterTurns)
    }

    /** The rendering on the screen is the one to keep; the camera's own pixels can go. */
    fun onShotKept(name: String) {
        viewModelScope.launch {
            photoStorage.dropAside(name)
            form.update { it.copy(photoFile = name, shotUnderReview = null) }
        }
    }

    /** A shot retaken or never taken: both files go now rather than waiting for the sweep. */
    fun onShotDiscarded(name: String) {
        viewModelScope.launch {
            form.update { it.copy(shotUnderReview = null) }
            photoStorage.discard(name)
        }
    }

    /**
     * Only the record lets go of the photo here. The file itself is removed once the entry
     * is saved, so backing out of the form leaves the photo where it was.
     */
    fun removePhoto() = form.update { it.copy(photoFile = null) }

    private suspend fun finishLocating(@StringRes messageRes: Int) {
        form.update { it.copy(isLocating = false) }
        _events.send(EditorEvent.Error(context.getString(messageRes)))
    }

    // endregion

    /** The star beside the title: adds it to the category's favourites and takes it out. */
    fun toggleTitleFavorite() {
        val categoryId = snapshot().categoryId ?: return
        val title = form.value.title.trim()
        if (title.isEmpty()) return

        // The title holds what the chip showed, which for a preloaded favourite is the
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

    // endregion

    // region Amounts

    fun selectWallet(expense: Boolean, wallet: TripWalletDetails) {
        // The wallet's own currency goes in straight away, taken from the wallet in hand
        // rather than looked up: one just made on this form has not reached the observed
        // list yet, and the half would sit with no currency until it did.
        updateBlock(expense) {
            it.copy(
                walletId = wallet.wallet.id,
                walletError = null,
                currencyCode = wallet.currency.code,
            )
        }
        applyWalletCurrencies()

        // A wallet that can only hold the one currency is already right.
        if (wallet.paymentMethod.fixedCurrency) return
        viewModelScope.launch { applyLastCurrency(expense, wallet) }
    }

    /**
     * Puts a card on the currency it was last paid in — the currency of the place, not the
     * one its account is held in. A wallet nothing has gone through yet falls back on the
     * last currency used anywhere, which on a trip already under way is the right guess,
     * and then on its own.
     *
     * It arrives a moment after the wallet does, so it steps aside if anything has moved
     * on in the meantime.
     */
    private suspend fun applyLastCurrency(expense: Boolean, wallet: TripWalletDetails) {
        val code = entryRepository.lastCurrencyForWallet(wallet.wallet.id)
            ?: snapshot().lastUsedCurrency?.code
            ?: return
        updateBlock(expense) { block ->
            val untouched = block.walletId == wallet.wallet.id &&
                block.currencyCode == wallet.currency.code
            if (untouched) block.copy(currencyCode = code) else block
        }
        applyWalletCurrencies()
    }

    // region Making a wallet from the entry form

    fun openWalletSheet(expense: Boolean) {
        form.update { it.copy(walletSheet = EntryWalletSheet(forExpense = expense)) }
        viewModelScope.launch {
            val methods = paymentMethodRepository.getAll()
            updateWalletSheet { it.copy(paymentMethods = methods) }
        }
    }

    fun closeWalletSheet() = form.update { it.copy(walletSheet = null) }

    fun walletSheetSelectPaymentMethod(paymentMethod: PaymentMethod) =
        updateWalletForm { it.selectPaymentMethod(paymentMethod.id) }

    fun walletSheetSelectCurrency(code: String?) = updateWalletForm { it.selectCurrency(code) }

    fun walletSheetUpdateName(value: String) = updateWalletForm { it.typeName(value) }

    fun walletSheetUpdateBalance(value: String) = updateWalletForm { it.typeOpeningBalance(value) }

    /** Changes the fields, keeping the name in step with the pair until someone types one. */
    private fun updateWalletForm(transform: (WalletForm) -> WalletForm) {
        // The currencies live in the combined state, not in the form on its own.
        val dictionaries = snapshot()
        form.update { state ->
            val sheet = state.walletSheet ?: return@update state
            val changed = transform(sheet.form)
            val named = changed.withGeneratedName(
                context,
                sheet.paymentMethod(changed.paymentMethodId),
                dictionaries.currency(changed.currencyCode),
            )
            state.copy(walletSheet = sheet.copy(form = named))
        }
    }

    private fun updateWalletSheet(transform: (EntryWalletSheet) -> EntryWalletSheet) =
        form.update { it.copy(walletSheet = it.walletSheet?.let(transform)) }

    fun confirmWallet() {
        val state = snapshot()
        val sheet = state.walletSheet ?: return
        val checked = sheet.form.check(
            context,
            sheet.paymentMethod(sheet.form.paymentMethodId),
            state.currency(sheet.form.currencyCode),
        ) { label -> state.wallets.any { walletName(context, it) == label } }
        val accepted = when (checked) {
            is WalletFormCheck.Refused -> {
                updateWalletSheet { it.copy(form = checked.form) }
                return
            }
            is WalletFormCheck.Accepted -> checked
        }

        commit.launch(viewModelScope) {
            try {
                val id = tripRepository.addWallet(
                    TripWallet(
                        tripId = route.tripId,
                        paymentMethodId = accepted.paymentMethodId,
                        currencyCode = accepted.currencyCode,
                        name = accepted.name,
                        openingBalanceMilli = accepted.openingBalance.milli,
                    )
                )
                closeWalletSheet()
                tripRepository.getWallet(id)?.let { selectWallet(sheet.forExpense, it) }
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, R.string.error_save_trip, e))
            }
        }
    }

    // endregion

    fun updateExpenseCurrency(code: String?) {
        updateBlock(expense = true) { it.copy(currencyCode = code) }
        // Not a reset of the choice — a currency the wallet allows survives it — but the
        // amount has to be rewritten to the decimals the new currency uses.
        applyWalletCurrencies()
    }

    fun updateIncomeCurrency(code: String?) {
        updateBlock(expense = false) { it.copy(currencyCode = code) }
        applyWalletCurrencies()
    }

    fun updateExpenseAmount(value: String) =
        updateBlock(expense = true) { it.copy(amount = value, amountError = null, amountMissing = false) }

    fun updateIncomeAmount(value: String) =
        updateBlock(expense = false) { it.copy(amount = value, amountError = null, amountMissing = false) }

    fun updateExpenseSettlement(value: String) =
        updateBlock(expense = true) { it.copy(settlement = value) }

    fun updateIncomeSettlement(value: String) =
        updateBlock(expense = false) { it.copy(settlement = value) }

    private fun updateBlock(expense: Boolean, transform: (AmountBlock) -> AmountBlock) {
        form.update { state ->
            if (expense) {
                state.copy(expense = transform(state.expense), formError = null)
            } else {
                state.copy(income = transform(state.income), formError = null)
            }
        }
    }

    /** Brings both halves back in line with what their wallets allow. */
    private fun applyWalletCurrencies() {
        val state = snapshot()
        form.update {
            it.copy(
                expense = it.expense.withWalletCurrency(state),
                income = it.income.withWalletCurrency(state),
            )
        }
    }

    /**
     * Settles the currency of a half, and the shape of its amount with it.
     *
     * A currency the wallet still allows is left alone; anything else falls back to the
     * wallet's own, and to nothing at all while there is no wallet. Half-typed text in the
     * amount survives, but a number is rewritten to the new currency's decimals, so 5.00
     * in dollars becomes 5 in yen.
     */
    private fun AmountBlock.withWalletCurrency(state: EntryEditState): AmountBlock {
        // A wallet the observed list has not caught up with yet says nothing about what
        // is allowed, and guessing from it would clear a currency that is perfectly good.
        if (walletId != null && state.wallet(walletId) == null) return this

        val options = state.currencyOptions(walletId)
        val code = currencyCode?.takeIf { chosen -> options.any { it.code == chosen } }
            ?: state.wallet(walletId)?.currency?.code
        val decimals = state.currency(code)?.decimals ?: return copy(currencyCode = code)
        val entered = MoneyFormat.parseAmount(amount, decimals)
        return copy(
            currencyCode = code,
            amount = entered?.let { MoneyFormat.formatAmountForInput(it, decimals) } ?: amount,
        )
    }

    // endregion

    fun save() {
        val validated = validate(snapshot())
        form.update {
            it.copy(
                categoryError = validated.categoryError,
                formError = validated.formError,
                expense = it.expense.copy(
                    walletError = validated.expense.walletError,
                    amountError = validated.expense.amountError,
                    amountMissing = validated.expense.amountMissing,
                ),
                income = it.income.copy(
                    walletError = validated.income.walletError,
                    amountError = validated.income.amountError,
                    amountMissing = validated.income.amountMissing,
                ),
            )
        }
        if (validated.hasErrors) return

        val expenseAmount = validated.expense.parsedAmount(validated)
            .takeIf { validated.showExpense }
        val incomeAmount = validated.income.parsedAmount(validated)
            .takeIf { validated.showIncome }

        // The settled amount needs no currency of its own: it is always the wallet's,
        // and a wallet keeps the currency it was created with.
        val expenseSettlement = validated.settlementOf(validated.expense)
            .takeIf { expenseAmount != null }
        val incomeSettlement = validated.settlementOf(validated.income)
            .takeIf { incomeAmount != null }

        val previous = original
        val entry = Entry(
            id = previous?.id ?: Route.NEW,
            tripId = route.tripId,
            categoryId = requireNotNull(validated.categoryId),
            // The time starts at the moment the form was opened and can be corrected. It
            // orders the entries of a day, and it decides which side of a wallet recount
            // an entry falls on, so an expense remembered in the evening can be put back
            // where it happened.
            dateTime = LocalDateTime.of(validated.date, validated.time),
            title = validated.title.trim(),
            place = validated.place.trim(),
            notes = validated.notes.trim(),
            quantityMilli = MoneyFormat.parseQuantity(
                validated.quantity
            )?.milli
                .takeIf { validated.showQuantity },
            expenseWalletId = validated.expense.walletId.takeIf { expenseAmount != null },
            expenseAmountMilli = expenseAmount?.milli,
            expenseCurrencyCode = validated.expense.currencyCode.takeIf { expenseAmount != null },
            incomeWalletId = validated.income.walletId.takeIf { incomeAmount != null },
            incomeAmountMilli = incomeAmount?.milli,
            incomeCurrencyCode = validated.income.currencyCode.takeIf { incomeAmount != null },
            expenseSettlementAmountMilli = expenseSettlement?.first?.milli,
            incomeSettlementAmountMilli = incomeSettlement?.first?.milli,
            latitude = validated.coordinates?.latitude,
            longitude = validated.coordinates?.longitude,
            photoFile = validated.photoFile,
        )

        commit.launch(viewModelScope) {
            try {
                if (previous == null) entryRepository.insert(entry) else entryRepository.update(entry)
                // The photo the entry no longer points at goes only now, when the change
                // is committed — until then backing out of the form had to keep it.
                if (previous?.photoFile != null && previous.photoFile != entry.photoFile) {
                    photoStorage.delete(previous.photoFile)
                }
                touchWallets(entry)
                touchCurrencies(entry)
                _events.send(EditorEvent.Closed)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, R.string.error_save_entry, e))
            }
        }
    }

    /**
     * Marks the currencies the entry was written in as just used, which is what puts them
     * at the top of the picker from now on. An exchange marks both of its currencies:
     * both really were used, and which is which is not in question.
     */
    private suspend fun touchCurrencies(entry: Entry) {
        val now = LocalDateTime.now()
        val codes = listOfNotNull(
            entry.expenseCurrencyCode.takeIf { entry.expenseAmountMilli != null },
            entry.incomeCurrencyCode.takeIf { entry.incomeAmountMilli != null },
        )
        codes.distinct().forEach { currencyRepository.touch(it, now) }
    }

    /**
     * Notes which wallets the entry moved through, so the next entry of the trip opens on
     * the one used last and the picker offers them in the order they are being used.
     */
    private suspend fun touchWallets(entry: Entry) {
        val now = LocalDateTime.now()
        listOfNotNull(entry.expenseWalletId, entry.incomeWalletId)
            .distinct()
            .forEach { tripRepository.touchWallet(it, now) }
    }

    /** The parsed settled amount with its wallet's currency, if one was entered at all. */
    private fun EntryEditState.settlementOf(block: AmountBlock): Pair<Money, Currency>? {
        val currency = settlementCurrency(block) ?: return null
        val amount = MoneyFormat.parseAmount(block.settlement, currency.decimals) ?: return null
        return amount to currency
    }

    private fun AmountBlock.parsedAmount(state: EntryEditState): Money? {
        val decimals = state.currency(currencyCode)?.decimals ?: DEFAULT_DECIMALS
        return MoneyFormat.parseAmount(amount, decimals)?.takeIf { !it.isZero }
    }

    private fun validate(state: EntryEditState): EntryEditState {
        // A category that is both an expense and an income — an exchange, money moved
        // between wallets — needs both halves: money that leaves one wallet has to arrive
        // somewhere, and a half left empty there is an entry half written.
        val bothRequired = state.showExpense && state.showIncome
        // On a one-sided category the one half there is has to be filled in; with the
        // category not chosen yet there is no half to speak of.
        val required = state.categoryId != null
        val expense = state.expense.validated(state, visible = state.showExpense, required = required)
        val income = state.income.validated(state, visible = state.showIncome, required = required)
        val missing = expense.amountMissing || income.amountMissing

        return state.copy(
            expense = expense,
            income = income,
            categoryError = R.string.error_category_required.takeIf { state.categoryId == null },
            // Said once, at the foot of the form: the halves only turn red.
            formError = when {
                !missing -> null
                bothRequired -> R.string.error_amounts_required
                else -> R.string.error_amount_required
            },
        )
    }

    private fun AmountBlock.validated(
        state: EntryEditState,
        visible: Boolean,
        required: Boolean,
    ): AmountBlock {
        if (!visible) return copy(walletError = null, amountError = null, amountMissing = false)

        val decimals = state.currency(currencyCode)?.decimals ?: DEFAULT_DECIMALS
        val entered = MoneyFormat.parseAmount(amount, decimals)
        // Blank or zero is a half that is not filled in. On a one-sided category that is
        // the only half there is; on a two-sided one both are needed — money that leaves
        // one wallet has to arrive somewhere.
        if (amount.isBlank() || entered?.isZero == true) {
            return copy(
                walletError = R.string.error_wallet_required.takeIf { required && walletId == null },
                amountError = null,
                amountMissing = required,
            )
        }
        return copy(
            walletError = R.string.error_wallet_required.takeIf { walletId == null },
            amountError = R.string.error_amount_invalid.takeIf { entered == null },
            amountMissing = false,
        )
    }

    /**
     * Turns the entry on the form into a new one: the same category, title, place, notes,
     * quantity, wallets, amounts and coordinates, timed now. The form goes on as a form
     * for a new entry, and saving it makes a second row rather than changing the first.
     *
     * Two things are not carried over. The receipt photo belongs to the purchase it was
     * taken at, and two rows pointing at one file would lose it when either was deleted.
     * The settled amounts are what the bank charged for the original operations, which
     * says nothing about what it will charge for these.
     */
    fun repeat() {
        if (original == null) return
        original = null
        form.update { state ->
            state.copy(
                date = LocalDate.now(),
                time = LocalTime.now(),
                photoFile = null,
                expense = state.expense.copy(settlement = "", isSettlementVisible = false),
                income = state.income.copy(settlement = "", isSettlementVisible = false),
                isEditMode = false,
                isDeleteDialogVisible = false,
            )
        }
    }

    fun showDeleteDialog() = form.update { it.copy(isDeleteDialogVisible = true) }

    fun hideDeleteDialog() = form.update { it.copy(isDeleteDialogVisible = false) }

    fun delete() {
        val entry = original ?: return
        hideDeleteDialog()
        commit.launch(viewModelScope) {
            try {
                entryRepository.delete(entry)
                photoStorage.delete(entry.photoFile)
                _events.send(EditorEvent.Closed)
            } catch (e: Exception) {
                _events.send(EditorEvent.Error(context, R.string.error_delete_entry, e))
            }
        }
    }

    private data class References(
        val categories: List<Category> = emptyList(),
        val currencies: List<Currency> = emptyList(),
        val wallets: List<TripWalletDetails> = emptyList(),
        val trip: Trip? = null,
    )

    private companion object {
        const val DEFAULT_DECIMALS = 2

        /**
         * The last minute a person would write, not the last instant there is: 23:59 is
         * what the time field offers and what anybody typing the end of a day types.
         */
        val LAST_MINUTE_OF_DAY: LocalTime = LocalTime.of(23, 59)
    }
}