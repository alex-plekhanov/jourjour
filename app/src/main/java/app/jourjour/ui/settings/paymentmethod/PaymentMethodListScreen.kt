package app.jourjour.ui.settings.paymentmethod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.ui.common.DictionaryListScaffold
import app.jourjour.ui.navigation.Route

@Composable
fun PaymentMethodListScreen(
    onBack: () -> Unit,
    onOpenPaymentMethod: (Long) -> Unit,
    viewModel: PaymentMethodListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DictionaryListScaffold(
        title = stringResource(R.string.title_payment_methods),
        onBack = onBack,
        onAdd = { onOpenPaymentMethod(Route.NEW) },
        isLoading = state.isLoading,
        isEmpty = state.paymentMethods.isEmpty(),
        emptyMessage = stringResource(R.string.empty_payment_methods),
    ) {
        items(state.paymentMethods, key = { it.id }) { paymentMethod ->
            PaymentMethodRow(
                paymentMethod = paymentMethod,
                onClick = { onOpenPaymentMethod(paymentMethod.id) },
            )
        }
    }
}

@Composable
private fun PaymentMethodRow(paymentMethod: PaymentMethod, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = paymentMethod.icon,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.width(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedName(paymentMethod.name, paymentMethod.nameKey),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (paymentMethod.fixedCurrency) {
                        R.string.payment_method_foreign_denied
                    } else {
                        R.string.payment_method_foreign_allowed
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
