package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jourjour.R
import app.jourjour.core.photo.PhotoStorage

/**
 * A receipt, filling the screen, to be zoomed into and moved about.
 *
 * The preview on the form shows the whole of a photo but shows it small, which is enough
 * to tell one receipt from another and no use at all for reading a total off it. This is
 * where it is read — see [ZoomablePhoto] for what the fingers do.
 *
 * It is decoded afresh at [ZOOMED_PHOTO_MAX_SIDE] rather than reusing the preview's bitmap:
 * magnifying a picture already shrunk for a 240dp band would only magnify what was thrown
 * away with it.
 */
@Composable
fun PhotoViewer(photoFile: String, photoStorage: PhotoStorage, onClose: () -> Unit) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoFile) {
        bitmap = photoStorage.load(photoFile, ZOOMED_PHOTO_MAX_SIDE)
    }

    Dialog(
        onDismissRequest = onClose,
        // Left to fill the screen rather than sit in a dialog's usual margins: a receipt is
        // read by covering as much of the glass with it as there is.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA)),
        ) {
            val photo = bitmap
            if (photo == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                ZoomablePhoto(
                    bitmap = photo,
                    contentDescription = stringResource(R.string.field_photo),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close_photo),
                )
            }
        }
    }
}

/** Dark enough to leave nothing of the form showing, light enough not to read as a crash. */
internal const val BACKDROP_ALPHA = 0.94f
