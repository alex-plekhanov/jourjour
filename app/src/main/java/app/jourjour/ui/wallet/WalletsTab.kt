package app.jourjour.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.walletName
import app.jourjour.ui.common.BLOCK_CORNER
import app.jourjour.ui.common.BLOCK_ELEVATION
import app.jourjour.ui.common.EmptyList

/**
 * The wallets of a trip, one to a row: what each holds now, what the last recount of it
 * found, and everything that can be done to it.
 *
 * Tapping a block opens the wallet's own form, which is where it is changed and where it
 * is deleted. Recounting and reconciling are buttons on the block rather than gestures,
 * since neither is what a person reaches for first.
 */
@Composable
fun WalletsTab(
    state: WalletsState,
    viewModel: WalletsViewModel,
    onReconcile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.balances.isEmpty() ->
                EmptyList(
                    R.string.empty_wallets,
                    R.string.empty_wallets_hint,
                    R.string.about_wallets,
                )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.balances, key = { it.wallet.wallet.id }) { balance ->
                    WalletCard(
                        balance = balance,
                        onRecount = { viewModel.openSnapshotSheet(balance) },
                        onEdit = { viewModel.openWalletSheet(balance) },
                        onReconcile = { onReconcile(balance.wallet.wallet.id) },
                    )
                }
            }
        }
    }

    state.snapshotSheet?.let { sheet ->
        WalletSnapshotSheet(
            sheet = sheet,
            onActualChange = viewModel::updateSnapshotActual,
            onNotesChange = viewModel::updateSnapshotNotes,
            onSave = viewModel::saveSnapshot,
            onDismiss = viewModel::closeSnapshotSheet,
        )
    }
}

@Composable
private fun WalletCard(
    balance: WalletBalance,
    onRecount: () -> Unit,
    onEdit: () -> Unit,
    onReconcile: () -> Unit,
) {
    // Tapping the wallet opens its form, which is where it is deleted from as well: a
    // wallet is a thing to be looked at and changed, and everything else it can do says
    // so in words underneath.
    //
    // Raised off the page and given the corners an event of the schedule has, because it
    // is the same kind of thing to read: a block that stands for one record, in a list of
    // them, on a tab of a trip.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(BLOCK_ELEVATION, RoundedCornerShape(BLOCK_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = balance.wallet.paymentMethod.icon,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.width(40.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = walletName(balance.wallet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (balance.isKnown) {
                        Text(
                            text = MoneyFormat.formatAmountWithCurrency(
                                balance.balance,
                                balance.currency,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        // A half paid in another currency moves the wallet by whatever the
                        // bank charged. Until that is written down the balance would be a
                        // guess, and a guess printed as a total is worse than none.
                        Text(
                            text = pluralStringResource(
                                R.plurals.wallet_balance_unknown,
                                balance.pendingCount,
                                balance.pendingCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    balance.lastSnapshot?.let { snapshot ->
                        Text(
                            text = snapshotSummaryText(snapshot, balance.currency.decimals),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapshot.difference?.isZero == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRecount) {
                    Text(stringResource(R.string.action_recount))
                }
                // Only an account the bank converts for is ever charged an amount other
                // than the one entered, so only such a wallet has anything to reconcile.
                if (balance.canReconcile) {
                    TextButton(onClick = onReconcile) {
                        Text(stringResource(R.string.action_reconcile))
                    }
                }
            }
        }
    }
}
