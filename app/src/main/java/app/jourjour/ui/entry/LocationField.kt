package app.jourjour.ui.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.location.LocationSource
import app.jourjour.core.location.openInMaps
import app.jourjour.ui.common.SecondaryButton

/**
 * Where an entry happened.
 *
 * Permission is asked for right here, on a button press, rather than at startup: that way
 * a person can see what it is for, and can use everything else without granting it at all.
 */
@Composable
fun LocationField(
    state: EntryEditState,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    onPermissionDenied: () -> Unit,
    hasPermission: () -> Boolean,
    onNoMapsApp: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Precise or approximate, either will do: approximate merely points at the block
        // instead of the cafe.
        if (granted.values.any { it }) onCapture() else onPermissionDenied()
    }

    val requestLocation = {
        if (hasPermission()) onCapture() else permissionLauncher.launch(LocationSource.PERMISSIONS)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.field_coordinates),
            style = MaterialTheme.typography.titleSmall,
        )

        when {
            state.isLocating -> Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.locating),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.coordinates == null -> SecondaryButton(
                onClick = requestLocation,
                text = stringResource(R.string.action_capture_location),
                icon = Icons.Default.Place,
                modifier = Modifier.padding(top = 4.dp),
            )

            else -> Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.coordinates.format(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear_location),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        onClick = {
                            val label = state.title.ifBlank { state.place }
                            if (!context.openInMaps(state.coordinates, label)) onNoMapsApp()
                        },
                        text = stringResource(R.string.action_open_in_maps),
                    )
                    SecondaryButton(
                        onClick = requestLocation,
                        text = stringResource(R.string.action_recapture_location),
                    )
                }
            }
        }
    }
}
