package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.ui.theme.FieldShape

/**
 * Picking the wallet an entry moved through. Only the trip's own wallets are offered —
 * a wallet is what ties a payment method to a currency and to an opening balance, and a
 * wallet from another trip means nothing here.
 *
 * The last line opens the form for a new one, because the first entry of a trip is often
 * written before anybody has thought about wallets at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletPickerField(
    selectedId: Long?,
    wallets: List<TripWalletDetails>,
    onSelect: (TripWalletDetails) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.field_wallet,
    @StringRes errorRes: Int? = null,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    val selected = wallets.firstOrNull { it.wallet.id == selectedId }
    val selectedLabel = selected
        ?.let { "${it.paymentMethod.icon} ${walletName(it)}" }
        ?: stringResource(R.string.value_not_set)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = FieldShape,
            colors = pickerFieldColors(enabled = true),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_wallet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(wallets, key = { it.wallet.id }) { details ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(details)
                                    sheetVisible = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = details.paymentMethod.icon,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                text = walletName(details),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = details.currency.code,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.wallet_new),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheetVisible = false
                                    onCreate()
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
