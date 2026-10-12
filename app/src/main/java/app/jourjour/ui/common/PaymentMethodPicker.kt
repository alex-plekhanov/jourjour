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
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.ui.theme.FieldShape

/** Picking a payment method in a sheet, the same way currencies are picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodPickerField(
    selectedId: Long?,
    paymentMethods: List<PaymentMethod>,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes labelRes: Int = R.string.field_payment_method,
    /** What the field is for, where that takes a paragraph; see FieldLabelWithInfo. */
    @StringRes hintRes: Int? = null,
    @StringRes hintDescriptionRes: Int? = null,
    @StringRes errorRes: Int? = null,
    enabled: Boolean = true,
    /**
     * Given where the field may be left unanswered, which puts "not set" at the head of
     * the list. A wallet has to be paid with something; a plan need not say yet.
     */
    onClear: (() -> Unit)? = null,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    val selected = paymentMethods.firstOrNull { it.id == selectedId }
    val selectedLabel = selected
        ?.let { "${it.icon} ${localizedName(it.name, it.nameKey)}" }
        ?: stringResource(R.string.value_not_set)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = {
                if (hintRes != null && hintDescriptionRes != null) {
                    FieldLabelWithInfo(labelRes, hintRes, hintDescriptionRes)
                } else {
                    FieldLabel(stringResource(labelRes))
                }
            },
            readOnly = true,
            enabled = false,
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            shape = FieldShape,
            colors = pickerFieldColors(enabled),
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
                    text = stringResource(R.string.select_payment_method),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    onClear?.let { clear ->
                        item(key = "not-set") {
                            Text(
                                text = stringResource(R.string.value_not_set),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clear()
                                        sheetVisible = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                    items(paymentMethods, key = { it.id }) { paymentMethod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(paymentMethod)
                                    sheetVisible = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = paymentMethod.icon,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                text = localizedName(paymentMethod.name, paymentMethod.nameKey),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
