package app.jourjour.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A date the way the screen's locale writes it.
 *
 * Built from the configuration rather than once for the process: a formatter made at
 * class-load time keeps the locale the process started in, and a screen recreated after
 * the language changed would go on writing its dates the old way while everything around
 * them had switched.
 */
@Composable
fun rememberDateFormatter(style: FormatStyle): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, style) { DateTimeFormatter.ofLocalizedDate(style).withLocale(locale) }
}

/**
 * A day of a trip: the weekday, the date, the month and the year, each written short.
 *
 * It heads a list that has something to say on the same line — what the day cost — so it
 * is abbreviated rather than spelt out: "сб, 26 сент. 2026 г." where the full form would
 * take the line to itself.
 *
 * Built from a skeleton rather than a pattern so that the parts come out in the order the
 * locale puts them in — "Sat, Sep 26, 2026" against "сб, 26 сент. 2026 г." — which a
 * pattern written here by hand would force into one shape for every language.
 */
@Composable
fun rememberDayFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, DAY_SKELETON)
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

/** Weekday, day of the month, abbreviated month, year. See [rememberDayFormatter]. */
private const val DAY_SKELETON = "EEEdMMMy"

/** A date and a time together, the way the screen's locale writes them. */
@Composable
fun rememberDateTimeFormatter(dateStyle: FormatStyle, timeStyle: FormatStyle): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, dateStyle, timeStyle) {
        DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle).withLocale(locale)
    }
}

/** Hours and minutes as the screen's locale writes them — 17:45 here, 5:45 PM there. */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
}
