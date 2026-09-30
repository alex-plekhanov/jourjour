package app.jourjour.ui.checklist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.FormSwitchRow
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.dismissingFocus
import app.jourjour.ui.common.pickerFieldColors
import app.jourjour.ui.theme.FieldShape

/**
 * The form of one checklist item, over the whole screen: which section it belongs to,
 * what it says, and the two flags that settle it.
 *
 * Delete is in the top bar, away from the button people came to press, as on every other
 * form — and it is the only place an item is deleted from, which leaves the rows of the
 * list to the two taps a checklist is read with.
 */
@Composable
fun ChecklistItemForm(
    sheet: ChecklistItemSheet,
    sections: List<String>,
    snackbarHostState: SnackbarHostState,
    onSelectSection: (String) -> Unit,
    onNewSection: () -> Unit,
    onNewSectionChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onSkippedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The form is a screen over the tab rather than a destination of its own, so Back is
    // wired here: shutting it is what Back means while it is open.
    BackHandler(onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize()) {
        EditorScaffold(
            title = stringResource(
                if (sheet.isNew) {
                    R.string.title_checklist_item_new
                } else {
                    R.string.title_checklist_item_edit
                }
            ),
            onBack = onDismiss,
            onSave = onConfirm,
            snackbarHostState = snackbarHostState,
            onDelete = onDelete.takeIf { !sheet.isNew },
        ) {
            SectionPickerField(
                selected = sheet.section,
                isNew = sheet.isNewSection,
                sections = sections,
                onSelect = onSelectSection,
                onNew = onNewSection,
                errorRes = sheet.sectionError.takeIf { !sheet.isNewSection },
            )
            if (sheet.isNewSection) {
                FormTextField(
                    value = sheet.newSection,
                    onValueChange = onNewSectionChange,
                    labelRes = R.string.field_section_name,
                    errorRes = sheet.sectionError,
                )
            }

            FormTextField(
                value = sheet.title,
                onValueChange = onTitleChange,
                labelRes = R.string.field_name,
                errorRes = sheet.titleError,
            )

            FormSwitchRow(
                titleRes = R.string.field_checklist_done,
                checked = sheet.isDone,
                onCheckedChange = onDoneChange,
            )
            FormSwitchRow(
                titleRes = R.string.field_checklist_skipped,
                subtitleRes = R.string.hint_checklist_skipped,
                checked = sheet.isSkipped,
                onCheckedChange = onSkippedChange,
            )
        }
    }
}

/**
 * Picking the section an item goes in, with a last line for one that does not exist yet.
 *
 * Modelled on the wallet picker for the same reason it was built that way: the first item
 * of a list is written before anybody has thought about what to call the group it is in.
 * A section invented here has no row anywhere until the item is saved, so backing out of
 * the sheet leaves nothing behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPickerField(
    selected: String?,
    isNew: Boolean,
    sections: List<String>,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    errorRes: Int?,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    val label = when {
        isNew -> stringResource(R.string.section_new)
        selected != null -> selected
        else -> stringResource(R.string.value_not_set)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = dismissingFocus { sheetVisible = true }),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            label = { FieldLabel(stringResource(R.string.field_section)) },
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
                    text = stringResource(R.string.select_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(sections, key = { it }) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheetVisible = false
                                    onSelect(name)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                    item {
                        if (sections.isNotEmpty()) HorizontalDivider()
                        Text(
                            text = stringResource(R.string.section_new),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheetVisible = false
                                    onNew()
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renaming a section.
 *
 * A dialog rather than a sheet because there is one field in it: a section has nothing of
 * its own but its name, and the rename reaches every item carrying it.
 */
@Composable
fun SectionRenameDialog(
    rename: SectionRename,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_section_edit)) },
        text = {
            FormTextField(
                value = rename.name,
                onValueChange = onChange,
                labelRes = R.string.field_section_name,
                errorRes = rename.error,
            )
        },
        confirmButton = {
            PrimaryButton(onClick = onConfirm, text = stringResource(R.string.action_save))
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
