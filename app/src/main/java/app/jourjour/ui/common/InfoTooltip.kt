package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * An "i" that opens a paragraph explaining what something is for.
 *
 * The explanation lives behind it rather than on the screen: it is read once and then
 * never again, and a paragraph standing there for good would push aside what a person came
 * to the screen to do. Persistent, so it stays up while it is being read and goes on a tap
 * anywhere else.
 */
@Composable
fun InfoTooltipButton(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    @StringRes descriptionRes: Int,
) {
    Explained(titleRes, textRes) { show ->
        IconButton(onClick = show) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(descriptionRes),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The same mark, small enough to ride in the label of a field.
 *
 * A target smaller than a finger is normally wrong, and here it is not: this is read once
 * in the life of a form and never pressed again, and the alternative is a heading over
 * the field repeating the words the label already says.
 */
@Composable
fun InfoTooltipMark(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    @StringRes descriptionRes: Int,
) {
    Explained(titleRes, textRes) { show ->
        Icon(
            Icons.Default.Info,
            contentDescription = stringResource(descriptionRes),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(MARK_SIZE)
                .clickable(onClick = show),
        )
    }
}

/** The tooltip both of them hang off, given whatever opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Explained(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    anchor: @Composable (show: () -> Unit) -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = {
            RichTooltip(title = { Text(stringResource(titleRes)) }) {
                Text(stringResource(textRes))
            }
        },
        state = tooltipState,
    ) {
        anchor { scope.launch { tooltipState.show() } }
    }
}

/** As tall as the label it stands in, which is set smaller than anything else on a form. */
private val MARK_SIZE = 14.dp
