package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.sharePhoto
import app.jourjour.ui.common.ConfirmDeleteDialog
import app.jourjour.ui.common.SecondaryButton
import app.jourjour.ui.common.dismissingFocus

/**
 * The receipt photo of an entry.
 *
 * The camera writes straight into the app's own storage through a link handed to it for
 * one file, so the shot never reaches the gallery. That also means it goes when the app
 * does — hence sharing sits right next to it.
 *
 * A shot is not taken on as it arrives: it goes through [PhotoReview] first, which is
 * where it is seen the way up it will be kept and turned if the camera guessed wrong.
 */
@Composable
fun PhotoField(
    photoFile: String?,
    photoStorage: PhotoStorage,
    onCaptured: (String, Int) -> Unit,
    onRemove: () -> Unit,
    onNoCamera: () -> Unit,
    onNoShareTarget: () -> Unit,
) {
    val context = LocalContext.current
    var pendingName by remember { mutableStateOf<String?>(null) }
    var reviewName by remember { mutableStateOf<String?>(null) }
    var removeDialogVisible by remember { mutableStateOf(false) }
    var viewerVisible by remember { mutableStateOf(false) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val name = pendingName
        pendingName = null
        if (name == null) return@rememberLauncherForActivityResult
        // A cancelled capture still leaves the empty file we reserved for it.
        if (saved) reviewName = name else onRemoveReservedFile(photoStorage, name)
    }

    val takePhoto = {
        val name = photoStorage.newName()
        pendingName = name
        runCatching { captureLauncher.launch(photoStorage.uri(name)) }
            .onFailure {
                pendingName = null
                onNoCamera()
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text = stringResource(R.string.field_photo), style = MaterialTheme.typography.titleSmall)

        if (photoFile == null) {
            SecondaryButton(
                onClick = { takePhoto() },
                text = stringResource(R.string.action_take_photo),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            PhotoPreview(
                photoFile = photoFile,
                photoStorage = photoStorage,
                onOpen = { viewerVisible = true },
            )
            // Three buttons do not always fit one line in every language; the last wraps.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SecondaryButton(
                    onClick = { takePhoto() },
                    text = stringResource(R.string.action_retake_photo),
                )
                SecondaryButton(
                    onClick = {
                        val shared = context.sharePhoto(
                            photoStorage.uri(photoFile),
                            context.getString(R.string.action_share_photo),
                        )
                        if (!shared) onNoShareTarget()
                    },
                    text = stringResource(R.string.action_share_photo),
                    icon = Icons.Default.Share,
                )
                SecondaryButton(
                    onClick = { removeDialogVisible = true },
                    text = stringResource(R.string.action_delete_photo),
                    destructive = true,
                )
            }
        }
    }

    reviewName?.let { name ->
        PhotoReview(
            photoFile = name,
            photoStorage = photoStorage,
            onKeep = { quarterTurns ->
                reviewName = null
                onCaptured(name, quarterTurns)
            },
            onRetake = {
                reviewName = null
                // The shot being retaken is the one nothing points at yet, so it goes now
                // rather than waiting for the sweep.
                onRemoveReservedFile(photoStorage, name)
                takePhoto()
            },
        )
    }

    if (viewerVisible && photoFile != null) {
        PhotoViewer(
            photoFile = photoFile,
            photoStorage = photoStorage,
            onClose = { viewerVisible = false },
        )
    }

    if (removeDialogVisible) {
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_photo_message),
            onConfirm = {
                removeDialogVisible = false
                onRemove()
            },
            onDismiss = { removeDialogVisible = false },
        )
    }
}

/**
 * The photo as the form shows it: the whole of it, and a tap away from being read.
 *
 * Fitted rather than cropped. A receipt is tall and narrow and the total is at the foot of
 * it, which is exactly what a crop to a wide band takes off; better a small picture of all
 * of it than a large picture of its middle.
 */
@Composable
private fun PhotoPreview(photoFile: String, photoStorage: PhotoStorage, onOpen: () -> Unit) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoFile) {
        bitmap = photoStorage.load(photoFile, PREVIEW_MAX_SIDE)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = dismissingFocus(onOpen)),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.action_open_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun onRemoveReservedFile(photoStorage: PhotoStorage, name: String) {
    // Nothing to await: the file is empty and the sweep would get it anyway.
    photoStorage.file(name).delete()
}

/** Tall enough to make out what the photo is of, without taking over the form. */
private val PREVIEW_HEIGHT = 240.dp

private const val PREVIEW_MAX_SIDE = 1080
