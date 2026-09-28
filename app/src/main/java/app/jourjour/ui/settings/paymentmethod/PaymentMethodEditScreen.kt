package app.jourjour.ui.settings.paymentmethod

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
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

@Composable
fun PaymentMethodEditScreen(
    onClose: () -> Unit,
    viewModel: PaymentMethodEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveEditorEvents(viewModel.events, snackbarHostState, onClose)

    EditorScaffold(
        title = stringResource(
            if (state.isEditMode) R.string.title_payment_method_edit
            else R.string.title_payment_method_new
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
            titleRes = R.string.field_fixed_currency,
            subtitleRes = R.string.hint_fixed_currency,
            checked = state.fixedCurrency,
            onCheckedChange = viewModel::updateFixedCurrency,
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
