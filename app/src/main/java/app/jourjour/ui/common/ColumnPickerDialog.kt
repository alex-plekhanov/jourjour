package app.jourjour.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.jourjour.R

/**
 * What a spreadsheet should carry.
 *
 * Asked only for the XLSX: that file is read by a person, who rarely wants every column at
 * once. The CSV is not asked, because an import reads it back and has to find the whole
 * record there.
 *
 * The ticks start on whatever the last sheet was written with, and nothing can be exported
 * with none of them. Which columns there are is the caller's business — the entries and
 * the schedule both ask this, and they are not the same table.
 */
@Composable
fun <T> ColumnPickerDialog(
    columns: List<T>,
    selected: Set<T>,
    titleOf: (T) -> String,
    onToggle: (T) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_export_columns)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                columns.forEach { column ->
                    ColumnRow(
                        title = titleOf(column),
                        checked = column in selected,
                        onToggle = { onToggle(column) },
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                onClick = onConfirm,
                text = stringResource(R.string.action_export),
                // A sheet of no columns is not worth writing, let alone saving as a choice.
                enabled = selected.isNotEmpty(),
            )
        },
        dismissButton = {
            SecondaryButton(onClick = onDismiss, text = stringResource(R.string.action_cancel))
        },
    )
}

@Composable
private fun ColumnRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the target, and it is the row that carries the tick for a
            // screen reader: a 20 dp checkbox is a poor thing to aim at or to announce.
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
