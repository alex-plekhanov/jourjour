package app.jourjour.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The link is built with android.net.Uri, so it is checked on a device. */
@RunWith(AndroidJUnit4::class)
class CoordinatesUriTest {

    private val tokyo = Coordinates(35.681236, 139.767125)

    @Test
    fun map_link_carries_the_point_and_the_label() {
        val uri = tokyo.geoUri("Lunch at the pier")
        // The point comes before the question mark so that a map opens on it even
        // when the app makes nothing of the query parameter.
        assertTrue(uri, uri.startsWith("geo:35.681236,139.767125?q="))
        // Anything a title may contain has to survive as a query parameter.
        assertTrue(uri, uri.contains("Lunch%20at%20the%20pier"))
    }

    @Test
    fun a_link_without_a_label_is_still_valid() {
        assertEquals("geo:35.681236,139.767125?q=35.681236%2C139.767125", tokyo.geoUri(""))
    }
}
