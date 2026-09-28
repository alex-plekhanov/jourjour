package app.jourjour.ui.common

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.jourjour.R
import app.jourjour.ui.theme.JourJourTheme

/**
 * A deletion prompt. The caller supplies the wording: a dictionary value refuses to go
 * while anything points at it, whereas a trip always goes and takes its entries with it.
 * Promising one when the other will happen would be a lie.
 */
@Composable
fun ConfirmDeleteDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.confirm_delete_title),
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Yes or no to something that will happen at once and to more than one row.
 *
 * The title is the caller's too: what is being asked decides it, and a prompt headed
 * "confirm deletion" over something that deletes nothing reads as a fright.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            PrimaryButton(onClick = onConfirm, text = stringResource(R.string.action_yes))
        },
        dismissButton = {
            SecondaryButton(onClick = onDismiss, text = stringResource(R.string.action_no))
        },
    )
}

/** The wording for dictionaries: deletion works while nothing points at the value. */
@Composable
fun dictionaryDeleteMessage(itemName: String): String =
    stringResource(R.string.confirm_delete_message, itemName)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmDeleteDialogPreview() {
    JourJourTheme {
        ConfirmDeleteDialog(
            message = dictionaryDeleteMessage("USD"),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
