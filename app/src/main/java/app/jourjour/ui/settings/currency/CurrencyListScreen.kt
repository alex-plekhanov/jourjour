package app.jourjour.ui.settings.currency

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
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.DictionaryListScaffold

@Composable
fun CurrencyListScreen(
    onBack: () -> Unit,
    onOpenCurrency: (String?) -> Unit,
    viewModel: CurrencyListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DictionaryListScaffold(
        title = stringResource(R.string.title_currencies),
        onBack = onBack,
        onAdd = { onOpenCurrency(null) },
        isLoading = state.isLoading,
        isEmpty = state.currencies.isEmpty(),
        emptyMessage = stringResource(
            if (state.query.isBlank()) R.string.empty_currencies else R.string.empty_search
        ),
        searchQuery = state.query,
        onSearchQueryChange = viewModel::updateQuery,
        searchPlaceholder = stringResource(R.string.search_currencies),
    ) {
        items(state.currencies, key = { it.code }) { currency ->
            CurrencyRow(currency = currency, onClick = { onOpenCurrency(currency.code) })
        }
    }
}

@Composable
private fun CurrencyRow(currency: Currency, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = currency.code,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedName(currency.name, currency.nameKey),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.currency_decimals_summary, currency.decimals),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = currency.symbol,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
