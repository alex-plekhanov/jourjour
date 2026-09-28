package app.jourjour.core.location

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CoordinatesTest {

    @Test
    fun `coordinates always carry a dot, whatever the device language`() {
        val tokyo = Coordinates(35.681236, 139.767125)
        val defaultLocale = Locale.getDefault()
        try {
            // A comma is the decimal separator in Russian, and no map on earth would
            // make sense of "35,68, 139,76".
            Locale.setDefault(Locale.of("ru", "RU"))
            assertEquals("35.681236, 139.767125", tokyo.format())
            Locale.setDefault(Locale.GERMANY)
            assertEquals("35.681236, 139.767125", tokyo.format())
            Locale.setDefault(Locale.US)
            assertEquals("35.681236, 139.767125", tokyo.format())
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun `negative coordinates keep their sign`() {
        assertEquals("-33.868820, 151.209290", Coordinates(-33.86882, 151.20929).format())
    }
}
