package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.Category
import app.jourjour.ui.theme.FieldShape

/**
 * The category of an entry or of a budget line, shown as a field that opens the grid.
 *
 * A field rather than a row of chips: there are seventeen categories before anybody adds
 * one of their own, and the picker is a grid of icons that a person recognises faster than
 * they read.
 */
@Composable
fun CategoryPickerField(
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
) {
    Box(modifier = modifier.clickable(onClick = dismissingFocus(onClick))) {
        OutlinedTextField(
            value = category
                ?.let { "${it.icon} ${localizedName(it.name, it.nameKey)}" }
                ?: stringResource(R.string.value_not_set),
            onValueChange = {},
            label = { FieldLabel(stringResource(R.string.field_category)) },
            readOnly = true,
            // Disabled on purpose so the wrapper receives the tap.
            enabled = false,
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            // A long category name may share its row with something else, so it is cut
            // rather than wrapped.
            singleLine = true,
            shape = FieldShape,
            colors = pickerFieldColors(enabled = true),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** The categories as a grid of icons, which is how one is chosen everywhere in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    categories: List<Category>,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.select_category),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    Column(
                        modifier = Modifier
                            .clickable { onSelect(category) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = category.icon, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = localizedName(category.name, category.nameKey),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
