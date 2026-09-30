package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape

/** The longest a length can be set to from the dial: it shows hours, and hours stop at 23. */
private const val LONGEST_MINUTES = 23 * 60 + 59

/**
 * How long something lasts, written as a clock is — `00:30`, `02:00` — and picked from
 * the same dial the clocks beside it are picked from.
 *
 * The same dial on purpose: the two ends of an event and the length between them are the
 * same three figures read three ways, and one of the three opening something that looks
 * nothing like the other two would say they were different kinds of thing.
 *
 * Twelve-hour clocks have nothing to say here, so the dial is always the twenty-four hour
 * one whatever the phone is set to: there is no morning in an hour. And it is headed with
 * what is being asked for rather than with "select time", which a length is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationField(
    minutes: Long,
    onMinutesChange: (Long) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dialogVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.clickable(
            enabled = enabled,
            onClick = dismissingFocus { dialogVisible = true },
        ),
    ) {
        OutlinedTextField(
            value = formatDuration(minutes),
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            singleLine = true,
            shape = FieldShape,
            colors = pickerFieldColors(enabled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (dialogVisible) {
        // A length past what the dial can show opens at the longest it can, since the
        // alternative is a hand pointing at an hour the dial does not have.
        val shown = minutes.coerceIn(0, LONGEST_MINUTES.toLong())
        val pickerState = rememberTimePickerState(
            initialHour = (shown / 60).toInt(),
            initialMinute = (shown % 60).toInt(),
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(stringResource(labelRes)) },
            confirmButton = {
                PrimaryButton(
                    onClick = {
                        onMinutesChange(pickerState.hour * 60L + pickerState.minute)
                        dialogVisible = false
                    },
                    text = stringResource(R.string.action_ok),
                )
            },
            dismissButton = {
                SecondaryButton(
                    onClick = { dialogVisible = false },
                    text = stringResource(R.string.action_cancel),
                )
            },
        ) {
            TimePicker(state = pickerState)
        }
    }
}

/**
 * Hours and minutes with the hours padded: 00:30, 02:00, 168:00 for a week.
 *
 * The same shape as the clocks it stands under and padded for the same reason, and the
 * hours run past twenty-four rather than turning into days — a length is one number, and
 * two units would be two things to read instead of one.
 */
fun formatDuration(minutes: Long): String = "%02d:%02d".format(minutes / 60, minutes % 60)
