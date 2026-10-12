package app.jourjour.ui.common

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import app.jourjour.ui.theme.FieldShape

/** A form text field: one look, with the error and the hint in the same place. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
    @StringRes hintRes: Int? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    /**
     * A form field is filled in with a name or a sentence, so the keyboard offers a capital
     * to start it with. The exceptions are fields holding something that is not written
     * language — a currency's code, which is capitals throughout, and its symbol, which is
     * whatever the symbol happens to be.
     */
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { FieldLabel(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        isError = errorRes != null,
        supportingText = when {
            errorRes != null -> {
                { Text(stringResource(errorRes)) }
            }
            hintRes != null -> {
                { Text(stringResource(hintRes)) }
            }
            else -> null
        },
        enabled = enabled,
        singleLine = singleLine,
        shape = FieldShape,
        colors = formFieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

/** A switch row: the whole row toggles, not just the switch itself. */
@Composable
fun FormSwitchRow(
    @StringRes titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes subtitleRes: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The text takes what is left after the switch, rather than as much as it likes:
        // without the weight a long subtitle pushes the switch off the screen.
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            if (subtitleRes != null) {
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}



/**
 * A caption over a field, with the paragraph that says why it is there behind an "i"
 * beside it.
 *
 * For the field whose name is not enough — one nobody has to fill in, whose point is what
 * it lets the app do later. The field under it carries no label of its own: the caption
 * is its name, and the two together would be the same words twice.
 *
 * Quieter than the headings that divide a form into sections: this names one field rather
 * than a run of them.
 */
@Composable
fun FieldHeading(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    @StringRes descriptionRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoTooltipButton(
            titleRes = titleRes,
            textRes = hintRes,
            descriptionRes = descriptionRes,
        )
    }
}

/**
 * A field's label, once the field has something in it, floats up onto the top border, and
 * the border leaves a gap for it. On an unfilled field that gap shows nothing; on a filled
 * one it shows the page, which cuts a pale notch out of the fill. Carrying the fill along
 * with the label closes it: inside the field the backing is invisible, on the border it
 * continues the field.
 *
 * The backing has to be drawn, not laid out. Material sizes the break from the label it is
 * given, so padding the label widens the break by exactly as much and never catches up: the
 * backing has to reach [LABEL_GAP_SIDE] past its own edges instead. It also runs
 * [LABEL_GAP_SKIRT] below them, because the break takes the fill out to the full height of
 * the label and rounded corners would leave the bottom two of it showing — down there the
 * backing is on the fill already, and nothing of it can be seen.
 *
 * The label keeps its size whatever the device's font is set to — see [UnscaledText]. It
 * says what the field is for, which is read while somebody is learning the form and never
 * again; what they type into it grows with the setting, as everything read does.
 */
@Composable
fun FieldLabel(text: String) {
    val fill = MaterialTheme.colorScheme.surfaceContainer
    UnscaledText {
        val ceiling = LocalTextStyle.current.fontSize.takeOrElse { DEFAULT_LABEL_SIZE }
        Text(
            text = text,
            // Shrunk rather than broken. A label resting in a narrow field is set at the
            // size of what is typed there, and a word that does not fit used to come
            // apart into a column of syllables; a point or two smaller it is still the
            // word. It floats up a size as soon as the field has anything in it, where
            // there is room for it whole.
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = SMALLEST_LABEL, maxFontSize = ceiling),
            modifier = Modifier.drawBehind {
                val side = LABEL_GAP_SIDE.toPx()
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(-side, 0f),
                    size = Size(size.width + 2 * side, size.height + LABEL_GAP_SKIRT.toPx()),
                    cornerRadius = CornerRadius(LABEL_BACKING_RADIUS.toPx()),
                )
            },
        )
    }
}

/**
 * The look of every field in the app: a pale fill and a quiet border, so that a form reads
 * as a column of places to type rather than a stack of boxes. The border only asserts
 * itself where the cursor is — that one is left at Material's focused colour and width,
 * since it is the only line that has to be found at a glance.
 */
@Composable
fun formFieldColors(): TextFieldColors {
    val fill = MaterialTheme.colorScheme.surfaceContainer
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = fill,
        errorContainerColor = fill,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DISABLED_ALPHA),
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Colours for fields that behave as buttons (currency, date, payment method). The field
 * itself is disabled so that it does not steal the tap from its clickable wrapper, yet it
 * has to look ordinary — so it gets the enabled colours back, while a field that is
 * genuinely unavailable gets the muted ones.
 */
@Composable
internal fun pickerFieldColors(enabled: Boolean): TextFieldColors {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.let {
        if (enabled) it else it.copy(alpha = DISABLED_ALPHA)
    }
    val fill = MaterialTheme.colorScheme.surfaceContainer
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = fill,
        errorContainerColor = fill,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledTextColor = contentColor,
        disabledBorderColor = outlineColor,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val DISABLED_ALPHA = 0.38f

/** What Material widens the break by on either side of a floating label. */
/** The size a label rests at where the field says nothing about it. */
private val DEFAULT_LABEL_SIZE = 16.sp

/** How small a label may be set before it stops being worth reading. */
private val SMALLEST_LABEL = 11.sp

private val LABEL_GAP_SIDE = 4.dp

/** How far the backing runs past the break, where it is on the fill and out of sight. */
private val LABEL_GAP_SKIRT = 8.dp

/**
 * Small enough that the backing is already at its full width by the time it crosses the
 * stroke: a lozenge would still be curving in there and leave the page showing at the ends.
 */
private val LABEL_BACKING_RADIUS = 6.dp
