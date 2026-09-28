package app.jourjour.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.checklist.ChecklistSection
import app.jourjour.data.entity.ChecklistItem
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.OverflowMenu

/**
 * What still has to be done before the trip.
 *
 * Read from a doorway with one hand full, so everything it can be asked is one tap: the
 * row ticks the item, the heading folds the section. Anything that changes the words
 * themselves is behind the three dots, out of the way of the tapping.
 */
@Composable
fun ChecklistTab(
    state: ChecklistState,
    viewModel: ChecklistViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.sections.isEmpty() ->
                EmptyList(
                    R.string.empty_checklist,
                    R.string.empty_checklist_hint,
                    R.string.about_checklist,
                )

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                state.sections.forEach { section ->
                    item(key = "section-${section.name}") {
                        SectionHeader(
                            section = section,
                            collapsed = state.isCollapsed(section),
                            onToggle = { viewModel.toggleSection(section.name) },
                            onRename = { viewModel.openRename(section.name) },
                            onDelete = { viewModel.askDeleteSection(section) },
                        )
                    }
                    if (state.isCollapsed(section)) return@forEach
                    items(section.items, key = { "item-${it.id}" }) { item ->
                        ItemRow(
                            item = item,
                            onToggle = { viewModel.toggleDone(item) },
                            onEdit = { viewModel.openSheet(item) },
                            onDelete = { viewModel.deleteItem(item) },
                        )
                    }
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        ChecklistItemSheetContent(
            sheet = sheet,
            sections = state.sectionNames,
            onSelectSection = viewModel::sheetSelectSection,
            onNewSection = viewModel::sheetNewSection,
            onNewSectionChange = viewModel::sheetUpdateNewSection,
            onTitleChange = viewModel::sheetUpdateTitle,
            onDoneChange = viewModel::sheetUpdateDone,
            onSkippedChange = viewModel::sheetUpdateSkipped,
            onConfirm = viewModel::confirmSheet,
            onDismiss = viewModel::closeSheet,
        )
    }

    state.copy?.let { copy ->
        ChecklistCopySheet(
            copy = copy,
            onSelectTrip = viewModel::copySelectTrip,
            onSelectSection = viewModel::copySelectSection,
            onSelectItem = viewModel::copySelectItem,
            onConfirm = viewModel::confirmCopy,
            onDismiss = viewModel::closeCopy,
        )
    }

    state.rename?.let { rename ->
        SectionRenameDialog(
            rename = rename,
            onChange = viewModel::updateRename,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::closeRename,
        )
    }

    state.deletion?.let { deletion ->
        ConfirmDeleteDialog(
            message = stringResource(
                R.string.confirm_delete_checklist_section,
                deletion.name,
                deletion.count,
            ),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * A section, as a band across the list.
 *
 * Struck through once nothing in it is waiting on anybody, the same mark its items carry,
 * because a section is only ever the sum of them.
 */
@Composable
private fun SectionHeader(
    section: ChecklistSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                // The way a tree is drawn: pointing along the row while it is shut,
                // pointing at what it holds once it is open. An up arrow reads as a
                // button that scrolls, not as a branch.
                imageVector = if (collapsed) {
                    Icons.Default.KeyboardArrowRight
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = stringResource(
                    if (collapsed) R.string.action_expand_section else R.string.action_collapse_section
                ),
            )
        }
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleSmall,
            textDecoration = if (section.isSettled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        RowMenu(onEdit = onRename, onDelete = onDelete)
    }
}

/**
 * One thing to do.
 *
 * Three states, told apart the way each of them is: done is struck through, ruled out for
 * this trip is greyed and unticks nothing, and outstanding is plain. Ruled out is not a
 * third position of the tick — it says nothing about whether the thing was done, only
 * that nobody is asking.
 */
@Composable
private fun ItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isSkipped, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isDone,
            // The row is the target, not the box: a tick the width of a finger is easier
            // to hit than one the width of a tick.
            onCheckedChange = null,
            enabled = !item.isSkipped,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            color = when {
                item.isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        RowMenu(onEdit = onEdit, onDelete = onDelete)
    }
}

/** Edit and delete, the two things every row of this screen can be asked out of the way. */
@Composable
private fun RowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    OverflowMenu { dismiss ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            },
            onClick = { dismiss(); onEdit() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null)
            },
            onClick = { dismiss(); onDelete() },
        )
    }
}
