package app.jourjour.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands one of our files to another app.
 *
 * The only way anything leaves the app besides an export a person files themselves: the
 * link is granted per file and lasts only as long as the receiver handles the intent, so
 * nothing else of ours becomes reachable.
 *
 * @return false when nothing on the device accepts the kind of file offered.
 */
fun Context.shareFile(uri: Uri, mimeType: String, chooserTitle: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
