package app.jourjour.ui.common

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The time of day, picked from a clock. It sits beside the date on the entry form and
 * decides the order of a day's entries — and, with it, which side of a wallet recount an
 * entry falls on.
 *
 * Seconds are not shown and not touched: an entry keeps the ones it was written with, so
 * two entries made in the same minute stay in the order they were made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val formatter = rememberTimeFormatter()

    Box(
        modifier = modifier.clickable(
            enabled = enabled,
            onClick = dismissingFocus { dialogVisible = true },
        ),
    ) {
        OutlinedTextField(
            value = time.format(formatter),
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
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        TimePickerDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(stringResource(labelRes)) },
            confirmButton = {
                PrimaryButton(
                    onClick = {
                        onTimeChange(time.withHour(pickerState.hour).withMinute(pickerState.minute))
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
