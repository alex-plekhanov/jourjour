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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.CategoryPickerField
import app.jourjour.ui.common.CategorySheet
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.FavoriteTitles
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.TitleWithNotesField

/**
 * Wide enough for a three-letter code with the arrow that opens the picker, and for the
 * handful of digits a quantity is. The two are given the same width so that the figures
 * they stand beside — the price and the total — line up down the form.
 */
private val NARROW_FIELD_WIDTH = 112.dp

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

    // Opening the notes is a request to write them, so the cursor follows. Only when the
    // control was pressed: a line that already carries notes opens with them on show, and
    // taking the cursor there would be taking it away from whatever was being edited.
    val notesFocus = remember { FocusRequester() }
    var notesAsked by remember { mutableStateOf(false) }
    LaunchedEffect(sheet.isNotesVisible, notesAsked) {
        if (sheet.isNotesVisible && notesAsked) {
            runCatching { notesFocus.requestFocus() }
            notesAsked = false
        }
    }

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

            TitleWithNotesField(
                title = sheet.title,
                notes = sheet.notes,
                isFavorite = state.isTitleFavorite,
                isNotesVisible = sheet.isNotesVisible,
                onTitleChange = viewModel::updateTitle,
                onNotesChange = viewModel::updateNotes,
                onToggleFavorite = viewModel::toggleTitleFavorite,
                onToggleNotes = {
                    notesAsked = !sheet.isNotesVisible
                    viewModel.toggleNotes()
                },
                notesFocus = notesFocus,
                labelRes = R.string.field_name,
            )

            FavoriteTitles(favorites = state.favorites, onPick = viewModel::pickFavorite)

            // How much apiece and how many, then what that comes to and in what money. The
            // two rows are built the same way — the wide figure first, the narrow one
            // beside it — so the eye runs down the amounts rather than across the form.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoneyField(
                    value = sheet.price,
                    onValueChange = viewModel::updatePrice,
                    currency = currency,
                    labelRes = R.string.field_unit_price,
                    modifier = Modifier.weight(1f),
                    showSymbol = false,
                )
                MoneyField(
                    value = sheet.quantity,
                    onValueChange = viewModel::updateQuantity,
                    currency = null,
                    labelRes = R.string.field_quantity,
                    modifier = Modifier.width(NARROW_FIELD_WIDTH),
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
                    modifier = Modifier.width(NARROW_FIELD_WIDTH),
                    errorRes = sheet.currencyError,
                    compact = true,
                )
            }

            PrimaryButton(
                onClick = viewModel::confirmSheet,
                text = stringResource(if (sheet.isNew) R.string.action_add else R.string.action_save),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
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

