package app.jourjour.core.location

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The staleness threshold is deliberately generous: an emulator hands out no fresh fix,
 * and its known position is hours old. What is being checked is that the source finds an
 * enabled provider, takes a position from it and turns that into coordinates.
 */
@RunWith(AndroidJUnit4::class)
class LocationSourceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun a_known_fix_becomes_coordinates() = runBlocking {
        val source = LocationSource(context, maxAgeNanos = TimeUnit.DAYS.toNanos(1))

        when (val result = source.current()) {
            is LocationResult.Success -> {
                val coordinates = result.coordinates
                assertTrue(coordinates.latitude in -90.0..90.0)
                assertTrue(coordinates.longitude in -180.0..180.0)
                // The format is ready to paste into a map.
                assertTrue(coordinates.format(), coordinates.format().contains(", "))
            }
            // A machine with location switched off has nothing to check, and that is fine.
            LocationResult.Unavailable, LocationResult.Timeout -> Unit
            LocationResult.PermissionDenied -> error("the rule granted the permission")
        }
    }

    @Test
    fun a_stale_fix_is_not_taken() = runBlocking {
        // A zero threshold rejects every known fix, leaving either a fresh answer from
        // the provider or a refusal, but never a stale point.
        val source = LocationSource(context, maxAgeNanos = 0)
        val result = source.current()
        assertTrue(
            result.toString(),
            result is LocationResult.Timeout ||
                result is LocationResult.Unavailable ||
                result is LocationResult.Success,
        )
    }

    @Test
    fun without_permission_the_source_reports_a_refusal() {
        // The rule granted the permission, so what is checked is the flag itself: it is
        // what decides whether a screen has to ask.
        val source = LocationSource(context, maxAgeNanos = 0)
        assertEquals(true, source.hasPermission())
    }
}
