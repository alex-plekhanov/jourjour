package app.jourjour.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.CategoryPickerField
import app.jourjour.ui.common.CategorySheet
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.SecondaryButton

/** Wide enough for a three-letter code and the arrow that opens the picker. */
private val CURRENCY_FIELD_WIDTH = 112.dp

/**
 * The form of one budget line.
 *
 * Laid out the way the entry form is, because it is the same thing said in advance: what
 * it is for, what it is called, how many and how much. Price, quantity and total are all
 * typed into and the third of them follows the other two — see [BudgetSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetItemSheetContent(
    sheet: BudgetSheet,
    state: BudgetState,
    viewModel: BudgetViewModel,
) {
    val currency: Currency? = state.currency(sheet.currencyCode)

    ModalBottomSheet(
        onDismissRequest = viewModel::closeSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(
                    if (sheet.isNew) R.string.title_budget_item_new else R.string.title_budget_item_edit
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            CategoryPickerField(
                category = state.categories.firstOrNull { it.id == sheet.categoryId },
                onClick = viewModel::showCategorySheet,
                modifier = Modifier.fillMaxWidth(),
                errorRes = sheet.categoryError,
            )

            FormTextField(
                value = sheet.title,
                onValueChange = viewModel::updateTitle,
                labelRes = R.string.field_name,
            )

            // How many and how much apiece, side by side: that is how the figure below
            // them is arrived at, and reading the row left to right says the sum out loud.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoneyField(
                    value = sheet.quantity,
                    onValueChange = viewModel::updateQuantity,
                    currency = null,
                    labelRes = R.string.field_quantity,
                    modifier = Modifier.weight(1f),
                )
                MoneyField(
                    value = sheet.price,
                    onValueChange = viewModel::updatePrice,
                    currency = currency,
                    labelRes = R.string.field_unit_price,
                    modifier = Modifier.weight(1f),
                    showSymbol = false,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoneyField(
                    value = sheet.amount,
                    onValueChange = viewModel::updateAmount,
                    currency = currency,
                    labelRes = R.string.field_amount,
                    modifier = Modifier.weight(1f),
                    errorRes = sheet.amountError,
                    showSymbol = false,
                )
                CurrencyPickerField(
                    selectedCode = sheet.currencyCode,
                    currencies = state.currencies,
                    onSelect = viewModel::selectCurrency,
                    modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
                    errorRes = sheet.currencyError,
                    compact = true,
                )
            }

            if (sheet.isNotesVisible) {
                FormTextField(
                    value = sheet.notes,
                    onValueChange = viewModel::updateNotes,
                    labelRes = R.string.field_notes,
                    singleLine = false,
                )
            } else {
                SecondaryButton(
                    onClick = viewModel::showNotes,
                    text = stringResource(R.string.action_show_notes),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            PrimaryButton(
                onClick = viewModel::confirmSheet,
                text = stringResource(if (sheet.isNew) R.string.action_add else R.string.action_save),
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )
        }
    }

    if (sheet.isCategorySheetVisible) {
        CategorySheet(
            categories = state.categories,
            onSelect = viewModel::selectCategory,
            onDismiss = viewModel::hideCategorySheet,
        )
    }
}

