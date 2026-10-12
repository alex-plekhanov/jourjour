package app.jourjour.ui.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.core.money.MoneyFormat
import app.jourjour.ui.common.CategoryPickerField
import app.jourjour.ui.common.CategorySheet
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.DateField
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.ObserveEditorEvents
import app.jourjour.ui.common.SecondaryIconButton
import app.jourjour.ui.common.TimeField
import app.jourjour.ui.common.WalletFormSheet
import app.jourjour.ui.common.WalletPickerField
import app.jourjour.ui.common.FavoriteTitles
import app.jourjour.ui.common.FOLD_TOGGLE_SIZE
import app.jourjour.ui.common.StackedBottomShape
import app.jourjour.ui.common.StackedTopShape
import app.jourjour.ui.common.TitleWithNotesField
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.dismissingFocus
import app.jourjour.ui.common.formFieldColors
import app.jourjour.ui.common.pickerFieldColors
import kotlinx.coroutines.launch
import app.jourjour.R
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.ui.theme.FieldShape

/**
 * Wide enough for the shortest form of a date in any locale — 11.09.26, 9/11/26 — and for
 * the field's own label, and no wider: what it does not take goes to whatever shares its
 * row. A ceiling rather than a width, since on a narrow phone the row has discs on it that
 * cannot be made smaller.
 */
private val DATE_FIELD_WIDTH = 112.dp

/** Wide enough for a three-letter code and the arrow that opens the picker. */
private val CURRENCY_FIELD_WIDTH = 112.dp

/** Wide enough for the longest short time a locale writes, which is 12:45 PM. */
private val TIME_FIELD_WIDTH = 124.dp
private val StackedBottomShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

@Composable
fun EntryEditScreen(
    onClose: () -> Unit,
    viewModel: EntryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noMapsAppMessage = stringResource(R.string.error_no_maps_app)

    ObserveEditorEvents(
        viewModel.events,
        snackbarHostState,
        onClose
    )

    val noCameraMessage = stringResource(R.string.error_no_camera)
    val noShareTargetMessage = stringResource(R.string.error_no_share_target)

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val onNoMapsApp: () -> Unit = { showMessage(noMapsAppMessage) }

    // A new entry opens on the category sheet, and the date, the currency and the payment
    // method are already filled in by the time it closes. The amount is the one thing left
    // to type, so the keyboard goes straight to it.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(state.categoryId) {
        if (!state.isEditMode && state.categoryId != null) {
            runCatching { amountFocus.requestFocus() }
        }
    }

    // Opening the notes is a request to write them, so the cursor follows. Only when the
    // control was pressed: an entry that already has notes opens with them on show, and
    // taking the cursor there would be taking it away from whatever was being edited.
    val notesFocus = remember { FocusRequester() }
    var notesAsked by remember { mutableStateOf(false) }
    LaunchedEffect(state.isNotesVisible, notesAsked) {
        if (state.isNotesVisible && notesAsked) {
            runCatching { notesFocus.requestFocus() }
            notesAsked = false
        }
    }

    EditorScaffold(
        title = stringResource(
            if (state.isEditMode) R.string.title_entry_edit else R.string.title_entry_new
        ),
        onBack = onClose,
        onSave = viewModel::save,
        onDelete = viewModel::showDeleteDialog.takeIf { state.isEditMode },
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.isEditMode) {
                IconButton(onClick = viewModel::repeat) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.action_repeat_entry),
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // As wide as they need and no wider, but able to give way: the discs beside
            // them are measured first, and on a narrow phone the two fields share what is
            // left rather than pushing a disc off the edge of the row.
            DateField(
                date = state.date,
                onDateChange = viewModel::updateDate,
                labelRes = R.string.field_date,
                modifier = Modifier.weight(1f).widthIn(max = DATE_FIELD_WIDTH),
                compact = true,
            )
            TimeField(
                time = state.time,
                onTimeChange = viewModel::updateTime,
                labelRes = R.string.field_time,
                modifier = Modifier.weight(1f).widthIn(max = TIME_FIELD_WIDTH),
            )
            // In the space the two narrow fields leave, and centred against them rather
            // than hung from the top of the row: they are discs, not fields, and nothing
            // above them labels what they do.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                SecondaryIconButton(
                    onClick = viewModel::stepToEarlierMoment,
                    painter = painterResource(R.drawable.ic_moment_before),
                    contentDescription = stringResource(R.string.action_moment_before),
                )
                SecondaryIconButton(
                    onClick = viewModel::stepToLaterMoment,
                    painter = painterResource(R.drawable.ic_moment_after),
                    contentDescription = stringResource(R.string.action_moment_after),
                )
            }
        }
        CategoryPickerField(
            category = state.category,
            onClick = viewModel::showCategorySheet,
            modifier = Modifier.fillMaxWidth(),
            errorRes = state.categoryError,
        )

        if (state.category != null) {
            if (state.showExpense) {
                AmountSection(
                    labelRes = R.string.section_expense,
                    settlementLabelRes = R.string.field_settlement_amount_expense,
                    block = state.expense,
                    state = state,
                    onWallet = { viewModel.selectWallet(expense = true, wallet = it) },
                    onNewWallet = { viewModel.openWalletSheet(expense = true) },
                    onCurrency = viewModel::updateExpenseCurrency,
                    onAmount = viewModel::updateExpenseAmount,
                    onSettlement = viewModel::updateExpenseSettlement,
                    onToggleSettlement = { viewModel.toggleSettlement(expense = true) },
                    amountFocus = amountFocus,
                )
            }
            if (state.showIncome) {
                AmountSection(
                    labelRes = R.string.section_income,
                    settlementLabelRes = R.string.field_settlement_amount_income,
                    block = state.income,
                    state = state,
                    onWallet = { viewModel.selectWallet(expense = false, wallet = it) },
                    onNewWallet = { viewModel.openWalletSheet(expense = false) },
                    onCurrency = viewModel::updateIncomeCurrency,
                    onAmount = viewModel::updateIncomeAmount,
                    onSettlement = viewModel::updateIncomeSettlement,
                    onToggleSettlement = { viewModel.toggleSettlement(expense = false) },
                    // On an exchange the expense half comes first and already has the cursor.
                    amountFocus = amountFocus.takeIf { !state.showExpense },
                )
            }

            if (state.showQuantity) {
                QuantityField(
                    state = state,
                    onQuantityChange = viewModel::updateQuantity,
                )
            }

            TitleWithNotesField(
                title = state.title,
                notes = state.notes,
                isFavorite = state.isTitleFavorite,
                isNotesVisible = state.isNotesVisible,
                onTitleChange = viewModel::updateTitle,
                onNotesChange = viewModel::updateNotes,
                onToggleFavorite = viewModel::toggleTitleFavorite,
                onToggleNotes = {
                    notesAsked = !state.isNotesVisible
                    viewModel.toggleNotes()
                },
                notesFocus = notesFocus,
            )

            FavoriteTitles(
                favorites = state.favorites,
                onPick = viewModel::pickFavorite,
            )

            PhotoField(
                photoFile = state.photoFile,
                photoStorage = viewModel.photoStorage,
                shotUnderReview = state.shotUnderReview,
                onShotTaken = viewModel::onShotTaken,
                onRenderShot = viewModel::renderShot,
                onKeepShot = viewModel::onShotKept,
                onDiscardShot = viewModel::onShotDiscarded,
                onRemove = viewModel::removePhoto,
                onNoCamera = { showMessage(noCameraMessage) },
                onNoShareTarget = { showMessage(noShareTargetMessage) },
            )

            FormTextField(
                value = state.place,
                onValueChange = viewModel::updatePlace,
                labelRes = R.string.field_place,
            )

            LocationField(
                state = state,
                onCapture = viewModel::captureLocation,
                onClear = viewModel::clearLocation,
                onPermissionDenied = viewModel::onLocationPermissionDenied,
                hasPermission = viewModel::hasLocationPermission,
                onNoMapsApp = onNoMapsApp,
            )

            state.formError?.let { formError ->
                Text(
                    text = stringResource(formError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    state.walletSheet?.let { sheet ->
        WalletFormSheet(
            titleRes = R.string.title_wallet_new,
            confirmRes = R.string.action_add,
            form = sheet.form,
            paymentMethods = sheet.paymentMethods,
            currencies = state.currencies,
            onSelectPaymentMethod = viewModel::walletSheetSelectPaymentMethod,
            onSelectCurrency = viewModel::walletSheetSelectCurrency,
            onNameChange = viewModel::walletSheetUpdateName,
            onBalanceChange = viewModel::walletSheetUpdateBalance,
            onConfirm = viewModel::confirmWallet,
            onDismiss = viewModel::closeWalletSheet,
        )
    }

    if (state.isCategorySheetVisible) {
        CategorySheet(
            categories = state.categories,
            onSelect = viewModel::selectCategory,
            onDismiss = viewModel::hideCategorySheet,
        )
    }

    if (state.isDeleteDialogVisible) {
        ConfirmDeleteDialog(
            message = stringResource(
                R.string.confirm_delete_entry_message,
                state.title.ifBlank {
                    state.category?.let { localizedName(it.name, it.nameKey) }.orEmpty()
                },
            ),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::hideDeleteDialog,
        )
    }
}


@Composable
private fun QuantityField(state: EntryEditState, onQuantityChange: (String) -> Unit) {
    MoneyField(
        value = state.quantity,
        onValueChange = onQuantityChange,
        currency = null,
        labelRes = R.string.field_quantity,
    )
    // The unit price is for reference only: never stored, always derived from the amount
    state.unitPrice?.let { (price, currency) ->
        Text(
            text = stringResource(
                R.string.entry_unit_price,
                MoneyFormat.formatAmountWithCurrency(price, currency),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One half of the entry: the wallet it moved through, then the amount with its currency.
 * The wallet comes first because it settles what the currency may be. The half names
 * itself through the label of the amount field, which saves a heading over every two
 * fields.
 */
@Composable
private fun AmountSection(
    @StringRes labelRes: Int,
    @StringRes settlementLabelRes: Int,
    block: AmountBlock,
    state: EntryEditState,
    onWallet: (TripWalletDetails) -> Unit,
    onNewWallet: () -> Unit,
    onCurrency: (String?) -> Unit,
    onAmount: (String) -> Unit,
    onSettlement: (String) -> Unit,
    onToggleSettlement: () -> Unit,
    amountFocus: FocusRequester?,
) {
    // The settled amount only matters when payment was made in a currency other than the
    // wallet's own, and even then it is usually learnt later, from the bank. So it is
    // folded away behind a control the way the notes are, and opens already filled in on
    // an entry that has one.
    val walletCurrency = state.settlementCurrency(block)
    val open = walletCurrency != null && block.isSettlementVisible

    val settlementFocus = remember { FocusRequester() }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(open, asked) {
        if (open && asked) {
            runCatching { settlementFocus.requestFocus() }
            asked = false
        }
    }

    // Where the two rows meet, which is where the control goes. Measured rather than
    // assumed: the height of a text field follows the font the device is set to.
    var amountRowHeight by remember { mutableIntStateOf(0) }

    WalletPickerField(
        selectedId = block.walletId,
        wallets = state.wallets,
        onSelect = onWallet,
        onCreate = onNewWallet,
        errorRes = block.walletError,
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { amountRowHeight = it.height },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MoneyField(
                    value = block.amount,
                    onValueChange = onAmount,
                    // A deposit given back is a negative amount on the row that paid it,
                    // rather than an income of its own.
                    allowNegative = true,
                    currency = state.currency(block.currencyCode),
                    // No symbol inside the field: the currency stands beside it as a code.
                    showSymbol = false,
                    labelRes = labelRes,
                    errorRes = block.amountError,
                    isError = block.amountError != null || block.amountMissing,
                    // With the settled amount open the two rows are one block, so the edge
                    // they share is left square and only the outside of the pair rounded.
                    shape = if (open) StackedTopShape else FieldShape,
                    modifier = Modifier
                        .weight(1f)
                        .then(amountFocus?.let { Modifier.focusRequester(it) } ?: Modifier),
                )
                CurrencyPickerField(
                    selectedCode = block.currencyCode,
                    currencies = state.currencyOptions(block.walletId),
                    onSelect = onCurrency,
                    // The wallet decides it, unless its payment method lets the bank convert.
                    enabled = !state.currencyIsLocked(block.walletId),
                    shape = if (open) StackedTopShape else FieldShape,
                    modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
                    compact = true,
                )
            }
            if (open && walletCurrency != null) {
                // Laid out as the row above it, down to the width of the two fields: it is
                // the same sum in the wallet's own money, and a differently shaped row
                // would suggest otherwise. Its currency is the wallet's and nothing else,
                // so that picker is there to be read rather than opened.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MoneyField(
                        value = block.settlement,
                        onValueChange = onSettlement,
                        allowNegative = true,
                        currency = walletCurrency,
                        showSymbol = false,
                        labelRes = settlementLabelRes,
                        shape = StackedBottomShape,
                        spacedAbove = false,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(settlementFocus),
                    )
                    CurrencyPickerField(
                        selectedCode = walletCurrency.code,
                        currencies = listOf(walletCurrency),
                        onSelect = {},
                        enabled = false,
                        shape = StackedBottomShape,
                        spacedAbove = false,
                        modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
                        compact = true,
                    )
                }
                Text(
                    text = stringResource(R.string.hint_settlement_amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (walletCurrency != null) {
                // Room for the lower half of the control, which has nothing to sit on
                // while the field is away.
                Spacer(modifier = Modifier.height(FOLD_TOGGLE_SIZE / 2))
            }
        }

        if (walletCurrency != null) {
            val description = stringResource(
                if (open) R.string.action_hide_settlement else R.string.action_show_settlement
            )
            // Centred on the form rather than on the field it touches: what it opens is a
            // row as wide as the form, and the eye reads the control against the column of
            // rows, not against one of the two fields the row is divided into.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, amountRowHeight - FOLD_TOGGLE_SIZE.roundToPx() / 2) }
                    .size(FOLD_TOGGLE_SIZE),
            ) {
                IconButton(
                    onClick = {
                        asked = !open
                        onToggleSettlement()
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_double_down),
                        contentDescription = description,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (open) 180f else 0f),
                    )
                }
            }
        }
    }
}
