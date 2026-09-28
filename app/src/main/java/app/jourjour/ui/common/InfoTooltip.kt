package app.jourjour.ui.common

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * An "i" that opens a paragraph explaining what something is for.
 *
 * The explanation lives behind it rather than on the screen: it is read once and then
 * never again, and a paragraph standing there for good would push aside what a person came
 * to the screen to do. Persistent, so it stays up while it is being read and goes on a tap
 * anywhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltipButton(
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    @StringRes descriptionRes: Int,
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
        IconButton(onClick = { scope.launch { tooltipState.show() } }) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(descriptionRes),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
