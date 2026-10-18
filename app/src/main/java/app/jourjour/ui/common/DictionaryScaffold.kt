package app.jourjour.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape

/**
 * The shared frame of a dictionary list: a top bar with a way back, optional search, an
 * add button, and the loading and empty states.
 *
 * All three dictionaries — currencies, payment methods, categories — are built on it, so
 * they look and behave alike without anyone having to remember to keep them in step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryListScaffold(
    title: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "",
    content: LazyListScope.() -> Unit,
) {
    val scrollAway = rememberScrollAwayState()
    Scaffold(
        modifier = modifier
            .windowInsetsPadding(keyboardInsets())
            .nestedScroll(scrollAway.connection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AddFab(
                onClick = onAdd,
                contentDescription = stringResource(R.string.action_add),
                modifier = Modifier.scrollAway(scrollAway),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (searchQuery != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = FieldShape,
                    colors = formFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    isEmpty -> Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Keeps the add button off the last row.
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.Top,
                        content = content,
                    )
                }
            }
        }
    }
}
