package app.jourjour.core.photo

import android.content.Context
import android.net.Uri
import app.jourjour.core.share.shareFile

/**
 * Hands a receipt photo to another app.
 *
 * The only way a photo leaves the app: everything else is kept out of the app's storage,
 * and sharing is what keeps a receipt when the app itself goes.
 *
 * @return false when nothing on the device accepts an image.
 */
fun Context.sharePhoto(uri: Uri, chooserTitle: String): Boolean =
    shareFile(uri, "image/jpeg", chooserTitle)
