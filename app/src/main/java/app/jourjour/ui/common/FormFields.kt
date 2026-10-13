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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
    /**
     * What the field will be saved as if nothing is typed into it, shown in its place in
     * grey — the same as the hint of a [MoneyField], and gone under the cursor for the same
     * reason: somebody who came to type wants an empty field, not one to clear first.
     */
    defaultValue: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val showingDefault = defaultValue != null && value.isBlank() && !focused
    OutlinedTextField(
        value = if (showingDefault) requireNotNull(defaultValue) else value,
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
        textStyle = LocalTextStyle.current.copy(
            color = if (showingDefault) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        ),
        shape = FieldShape,
        colors = formFieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .onFocusChanged { focused = it.isFocused },
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
    Labelled { LabelText(text) }
}

/**
 * The same label with an "i" in it, for a field whose name does not say what it is for.
 *
 * Only for a field whose label is always up on the border — one that is never empty, like
 * a picker, which says "not set" where nothing is set. On a field that can be empty the
 * label drops into the middle of it at full size, and the mark would ride down with it.
 *
 * In the label rather than on a heading over the field: a heading would have to repeat
 * the words the label already says, and two lines saying "payment method" one under the
 * other is a form that has lost count of what it is asking.
 */
@Composable
fun FieldLabelWithInfo(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    @StringRes descriptionRes: Int,
) {
    Labelled {
        LabelText(stringResource(titleRes))
        InfoTooltipMark(titleRes, hintRes, descriptionRes)
    }
}

/**
 * The backing the whole label stands on, mark and all.
 *
 * Material cuts the gap in the border to the width of everything in here, so everything
 * in here has to carry the fill: anything the backing missed would show the page through
 * the border.
 */
@Composable
private fun Labelled(content: @Composable RowScope.() -> Unit) {
    val fill = MaterialTheme.colorScheme.surfaceContainer
    UnscaledText {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.drawBehind {
                val side = LABEL_GAP_SIDE.toPx()
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(-side, 0f),
                    size = Size(size.width + 2 * side, size.height + LABEL_GAP_SKIRT.toPx()),
                    cornerRadius = CornerRadius(LABEL_BACKING_RADIUS.toPx()),
                )
            },
            content = content,
        )
    }
}

/** The words of the label. */
@Composable
private fun LabelText(text: String) {
    Text(text = text)
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

/** How tall a field stands, which is what anything beside one is lined up against. */
val FIELD_HEIGHT = 56.dp

/**
 * Where a control standing beside a field begins.
 *
 * A field is set down by the gap it keeps from the row above, and its label sits in the
 * border rather than over it, so the box a person actually sees starts lower than the
 * arithmetic says. This is that gap as the eye reads it rather than as the layout gives it:
 * two dp of difference is nothing to measure and everything to notice.
 *
 * Together with [FIELD_HEIGHT] it is how a button is centred against the field rather than
 * against the row — a row is as tall as its tallest line, an error under a figure makes one
 * taller, and a button that drifted down the moment something was mistyped would be
 * answering the mistake.
 */
val BESIDE_FIELD_TOP = 12.dp

private const val DISABLED_ALPHA = 0.38f

/** What Material widens the break by on either side of a floating label. */
private val LABEL_GAP_SIDE = 4.dp

/** How far the backing runs past the break, where it is on the fill and out of sight. */
private val LABEL_GAP_SKIRT = 8.dp

/**
 * Small enough that the backing is already at its full width by the time it crosses the
 * stroke: a lozenge would still be curving in there and leave the page showing at the ends.
 */
private val LABEL_BACKING_RADIUS = 6.dp
