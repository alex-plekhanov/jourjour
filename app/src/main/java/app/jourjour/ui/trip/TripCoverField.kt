package app.jourjour.ui.trip

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.ui.common.SecondaryButton

/** How tall the cover is shown on the form, where it is looked at rather than looked past. */
private val PREVIEW_HEIGHT = 160.dp

/** A cover is stored no wider than this, so the preview gets it whole. */
private const val PREVIEW_MAX_SIDE = 1080

/** A fade that takes nothing away: the form shows the cover as it will be stored. */
private val NO_FADE = SolidColor(Color.Black)

/**
 * The cover photo of a trip.
 *
 * The picture is picked, never taken: a cover is a photo of the place, chosen afterwards
 * from what is already in the gallery. The photo picker asks for no permission of its own
 * and hands over one picture at a time.
 *
 * Removing a cover needs no confirmation, unlike a receipt: the original stays in the
 * gallery, so nothing is lost that picking again would not bring back.
 */
@Composable
fun TripCoverField(
    photoFile: String?,
    coverStorage: TripCoverStorage,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
) {
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPick) }

    val pick = {
        pickLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.field_trip_cover),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.hint_trip_cover),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (photoFile == null) {
            SecondaryButton(
                onClick = { pick() },
                text = stringResource(R.string.action_pick_cover),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                TripCover(
                    photoFile = photoFile,
                    coverStorage = coverStorage,
                    fade = NO_FADE,
                    maxSide = PREVIEW_MAX_SIDE,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    onClick = { pick() },
                    text = stringResource(R.string.action_replace_cover),
                )
                SecondaryButton(
                    onClick = onRemove,
                    text = stringResource(R.string.action_delete_cover),
                    destructive = true,
                )
            }
        }
    }
}
