package app.jourjour.ui.settings.currency

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.jourjour.R
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.dictionaryDeleteMessage
import app.jourjour.ui.common.EditorScaffold
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.ObserveEditorEvents
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.formFieldColors
import app.jourjour.ui.theme.FieldShape

@Composable
fun CurrencyEditScreen(
    onClose: () -> Unit,
    viewModel: CurrencyEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveEditorEvents(viewModel.events, snackbarHostState, onClose)

    EditorScaffold(
        title = stringResource(
            if (state.isEditMode) R.string.title_currency_edit else R.string.title_currency_new
        ),
        onBack = onClose,
        onSave = viewModel::save,
        onDelete = viewModel::showDeleteDialog.takeIf { state.isEditMode },
        snackbarHostState = snackbarHostState,
    ) {
        FormTextField(
            value = state.code,
            onValueChange = viewModel::updateCode,
            labelRes = R.string.field_code,
            errorRes = state.codeError,
            // The code is the primary key and is not changed on an existing row.
            enabled = !state.isEditMode,
            // A code is three capitals and nothing else: USD, not Usd.
            capitalization = KeyboardCapitalization.Characters,
        )
        FormTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            labelRes = R.string.field_name,
            errorRes = state.nameError,
        )
        FormTextField(
            value = state.symbol,
            onValueChange = viewModel::updateSymbol,
            labelRes = R.string.field_symbol,
            errorRes = state.symbolError,
            // A symbol is whatever the currency writes itself with, and half of them are
            // lower case: kr, zl, lv. Offering a capital would be wrong more often than not.
            capitalization = KeyboardCapitalization.None,
        )
        OutlinedTextField(
            value = state.decimals,
            onValueChange = viewModel::updateDecimals,
            label = { FieldLabel(stringResource(R.string.field_decimals)) },
            isError = state.decimalsError != null,
            supportingText = {
                Text(stringResource(state.decimalsError ?: R.string.hint_decimals))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = FieldShape,
            colors = formFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
