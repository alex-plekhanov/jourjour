package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle

/**
 * The shortest the locale writes a date, with the year cut to two digits.
 *
 * The pattern is the locale's own, so the parts stay in the order that locale puts them
 * in — 11.09.26 here, 9/11/26 there. Only the year is shortened, which no locale's short
 * form does by itself and which is what buys the width.
 */
@Composable
private fun rememberShortDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
        DateTimeFormatter.ofPattern(pattern.replace(Regex("y+"), "yy"), locale)
    }
}

/**
 * A date field with a calendar. The date is a calendar date, carrying no time and no
 * zone, so it travels to the picker and back through UTC: any other offset would shift
 * the chosen day by one on devices west of Greenwich.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
    /** Narrow: the short date, no calendar glyph, and only as wide as the caller makes it. */
    compact: Boolean = false,
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val shortFormatter = rememberShortDateFormatter()
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)

    Box(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clickable(onClick = dismissingFocus { dialogVisible = true }),
    ) {
        OutlinedTextField(
            value = date.format(if (compact) shortFormatter else dateFormatter),
            onValueChange = {},
            label = { FieldLabel(stringResource(labelRes)) },
            readOnly = true,
            // Disabled on purpose so the wrapper gets the tap, see CurrencyPickerField.
            enabled = false,
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            trailingIcon = if (compact) {
                null
            } else {
                { Icon(Icons.Default.DateRange, contentDescription = null) }
            },
            singleLine = true,
            shape = FieldShape,
            colors = pickerFieldColors(enabled = true),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (dialogVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { dialogVisible = false },
            confirmButton = {
                PrimaryButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
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
            DatePicker(state = pickerState)
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
