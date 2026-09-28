package app.jourjour.ui.settings.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.dictionaryDeleteMessage
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.IconPicker
import app.jourjour.ui.common.FormSwitchRow
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.ObserveEditorEvents
import app.jourjour.ui.common.FavoriteChip
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.formFieldColors
import app.jourjour.ui.theme.FieldShape

/** The button that files a favourite away: a touch target, no larger. */
private val ADD_BUTTON_SIZE = 48.dp

@Composable
fun CategoryEditScreen(
    onClose: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveEditorEvents(viewModel.events, snackbarHostState, onClose)

    EditorScaffold(
        title = stringResource(
            if (state.isEditMode) R.string.title_category_edit else R.string.title_category_new
        ),
        onBack = onClose,
        onSave = viewModel::save,
        onDelete = viewModel::showDeleteDialog.takeIf { state.isEditMode },
        snackbarHostState = snackbarHostState,
    ) {
        IconPicker(
            selected = state.icon,
            presets = state.iconPresets,
            onSelect = viewModel::updateIcon,
            onAddPreset = viewModel::addIconPreset,
        )
        FormTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            labelRes = R.string.field_name,
            errorRes = state.nameError,
        )

        FormSwitchRow(
            titleRes = R.string.field_is_expense,
            checked = state.isExpense,
            onCheckedChange = viewModel::updateIsExpense,
        )
        FormSwitchRow(
            titleRes = R.string.field_is_income,
            checked = state.isIncome,
            onCheckedChange = viewModel::updateIsIncome,
        )
        state.directionError?.let { directionError ->
            Text(
                text = stringResource(directionError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FormSwitchRow(
            titleRes = R.string.field_track_quantity,
            subtitleRes = R.string.hint_track_quantity,
            checked = state.trackQuantity,
            onCheckedChange = viewModel::updateTrackQuantity,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        FavoritesEditor(
            favorites = state.favorites,
            draft = state.favoriteDraft,
            canAdd = state.canAddFavorite,
            onDraftChange = viewModel::updateFavoriteDraft,
            onAdd = viewModel::addFavorite,
            onRemove = viewModel::removeFavorite,
        )
    }

    if (state.isDeleteDialogVisible) {
        ConfirmDeleteDialog(
            message = dictionaryDeleteMessage(state.name),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::hideDeleteDialog,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoritesEditor(
    favorites: List<String>,
    draft: String,
    canAdd: Boolean,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.field_favorites),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(R.string.hint_favorites),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // A text field is taller than it looks: it keeps room above itself for the label to
    // float into. Centring the two against each other therefore hangs the button that much
    // too high, so it is placed against the field's box instead — measured, because the
    // room the label needs follows the font the device is set to.
    var fieldHeight by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { FieldLabel(stringResource(R.string.field_favorite_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            shape = FieldShape,
            colors = formFieldColors(),
            modifier = Modifier
                .weight(1f)
                // Ahead of the padding, so the height measured is the one the row lays
                // out — the padding included, as the button's offset counts from the row.
                .onSizeChanged { fieldHeight = it.height }
                .padding(top = 8.dp),
        )
        FilledTonalIconButton(
            onClick = onAdd,
            enabled = canAdd,
            modifier = Modifier
                .padding(start = 8.dp)
                .offset {
                    val box = OutlinedTextFieldDefaults.MinHeight.roundToPx()
                    IntOffset(0, fieldHeight - (box + ADD_BUTTON_SIZE.roundToPx()) / 2)
                }
                .size(ADD_BUTTON_SIZE),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
        }
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        favorites.forEach { favorite ->
            FavoriteChip(
                onClick = { onRemove(favorite) },
                label = favorite,
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}
