package app.jourjour.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.DateField
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.ObserveEditorEvents
import app.jourjour.ui.common.WalletFormSheet
import app.jourjour.ui.common.SecondaryButton
import kotlinx.coroutines.launch

@Composable
fun TripEditScreen(
    onClose: () -> Unit,
    onCreated: (Long) -> Unit = { onClose() },
    onDeleted: () -> Unit = onClose,
    viewModel: TripEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveEditorEvents(viewModel.events, snackbarHostState, onClose, onCreated, onDeleted)

    EditorScaffold(
        title = stringResource(
            if (state.isEditMode) R.string.title_trip_edit else R.string.title_trip_new
        ),
        onBack = onClose,
        onSave = viewModel::save,
        onDelete = viewModel::showDeleteDialog.takeIf { state.isEditMode },
        snackbarHostState = snackbarHostState,
    ) {
        FormTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            labelRes = R.string.field_name,
            errorRes = state.nameError,
        )
        DateField(
            date = state.startDate,
            onDateChange = viewModel::updateStartDate,
            labelRes = R.string.field_start_date,
        )
        DateField(
            date = state.endDate,
            onDateChange = viewModel::updateEndDate,
            labelRes = R.string.field_end_date,
            errorRes = state.endDateError,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        TripCoverField(
            photoFile = state.photoFile,
            coverStorage = viewModel.coverStorage,
            onPick = viewModel::pickCover,
            onRemove = viewModel::removeCover,
        )

        if (state.photoCount > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SecondaryButton(
                onClick = viewModel::showPhotoDeleteDialog,
                text = stringResource(R.string.action_delete_trip_photos, state.photoCount),
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        RatesSection(
            state = state,
            onAdd = { viewModel.openRateSheet() },
            onEdit = { index -> viewModel.openRateSheet(index) },
            onRemove = viewModel::removeRate,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        WalletsSection(
            state = state,
            onAdd = { viewModel.openWalletSheet() },
            onEdit = { index -> viewModel.openWalletSheet(index) },
            onRemove = viewModel::removeWallet,
        )
    }

    state.rateSheet?.let { sheet ->
        RateFormSheet(
            titleRes = if (sheet.isNew) R.string.title_rate_new else R.string.title_rate_edit,
            confirmRes = if (sheet.isNew) R.string.action_add else R.string.action_save,
            form = sheet.form,
            currencies = state.currencies,
            baseCurrency = state.baseCurrency,
            onSelectCurrency = viewModel::rateSheetSelectCurrency,
            onMultiplierChange = viewModel::rateSheetUpdateMultiplier,
            onRateChange = viewModel::rateSheetUpdateRate,
            onFetch = viewModel::fetchRate,
            onConfirm = viewModel::confirmRate,
            onDismiss = viewModel::closeRateSheet,
        )
    }

    state.walletSheet?.let { sheet ->
        WalletSheet(
            sheet = sheet,
            state = state,
            onSelectPaymentMethod = viewModel::walletSheetSelectPaymentMethod,
            onSelectCurrency = viewModel::walletSheetSelectCurrency,
            onNameChange = viewModel::walletSheetUpdateName,
            onBalanceChange = viewModel::walletSheetUpdateBalance,
            onConfirm = viewModel::confirmWallet,
            onDismiss = viewModel::closeWalletSheet,
        )
    }

    if (state.isPhotoDeleteDialogVisible) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_trip_photos, state.photoCount),
            onConfirm = viewModel::deleteAllPhotos,
            onDismiss = viewModel::hidePhotoDeleteDialog,
        )
    }

    if (state.isDeleteDialogVisible) {
        // A trip always goes, taking its wallets and entries along, so the wording differs
        // from the dictionaries, where references hold a deletion back.
        val message = stringResource(R.string.confirm_delete_trip_message, state.name) +
            if (state.entryCount > 0) {
                " " + stringResource(R.string.confirm_delete_trip_entries, state.entryCount)
            } else {
                ""
            }
        ConfirmDeleteDialog(
            message = message,
            onConfirm = viewModel::delete,
            onDismiss = viewModel::hideDeleteDialog,
        )
    }
}

/**
 * The rates of the trip, each written as the equation it is: so many units of one
 * currency, and what they come to in the base one.
 *
 * Nothing here is required and nothing is checked for completeness — a rate missing for a
 * currency the trip spent shows up where it matters, in the report. What the section does
 * insist on is a base currency: without one there is nothing to convert to, so the button
 * is replaced by the one sentence that says where to set it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatesSection(
    state: TripEditState,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.field_rates),
            style = MaterialTheme.typography.titleSmall,
        )
        // The long explanation of what a rate is for lives behind this, rather than under
        // the heading: it is read once and then never again, and a paragraph standing on
        // the form for good would push the wallets off the screen.
        val tooltipState = rememberTooltipState(isPersistent = true)
        val scope = rememberCoroutineScope()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                RichTooltip(
                    title = { Text(stringResource(R.string.field_rates)) },
                ) {
                    Text(stringResource(R.string.hint_rates))
                }
            },
            state = tooltipState,
        ) {
            IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.action_about_rates),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    state.rates.forEachIndexed { index, rate ->
        // No padding of its own, unlike a wallet row: a rate is one line, and the button
        // beside it already holds the row open to the smallest thing a finger can hit.
        // Padding on top of that would leave a gap the height of a second line that is
        // never coming.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(index) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.rate_summary,
                    rate.multiplier.toString(),
                    rate.fromCurrencyCode,
                    MoneyFormat.formatRate(rate.rate),
                    rate.toCurrencyCode,
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemove(index) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove_rate),
                )
            }
        }
    }

    val strayBases = state.ratesAgainstAnotherBase
    if (strayBases.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.warning_rates_other_base,
                strayBases.joinToString(", "),
                state.baseCurrency.orEmpty(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (state.baseCurrency == null) {
        Text(
            text = stringResource(R.string.hint_rates_need_base_currency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        SecondaryButton(
            onClick = onAdd,
            text = stringResource(R.string.action_add_rate),
            icon = Icons.Default.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun WalletsSection(
    state: TripEditState,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Text(text = stringResource(R.string.field_wallets), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringResource(R.string.hint_wallets),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    state.wallets.forEachIndexed { index, wallet ->
        val paymentMethod = state.paymentMethod(wallet.paymentMethodId)
        val currency = state.currency(wallet.currencyCode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(index) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = paymentMethod?.icon.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(40.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = walletName(LocalContext.current, wallet.name, paymentMethod, currency),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.wallet_opening_balance_summary,
                        MoneyFormat.formatAmount(wallet.openingBalance, currency?.decimals ?: 2),
                        currency?.symbol ?: wallet.currencyCode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRemove(index) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove_wallet),
                )
            }
        }
    }

    SecondaryButton(
        onClick = onAdd,
        text = stringResource(R.string.action_add_wallet),
        icon = Icons.Default.Add,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun WalletSheet(
    sheet: TripWalletSheet,
    state: TripEditState,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onSelectCurrency: (String?) -> Unit,
    onNameChange: (String) -> Unit,
    onBalanceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WalletFormSheet(
        titleRes = if (sheet.isNew) R.string.title_wallet_new else R.string.title_wallet_edit,
        confirmRes = if (sheet.isNew) R.string.action_add else R.string.action_save,
        form = sheet.form,
        paymentMethods = state.paymentMethods,
        currencies = state.currencies,
        onSelectPaymentMethod = onSelectPaymentMethod,
        onSelectCurrency = onSelectCurrency,
        onNameChange = onNameChange,
        onBalanceChange = onBalanceChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        currencyLocked = state.walletCurrencyIsLocked,
    )
}
