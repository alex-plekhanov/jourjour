package app.jourjour.ui.checklist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.data.entity.Trip
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.dismissingFocus
import app.jourjour.ui.common.pickerFieldColors
import app.jourjour.ui.common.rememberDateFormatter
import app.jourjour.ui.theme.FieldShape
import java.time.format.FormatStyle

/**
 * Taking a checklist off a trip already made.
 *
 * The fields arrive one at a time, each narrowing the one above it, so that the form is
 * only ever as long as the question being asked: most of the time it is one field and a
 * button, and the two below it are there for the rarer case of wanting a single section
 * or a single line out of an old list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistCopySheet(
    copy: ChecklistCopy,
    onSelectTrip: (Long) -> Unit,
    onSelectSection: (String?) -> Unit,
    onSelectItem: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.title_copy_checklist),
                style = MaterialTheme.typography.titleMedium,
            )

            TripPickerField(
                trip = copy.trip,
                trips = copy.trips,
                onSelect = onSelectTrip,
                errorRes = copy.tripError,
            )

            if (copy.sourceIsEmpty) {
                Text(
                    text = stringResource(R.string.hint_copy_source_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Each field waits for the one above it: a section cannot be picked out of a
            // trip nobody has named, and an item cannot be picked out of every section at
            // once.
            if (copy.sections.isNotEmpty()) {
                NarrowingField(
                    selected = copy.section,
                    options = copy.sections,
                    onSelect = onSelectSection,
                    labelRes = R.string.field_copy_section,
                    everythingRes = R.string.copy_all_sections,
                    titleRes = R.string.select_section,
                )
            }
            if (copy.section != null) {
                NarrowingField(
                    selected = copy.title,
                    options = copy.titles,
                    onSelect = onSelectItem,
                    labelRes = R.string.field_copy_item,
                    everythingRes = R.string.copy_all_items,
                    titleRes = R.string.select_checklist_item,
                )
            }

            PrimaryButton(
                onClick = onConfirm,
                text = stringResource(R.string.action_copy),
                enabled = copy.canCopy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

/** Which trip to take the list off. Named and dated, the way the trip list names them. */
@Composable
private fun TripPickerField(
    trip: Trip?,
    trips: List<Trip>,
    onSelect: (Long) -> Unit,
    @StringRes errorRes: Int?,
) {
    val dates = rememberDateFormatter(FormatStyle.MEDIUM)
    PickerField(
        value = trip?.name ?: stringResource(R.string.value_not_set),
        labelRes = R.string.field_copy_from_trip,
        errorRes = errorRes,
        titleRes = R.string.select_trip,
    ) { dismiss ->
        items(trips, key = { it.id }) { option ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        dismiss()
                        onSelect(option.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(text = option.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${dates.format(option.startDate)} — ${dates.format(option.endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One of the two optional fields: pick one of these, or take them all.
 *
 * Taking them all is written out — "All sections" — rather than shown as an empty field
 * needing a line of explanation under it. It is the ordinary answer, not the absence of
 * one, and a field saying what it will do needs nothing said about it. It stands at the
 * head of the list too, since a field once narrowed has to be openable again.
 */
@Composable
private fun NarrowingField(
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    @StringRes labelRes: Int,
    @StringRes everythingRes: Int,
    @StringRes titleRes: Int,
) {
    val everything = stringResource(everythingRes)
    PickerField(
        value = selected ?: everything,
        labelRes = labelRes,
        titleRes = titleRes,
    ) { dismiss ->
        item {
            Text(
                text = everything,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        dismiss()
                        onSelect(null)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            HorizontalDivider()
        }
        items(options, key = { it }) { option ->
            Text(
                text = option,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        dismiss()
                        onSelect(option)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

/**
 * A read-only field that opens a sheet, which is how every picker in the app behaves. The
 * rows are the caller's, since the three here hold different things.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    value: String,
    @StringRes labelRes: Int,
    @StringRes titleRes: Int,
    @StringRes errorRes: Int? = null,
    @StringRes hintRes: Int? = null,
    rows: androidx.compose.foundation.lazy.LazyListScope.(dismiss: () -> Unit) -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            isError = errorRes != null,
            supportingText = when {
                errorRes != null -> {
                    { Text(stringResource(errorRes)) }
                }
                hintRes != null -> {
                    { Text(stringResource(hintRes)) }
                }
                else -> null
            },
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
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    rows { sheetVisible = false }
                }
            }
        }
    }
}
