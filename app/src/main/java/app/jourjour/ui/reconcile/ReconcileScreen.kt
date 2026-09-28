package app.jourjour.ui.reconcile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.keyboardInsets
import app.jourjour.ui.common.rememberDateTimeFormatter
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.ObserveEditorEvents
import app.jourjour.ui.common.PrimaryButton
import java.time.format.FormatStyle

/**
 * The moment an operation happened, both halves of it short. The time matters here: a card
 * charged twice in one day shows up as two lines that are otherwise the same, and the hour
 * is what tells them apart in the banking app.
 */
/** Wide enough for an amount with three decimals, and the same on every row. */
private val SETTLEMENT_FIELD_WIDTH = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(
    onBack: () -> Unit,
    viewModel: ReconcileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Saving does not close this screen: reconciliation is filled in a bit at a time, as
    // the operations turn up in the bank.
    ObserveEditorEvents(
        viewModel.events,
        snackbarHostState,
        onClose = {})

    Scaffold(
        modifier = Modifier.windowInsetsPadding(keyboardInsets()),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.wallet?.let { walletName(it) } ?: stringResource(R.string.title_reconcile))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.hasChanges) {
                Surface(shadowElevation = 8.dp) {
                    PrimaryButton(
                        onClick = viewModel::save,
                        text = stringResource(R.string.action_save),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.rows.isEmpty() -> Text(
                    text = stringResource(R.string.empty_reconcile),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { WalletSummary(state) }
                    state.accountCurrency?.let { currency ->
                        item { SettlementColumnHeader(currency) }
                    }
                    items(count = state.rows.size, key = { state.rows[it].key }) { index ->
                        val row = state.rows[index]
                        SettlementRowItem(
                            row = row,
                            onChange = { value -> viewModel.updateSettlement(row, value) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletSummary(state: ReconcileState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(
                R.string.reconcile_group_summary,
                state.spentTotals.joinToString(", ") { (amount, currency) ->
                    MoneyFormat.formatAmountWithCurrency(amount, currency)
                },
                state.accountCurrency
                    ?.let { MoneyFormat.formatAmountWithCurrency(state.settledTotal, it) }
                    .orEmpty(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.missingCount > 0) {
            Text(
                text = stringResource(R.string.reconcile_missing, state.missingCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * What the column of fields is for, said once above it. Every one of them is an amount in
 * the account's own currency, so the fields themselves need only a word for the direction.
 */
@Composable
private fun SettlementColumnHeader(currency: Currency) {
    Text(
        text = stringResource(R.string.reconcile_account_change, currency.code),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettlementRowItem(row: SettlementRow, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Even above and below: the field brings no spacing of its own here, so what
            // is set is what is seen on both sides of the divider.
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${row.dateTime.format(rememberDateTimeFormatter(FormatStyle.SHORT, FormatStyle.SHORT))} · " +
                    MoneyFormat.formatAmountWithCurrency(row.spent, row.spentCurrency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyField(
            value = row.settlement,
            onValueChange = onChange,
            currency = row.accountCurrency,
            // A refund is credited, not charged, and the row has to say which it is. One
            // word each: the header above says what account, and in what currency.
            labelRes = if (row.isExpense) {
                R.string.field_settlement_short_expense
            } else {
                R.string.field_settlement_short_income
            },
            // The symbol would say what the heading has already said, and a symbol taller
            // than a digit — the dirham's is — makes a filled field taller than an empty
            // one, so a column of them comes out ragged.
            showSymbol = false,
            spacedAbove = false,
            modifier = Modifier.width(SETTLEMENT_FIELD_WIDTH),
        )
    }
}
