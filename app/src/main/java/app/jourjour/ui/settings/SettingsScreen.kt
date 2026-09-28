package app.jourjour.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.jourjour.R
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.keyboardInsets
import app.jourjour.ui.common.FormSwitchRow
import app.jourjour.ui.common.FieldLabel
import app.jourjour.ui.common.formFieldColors
import app.jourjour.ui.theme.FieldShape

/**
 * What the app does rather than what it holds — the dictionaries live on their own screen.
 * Every setting sits under a heading: there are few enough of them to read at a glance, and
 * a loose one above the first heading would look like it had been left out of a section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.windowInsetsPadding(keyboardInsets()),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.title_currency_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            // Clearable: having no base currency is the starting state and a working one,
            // so there has to be a way back to it.
            CurrencyPickerField(
                selectedCode = state.baseCurrency,
                currencies = state.currencies,
                onSelect = viewModel::updateBaseCurrency,
                labelRes = R.string.field_base_currency,
                hintRes = R.string.hint_base_currency,
                clearable = true,
            )

            Text(
                text = stringResource(R.string.title_photo_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            FormSwitchRow(
                titleRes = R.string.field_compress_photos,
                subtitleRes = R.string.hint_compress_photos,
                checked = state.compress,
                onCheckedChange = viewModel::updateCompress,
            )
            OutlinedTextField(
                value = state.maxSide,
                onValueChange = viewModel::updateMaxSide,
                label = { FieldLabel(stringResource(R.string.field_photo_max_side)) },
                isError = state.maxSideError != null,
                supportingText = {
                    Text(stringResource(state.maxSideError ?: R.string.hint_photo_max_side))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                // Nothing to size when photos are kept as they came off the camera.
                shape = FieldShape,
                colors = formFieldColors(),
                enabled = state.compress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.title_developer_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            FormSwitchRow(
                titleRes = R.string.field_enable_dumps,
                subtitleRes = R.string.hint_enable_dumps,
                checked = state.dumpsEnabled,
                onCheckedChange = viewModel::updateDumpsEnabled,
            )
        }
    }
}
