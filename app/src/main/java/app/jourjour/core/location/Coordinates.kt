package app.jourjour.core.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

/** Where an entry happened. */
data class Coordinates(val latitude: Double, val longitude: Double) {

    /**
     * "55.755826, 37.617300" — always a dot, always in this order, whatever the device
     * language is. Such a string can be pasted into any map or search box, while a comma
     * standing in for the decimal point would make it unreadable.
     */
    fun format(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    /** A link for a maps app: the point plus a label, so it can be told apart on the map. */
    fun geoUri(label: String): String {
        val point = String.format(Locale.US, "%f,%f", latitude, longitude)
        val query = if (label.isBlank()) point else "$point($label)"
        return "geo:$point?q=${android.net.Uri.encode(query)}"
    }
}

/**
 * Opens the point in a maps app.
 *
 * @return false when there is nothing to open it with — a device may carry no maps app
 * at all. Checking upfront with `resolveActivity` is not an option: since Android 11 it
 * returns nothing unless package queries are declared, and catching the refusal is cheaper.
 */
fun Context.openInMaps(coordinates: Coordinates, label: String): Boolean = try {
    startActivity(
        Intent(Intent.ACTION_VIEW, coordinates.geoUri(label).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}
