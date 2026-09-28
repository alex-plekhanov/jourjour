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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.text.localizedName
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.Currency
import app.jourjour.ui.theme.FieldShape

/**
 * Picking a currency. There are dozens of them in the dictionary, so instead of a
 * dropdown this opens a sheet that searches by code and by name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerField(
    selectedCode: String?,
    currencies: List<Currency>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.field_currency,
    @StringRes hintRes: Int? = null,
    @StringRes errorRes: Int? = null,
    enabled: Boolean = true,
    /** Narrow: the three-letter code alone, and only as wide as the caller makes it. */
    compact: Boolean = false,
    /** Square an edge to join this field to the one above or below it. */
    shape: Shape = FieldShape,
    /** The gap a stacked field leaves above itself. Off where nothing is stacked above. */
    spacedAbove: Boolean = true,
    /**
     * Whether the sheet offers a way back to no currency at all. Off where a currency is
     * required — an amount without one means nothing — and on where having none is a
     * working answer, as it is for the base currency.
     */
    clearable: Boolean = false,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    val selected = currencies.firstOrNull { it.code == selectedCode }
    val selectedLabel = when {
        selected != null && compact -> selected.code
        selected != null -> "${selected.code} — ${localizedName(selected.name, selected.nameKey)}"
        selectedCode != null -> selectedCode
        // A narrow field has room for a code and nothing more, so an empty one says so
        // with a dash rather than with words it would have to cut in half.
        compact -> EMPTY_COMPACT
        else -> stringResource(R.string.value_not_set)
    }

    // The field is disabled on purpose: that way it does not intercept the tap and the
    // wrapper receives it. A transparent layer on top would look the same but would hide
    // the field from the accessibility tree — TalkBack would read out an empty button
    // instead of the currency.
    Box(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clickable(enabled = enabled, onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            enabled = false,
            isError = errorRes != null,
            supportingText = when {
                errorRes != null -> {
                    { Text(stringResource(errorRes)) }
                }
                hintRes != null && !compact -> {
                    { Text(stringResource(hintRes)) }
                }
                else -> null
            },
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = shape,
            colors = pickerFieldColors(enabled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (spacedAbove) 8.dp else 0.dp),
        )
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CurrencyPickerSheet(
                currencies = currencies,
                clearable = clearable,
                onSelect = {
                    onSelect(it)
                    sheetVisible = false
                },
            )
        }
    }
}


@Composable
private fun CurrencyPickerSheet(
    currencies: List<Currency>,
    clearable: Boolean,
    onSelect: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val matching = remember(currencies, query) {
        if (query.isBlank()) {
            currencies
        } else {
            currencies.filter {
                it.code.contains(query, ignoreCase = true) ||
                    resolveName(context, it.name, it.nameKey).contains(query, ignoreCase = true)
            }
        }
    }
    // A handful of currencies get used over and over on a trip and the rest never; the
    // recent ones go on top, and everything else falls back to the alphabet of codes,
    // which is what a person scanning a long list is actually reading.
    val recent = remember(matching) {
        matching.filter { it.usedAt != null }.sortedByDescending { it.usedAt }.take(RECENT_COUNT)
    }
    val rest = remember(matching, recent) { (matching - recent.toSet()).sortedBy { it.code } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.select_currency),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_currencies)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = FieldShape,
            colors = formFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            // Only while nothing is typed: a search is for a currency, and this row is the
            // absence of one.
            if (clearable && query.isBlank()) {
                item {
                    NotSetRow(onSelect)
                    HorizontalDivider()
                }
            }
            items(recent, key = { it.code }) { CurrencyRow(it, onSelect) }
            if (recent.isNotEmpty() && rest.isNotEmpty()) {
                item { HorizontalDivider() }
            }
            items(rest, key = { it.code }) { CurrencyRow(it, onSelect) }
        }
    }
}

/** Choosing no currency, where that is an answer rather than an unfinished field. */
@Composable
private fun NotSetRow(onSelect: (String?) -> Unit) {
    Text(
        text = stringResource(R.string.value_not_set),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(null) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun CurrencyRow(currency: Currency, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(currency.code) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = currency.code,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = localizedName(currency.name, currency.nameKey),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = currency.symbol, style = MaterialTheme.typography.titleMedium)
    }
}

/** How many recently used currencies stand above the line. */
private const val RECENT_COUNT = 5

/** What a narrow picker shows while nothing is chosen. */
private const val EMPTY_COMPACT = "—"
