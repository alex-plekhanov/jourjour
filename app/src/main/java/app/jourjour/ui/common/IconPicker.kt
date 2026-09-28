package app.jourjour.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape

/**
 * Picking an icon.
 *
 * The chosen icon is shown by highlighting it among the offered ones and nowhere else:
 * repeating it in a preview and in a text field said the same thing three times over.
 *
 * The last cell opens a prompt for any emoji at all. What is typed there is added to
 * [presets] through [onAddPreset], so an icon has to be hunted down only once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selected: String,
    presets: List<String>,
    onSelect: (String) -> Unit,
    onAddPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var promptVisible by remember { mutableStateOf(false) }

    // An icon saved earlier may be missing from the offered ones — a preset list can be
    // reseeded, and a record keeps whatever it was given. Showing it keeps the selection
    // visible instead of leaving nothing highlighted.
    val icons = remember(presets, selected) {
        if (selected.isBlank() || selected in presets) presets else presets + selected
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.field_icon),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icons.forEach { emoji ->
                IconCell(
                    isSelected = emoji == selected,
                    onClick = { onSelect(emoji) },
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            val customDescription = stringResource(R.string.action_custom_icon)
            IconCell(isSelected = false, onClick = { promptVisible = true }) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = customDescription },
                )
            }
        }
    }

    if (promptVisible) {
        CustomIconDialog(
            onConfirm = { emoji ->
                promptVisible = false
                onAddPreset(emoji)
                onSelect(emoji)
            },
            onDismiss = { promptVisible = false },
        )
    }
}

@Composable
private fun IconCell(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun CustomIconDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_custom_icon)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(ICON_MAX_LENGTH) },
                label = { FieldLabel(stringResource(R.string.field_emoji)) },
                supportingText = { Text(stringResource(R.string.hint_custom_icon)) },
                singleLine = true,
                shape = FieldShape,
                colors = formFieldColors(),
            )
        },
        confirmButton = {
            PrimaryButton(
                onClick = { onConfirm(value.trim()) },
                text = stringResource(R.string.action_add),
                enabled = value.isNotBlank(),
            )
        },
        dismissButton = {
            SecondaryButton(onClick = onDismiss, text = stringResource(R.string.action_cancel))
        },
    )
}

/** An emoji spans several code units: flags and composed ones take 4 to 8 characters. */
private const val ICON_MAX_LENGTH = 8
