package app.jourjour.core.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a receipt photo to another app.
 *
 * The only way a photo leaves the app: everything else is kept out of the app's storage,
 * and sharing is what keeps a receipt when the app itself goes.
 *
 * @return false when nothing on the device accepts an image.
 */
fun Context.sharePhoto(uri: Uri, chooserTitle: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        // The receiver has no rights to our storage; this grant lasts only as long as it
        // handles the intent.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
