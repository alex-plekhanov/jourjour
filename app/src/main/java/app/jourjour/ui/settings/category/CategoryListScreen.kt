package app.jourjour.ui.settings.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.CategoryWithFavorites
import app.jourjour.ui.common.DictionaryListScaffold
import app.jourjour.ui.navigation.Route

@Composable
fun CategoryListScreen(
    onBack: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DictionaryListScaffold(
        title = stringResource(R.string.title_categories),
        onBack = onBack,
        onAdd = { onOpenCategory(Route.NEW) },
        isLoading = state.isLoading,
        isEmpty = state.categories.isEmpty(),
        emptyMessage = stringResource(R.string.empty_categories),
    ) {
        items(state.categories, key = { it.category.id }) { item ->
            CategoryRow(item = item, onClick = { onOpenCategory(item.category.id) })
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryWithFavorites, onClick: () -> Unit) {
    val category = item.category
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.icon,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.width(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedName(category.name, category.nameKey),
                style = MaterialTheme.typography.bodyLarge,
            )
            // No badges for expense, income or quantity: the list is for finding a
            // category, and its settings are one tap away on the form itself.
            if (item.favorites.isNotEmpty()) {
                Text(
                    // Resolved like any other preloaded name. Without this the list shows
                    // the language the database was seeded in, which is only the device's
                    // as long as nobody has changed it since.
                    text = item.favorites
                        .map { localizedName(it.name, it.nameKey) }
                        .joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

